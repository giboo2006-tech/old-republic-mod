#!/usr/bin/env python3
"""mission/fence_camp and mission/fence_camp_2 (docs/MILESTONE_6.md 5.4).

Imperial Intelligence's "Names" (i1_names): Dovan Lisk, a fence, keeps the names of everyone who handled
the stolen crystals in one book, neatly, in a careful hand. The player takes the ledger from its lectern
(BOOK), deals with Lisk (FENCE) and his four guards (CREW_1..4), and may burn the book at his fire (FIRE).
Both variants: 31 x 16 x 31, buried 1, low ground (a fence trades where the roads meet in a valley).

  fence_camp    "Lisk's Pavilion": a trader's marquee at a crossroads inside a ring of sandbags and
                crates. Under its striped roof a long counter, the ledger on its lectern behind it, the
                strongbox chained at the back; customers' rugs and a table of crystals in front; the
                guards' tents and cook fire to the west, a cargo hauler being loaded to the east.
  fence_camp_2  "The Waystation": Lisk has moved into a ruined stone waystation by the road. Tarps roof
                its surviving hall, where he keeps his counting room; market stalls fill the courtyard
                round the fire; the broken watchtower at the corner still carries his lamp.

Usage: python3 tools/assets/structures/qm/fence_camp.py [--preview <folder>] [--only <id part>]
"""
import math

import qmkit as K
from qmkit import S, AIR, Template, B, Noise, mod

SIZE = (31, 16, 31)
BURIED = 1
ANCHORS = ['BOOK', 'CREW_1', 'CREW_2', 'CREW_3', 'CREW_4', 'FENCE', 'FIRE', 'HEART']
META = dict(buried=BURIED, terrain='low', max_slope=4, clearance=10, support_depth=8, ramp_rings=2, clear_above=6)


def hauler(t, x, y, z, rng, length=6, cargo=((K.CRATE, 3), (K.CRATE_REP, 1), (K.CRATE_IMP, 1))):
    """A cargo landspeeder hovering over the feet layer, nose north (-z), its bed at the back laden with
    crates: a cab with a screen, a flat bed of plates, engine pods along both sides."""
    for dz in range(length):
        zz = z + dz
        for dx in range(3):
            t.set(x + dx, y, zz, K.PLATING_SLAB_TOP)
        if dz in (1, length - 2):
            for side in (-1, 3):
                t.set(x + side, y, zz, K.pipes('z'))
    t.set(x + 1, y, z, K.plating_stairs('south', 'top'))
    t.set(x, y + 1, z + 1, K.plating_stairs('south'))
    t.set(x + 2, y + 1, z + 1, K.plating_stairs('south'))
    t.set(x + 1, y + 1, z + 1, S('gray_stained_glass_pane', east=True, west=True, north=False, south=False,
                                 waterlogged=False))
    t.set(x + 1, y + 1, z + 2, K.carpet('black'))
    t.set(x, y + 1, z + 2, K.rod('up'))
    for dz in range(3, length):
        for dx in range(3):
            if rng.random() < 0.7:
                t.set(x + dx, y + 1, z + dz, K.pick(rng, cargo))
                if rng.random() < 0.3:
                    t.set(x + dx, y + 2, z + dz, K.pick(rng, cargo) if rng.random() < 0.6 else K.CANVAS_SLAB)
    t.set(x + 1, y, z + length, K.VENT)


# =============================================================================================
# fence_camp: Lisk's Pavilion
# =============================================================================================

def build_pavilion():
    t = Template(*SIZE, seed=6301)
    rng = t.rng
    G, F = 0, 1
    worn = K.Mix(K.WORN, 6303, scale=4.5, jitter=0.15)
    track = K.Mix(K.TRACK, 6305, 3.5, 0.2)

    # --- ground: the yard worn bare, the crossroads north of the gate ----------------------------
    cx, cz = 15.0, 17.5
    K.ground_blob(t, G, cx, cz, 12.6, 11.2, worn, 6309, fray=0.18)
    K.trail(t, G, [(15, -1), (15, 4), (15, 9)], 4, track, 6311)
    K.trail(t, G, [(-1, 4.5), (8, 5.0), (15, 4.0), (22, 3.5), (31, 4.5)], 3, track, 6313)

    # --- the ring of sandbags, crates and stakes, a gate in the north -----------------------------------
    wob = Noise(6315, 4.0)
    yard, ring = K.ellipse_cells(cx, cz, 13.4, 11.8, wob, 0.15)
    kind = Noise(6317, 2.2)
    for (x, z) in ring:
        if abs(x - 15) <= 2 and z < cz:
            continue  # the gate
        v = kind.at(x, 0, z)
        if v < 0.22:
            continue  # a gap
        t.set(x, G, z, K.GRAVEL if (x + z) % 2 else K.COARSE)
        if v < 0.62:
            K.sandbags(t, [(x, z)], F, rng, high=0.55)
        elif v < 0.78:
            t.set(x, F, z, K.CRATE if rng.random() < 0.7 else K.CRATE_IMP)
            if rng.random() < 0.3:
                t.set(x, F + 1, z, K.CANVAS_SLAB)
        else:
            t.set(x, F, z, K.fence('spruce_fence'))
            t.set(x, F + 1, z, K.fence('spruce_fence') if rng.random() < 0.6 else K.rod())
    # the gate: two lamp posts with a crimson rag, the trader's sign without words
    gz = min(z for (x, z) in ring if x == 15)
    for x in (12, 18):
        K.column(t, x, F, F + 3, gz, K.beam('y'))
        t.set(x, G, gz, K.GRAVEL)
        t.set(x, F + 4, gz, K.lantern())
    for x in range(12, 19):
        t.set(x, F + 3, gz, K.beam('x')) if x not in (12, 18) else None
    t.set(15, F + 2, gz, K.chain('y'))
    t.set(15, F + 1, gz, K.lantern(True))
    K.banner(t, 13, F + 2, gz + 1, 'south', 'red')
    K.banner(t, 17, F + 2, gz + 1, 'south', 'red')
    for x in range(13, 18):
        for y in (F, F + 1):
            if t.get(x, y, gz) is None:
                t.set(x, y, gz, AIR)

    # --- the pavilion -----------------------------------------------------------------------------------
    X0, X1, Z0, Z1 = 9, 21, 16, 24  # the posts' rectangle
    ZR = (Z0 + Z1) // 2              # the ridge line
    for x in (9, 13, 17, 21):
        for z in (Z0, Z1):
            K.column(t, x, F, F + 3, z, K.beam('y'))
    for x in (X0, X1):
        K.column(t, x, F, F + 7, ZR, K.beam('y'))
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            t.set(x, G, z, K.PACKED_MUD if (x * 3 + z) % 7 else K.COARSE)
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z0 - 1, Z1 + 2):
            k = abs(z - ZR)
            y = F + 8 - k
            if z == ZR or (x in (11, 15, 19) and z not in (Z0 - 1, Z1 + 1)):
                t.set(x, y, z, K.CANVAS_CRIMSON)  # the ridge and three crimson seams
            elif z < ZR:
                t.set(x, y, z, K.canvas_stairs('south'))
            else:
                t.set(x, y, z, K.canvas_stairs('north'))
        t.set(x, F + 9, ZR, K.CANVAS_SLAB) if x % 2 == 0 else None
    # crimson gables (the upper triangle), a valance along the front
    for x in (X0 - 1, X1 + 1):
        for z in range(Z0 + 1, Z1):
            for y in range(F + 5, F + 8 - abs(z - ZR)):
                t.set(x, y, z, K.CANVAS_CRIMSON)
    for x in range(X0 + 1, X1):
        if x not in (13, 17):
            t.set(x, F + 3, Z0, K.CANVAS_CRIMSON if x % 2 else K.CANVAS)
    # pennant poles over both gables
    for x in (X0 - 1, X1 + 1):
        K.column(t, x, F + 9, F + 11, ZR, K.beam('y'))
        t.set(x, F + 12, ZR, K.rod())
    K.banner(t, X0 - 2, F + 10, ZR, 'west', 'red')
    K.banner(t, X1 + 2, F + 10, ZR, 'east', 'red')
    # the back closed with navy hangings; side curtains, some down, some tied up
    for x in range(X0 + 1, X1):
        if x in (13, 17):
            continue
        for y in range(F, F + 4):
            t.set(x, y, Z1, K.CANVAS_NAVY)
    for x in (X0, X1):
        for z in range(Z0 + 1, Z1):
            if z == ZR:
                continue
            down = (z + x) % 3 != 0
            for y in range(F, F + 4):
                if down or y >= F + 2:
                    t.set(x, y, z, K.CANVAS_NAVY if (z % 4) else K.CANVAS)
    for z in range(Z0 + 1, Z1):  # the doorway into the side, west
        if z in (ZR - 2, ZR - 1):
            for y in range(F, F + 3):
                t.set(X0, y, z, AIR)

    # --- inside: the counter, the ledger, the strongbox, the goods ------------------------------------------
    cz_ = Z0 + 3  # the counter line
    for x in range(X0 + 1, X1):
        if x == 12:
            continue  # the flap
        if x in (10, 11, 19, 20):
            t.set(x, F, cz_, K.CRATE)
        else:
            t.set(x, F, cz_, S('spruce_trapdoor', facing='south', half='top', open=False, powered=False, waterlogged=False)
                  if x % 2 else K.PLATING_SLAB_TOP)
    t.set(14, F + 1, cz_, K.cluster())
    t.set(16, F + 1, cz_, K.candle(2, lit=True))
    t.set(17, F + 1, cz_, S('small_amethyst_bud', facing='up', waterlogged=False))
    t.set(10, F + 1, cz_, K.pot())
    t.set(19, F + 1, cz_, K.lantern())
    # the ledger on its lectern behind the counter, his stool, the reader's place before it
    mod.lectern(t, 15, F, Z1 - 2, facing='north', book=True)
    t.anchor('BOOK', 15, F, Z1 - 2)
    t.set(14, F, Z1 - 2, K.CANVAS_SLAB)
    t.set(14, F, Z1 - 1, K.PLATING_SLAB_TOP)
    t.set(14, F + 1, Z1 - 1, K.candle(3, lit=True))
    # the goods along the back: crates of crystals, the strongbox in chains
    for (x, h) in ((10, 2), (11, 1), (12, 2), (18, 1), (19, 2), (20, 2)):
        K.crate_pile(t, [(x, Z1 - 1, h)], F, rng, kinds=((K.CRATE, 3), (K.CRATE_IMP, 1), (K.CRATE_REP, 1)))
        if h == 1:
            t.set(x, F + 1, Z1 - 1, K.cluster())
    for x in (19, 20):
        t.set(x, F + 2, Z1 - 1, K.chain('x')) if h else None
    t.set(16, F, Z1 - 1, K.CRATE_IMP)
    t.set(17, F, Z1 - 1, K.CRATE_IMP)
    t.set(16, F + 1, Z1 - 1, K.chain('x'))
    t.set(17, F + 1, Z1 - 1, K.chain('x'))
    t.set(11, F, Z1 - 2, S('amethyst_block'))
    # lamps hung from the ridge
    for x in (12, 18):
        t.set(x, F + 7, ZR, K.chain('y'))
        t.set(x, F + 6, ZR, K.lantern(True))
    # rugs for the customers, a crystal table and a scale before the counter
    for x in range(11, 20):
        for z in (Z0 + 1, Z0 + 2):
            if t.get(x, F, z) is None and (x + z) % 5:
                t.set(x, F, z, K.carpet('red' if (x // 2) % 2 else 'brown'))
    for (x, z) in ((10, Z0 - 2), (11, Z0 - 2)):
        t.set(x, F, z, K.CRATE)
        K.support(t, x, F, z)
    t.set(10, F + 1, Z0 - 2, K.cluster())
    t.set(11, F + 1, Z0 - 2, S('amethyst_cluster', facing='up', waterlogged=False))
    t.set(20, F, Z0 - 2, K.PLATING_SLAB_TOP)
    t.set(20, F + 1, Z0 - 2, S('heavy_weighted_pressure_plate', power=0))
    mod.light_fill(t, (X0, F, Z0, X1, F + 3, Z1))

    # --- the guards' tents and the cook fire to the west -------------------------------------------------------
    tent1 = K.a_tent(t, 2, 14, 5, 5, F, ridge='z', door='min', back=K.wool('brown'))
    for i in range(5):
        for j in range(5):
            x, z = tent1(i, j)
            t.set(x, G, z, K.COARSE)
    K.bedroll(t, *tent1(1, 1), along='z') if False else None
    x, z = tent1(1, 1)
    K.bedroll(t, x, F, z, color='gray', along='z', length=3)
    x, z = tent1(1, 3)
    K.bedroll(t, x, F, z, color='brown', along='z', length=3)
    tent2 = K.a_tent(t, 3, 21, 4, 5, F, ridge='x', door='max', back=K.CANVAS)
    for i in range(4):
        for j in range(5):
            x, z = tent2(i, j)
            t.set(x, G, z, K.COARSE)
    x, z = tent2(1, 1)
    K.bedroll(t, x, F, z, color='green', along='x', length=2)
    x, z = tent2(1, 3)
    t.set(x, F, z, K.CRATE)
    fx, fz = 8, 11
    K.fire_pit(t, fx, F, fz, rng, stones=0.5)
    t.anchor('FIRE', fx, F, fz)
    t.set(fx - 2, F, fz, K.log(axis='z'))
    t.set(fx - 2, F, fz + 1, K.log(axis='z'))
    t.set(fx + 1, F, fz - 2, K.log(axis='x'))
    t.set(fx + 2, F, fz - 2, K.log(axis='x'))
    t.set(fx + 2, F, fz + 1, S('cauldron'))
    K.lamp_post(t, 6, F, 8, height=3, arm='east')

    # --- the strongroom stack and the cargo hauler to the east --------------------------------------------------
    K.lean_to(t, 23, 18, 25, 22, F, 'west', 4)
    for z in (18, 22):
        K.column(t, 23, F, F + 3, z, K.beam('y'))
    for x in range(23, 27):
        for z in range(18, 23):
            t.set(x, G, z, K.GRAVEL if (x + z) % 3 else K.COARSE)
    K.crate_pile(t, [(25, 19, 2), (25, 20, 2), (25, 21, 2), (24, 21, 1), (26, 20, 2), (26, 19, 1)], F, rng,
                 kinds=((K.CRATE_IMP, 2), (K.CRATE, 2), (K.CRATE_REP, 1)), tarp=0.0)
    t.set(24, F, 19, K.chain('z'))
    hauler(t, 21, F, 7, rng)
    for x in range(20, 25):
        for z in range(6, 15):
            if t.get(x, G, z) is None:
                t.set(x, G, z, K.COARSE if (x + z) % 3 else K.GRAVEL)
    t.set(19, F, 10, K.CRATE)
    t.set(19, F, 11, K.CRATE_REP)
    t.set(19, F + 1, 10, K.CRATE)
    K.lamp_post(t, 24, F, 15, height=3, arm='west')

    # --- people -------------------------------------------------------------------------------------------------
    K.spot(t, 'FENCE', 16, F, Z1 - 2)       # Lisk beside his ledger
    K.spot(t, 'CREW_1', 14, F, gz + 2)      # at the gate
    K.spot(t, 'CREW_2', fx, F, fz + 2)      # at the fire
    K.spot(t, 'CREW_3', 23, F, 20)          # at the strongroom
    K.spot(t, 'CREW_4', 20, F, 13)          # loading the hauler
    K.spot(t, 'HEART', 15, F, Z0 - 3)       # before the pavilion

    K.litter(t, F, (3, 6, 27, 28), [(K.DEBRIS, 3), (K.SCORCH, 1)], 0.025, rng)
    return t


# =============================================================================================
# fence_camp_2: The Waystation
# =============================================================================================

RUIN_MIX = [(K.RUIN_BRICKS, 5), (K.RUIN, 3), (K.RUIN_CRACKED, 2)]


def ruin_wall(t, cells, y, heights, mix, rng, moss, outward=None, pilaster_every=4, cap=True):
    """A ruined wall along `cells` (in order): a plinth course of big stone, a body of mixed bricks
    (mossy low down where water runs), a slab cap where it still stands to its full height, pilasters
    of ruin pillar every few cells standing one higher (and one proud, toward `outward`)."""
    for i, (x, z) in enumerate(cells):
        h = heights(i, x, z)
        if h <= 0:
            continue
        t.set(x, y - 1, z, K.RUIN)
        for k in range(h):
            yy = y + k
            if k == 0:
                s = K.RUIN if (x + z) % 3 else K.RUIN_CARVED
            elif k <= 1 and moss.at(x, yy, z) > 0.62:
                s = K.RUIN_MOSSY
            else:
                s = mix(x, yy, z)
            t.set(x, yy, z, s)
        if pilaster_every and i % pilaster_every == 0 and h >= 2:
            K.column(t, x, y, y + h, z, K.ruin_pillar('y'))
            if outward:
                ox, oz = x + outward[0], z + outward[1]
                if t.inside(ox, y, oz) and t.get(ox, y, oz) is None:
                    K.column(t, ox, y, y + min(h, 2) - 1, oz, K.RUIN)
                    t.set(ox, y + min(h, 2), oz, K.slab('or:ruin_brick_slab'))
                    t.set(ox, y - 1, oz, K.RUIN)
        elif cap and h >= 3 and rng.random() < 0.7:
            t.set(x, y + h, z, K.slab('or:ruin_brick_slab'))


def rubble(t, y, cx, cz, r, rng, direction, density=0.6):
    """Fallen stone below a collapsed stretch of wall, thrown toward `direction` (dx, dz)."""
    for k in range(int(r * 6)):
        d = rng.random() * r
        x = int(round(cx + direction[0] * d + (rng.random() - 0.5) * 2.2))
        z = int(round(cz + direction[1] * d + (rng.random() - 0.5) * 2.2))
        if not t.inside(x, y, z) or t.get(x, y, z) is not None or rng.random() > density:
            continue
        r_ = rng.random()
        s = K.slab('or:ruin_brick_slab') if r_ < 0.4 else (K.RUIN_CRACKED if r_ < 0.6 else (
            K.stairs('or:ruin_brick_stairs', rng.choice(['north', 'south', 'east', 'west'])) if r_ < 0.8 else K.RUIN))
        if 'stairs' in s:
            s = S('or:ruin_brick_stairs', facing=rng.choice(['north', 'south', 'east', 'west']), half='bottom',
                  shape='straight', waterlogged=False)
        t.set(x, y, z, s)
        K.support(t, x, y, z, K.GRAVEL)


def stall(t, x0, z0, x1, z1, y, rng, cloth, goods):
    """A market stall: four posts, a cloth awning, a counter of crates and plates along its front (-z)."""
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        K.column(t, x, y, y + 2, z, K.fence('spruce_fence'))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, y + 3, z, cloth if x % 2 else K.CANVAS)
        t.set(x, y + 3, z0 - 1, K.canvas_stairs('south', 'top')) if False else None
    for x in range(x0 + 1, x1):
        t.set(x, y, z0, K.PLATING_SLAB_TOP if x % 2 else K.CRATE)
        if goods:
            t.set(x, y + 1, z0, K.pick(rng, goods))


def build_waystation():
    t = Template(*SIZE, seed=6401)
    rng = t.rng
    G, F = 0, 1
    worn = K.Mix(K.WORN, 6403, scale=4.5, jitter=0.15)
    track = K.Mix(K.TRACK, 6405, 3.5, 0.2)
    bricks = K.Mix(RUIN_MIX, 6407, scale=2.4, jitter=0.25)
    moss = Noise(6409, 2.0)
    hn = Noise(6411, 3.4)

    WX0, WX1, WZ0, WZ1 = 5, 25, 8, 27   # the compound's outer walls
    HZ = 19                              # the hall's front wall

    # --- ground: the road along the north, the courtyard paved with old flags and worn earth --------
    K.trail(t, G, [(-1, 4.0), (8, 4.5), (15, 3.5), (22, 4.0), (31, 3.0)], 4, track, 6413)
    K.trail(t, G, [(15, 4), (15, WZ0)], 4, track, 6415)
    flags = K.Mix([(K.PALE_TILES, 1), (K.RUIN, 3), (K.RUIN_CRACKED, 2), (K.GRAVEL, 2), (K.COARSE, 2)], 6417, 2.0, 0.4)
    for x in range(WX0 + 1, WX1):
        for z in range(WZ0 + 1, WZ1):
            t.set(x, G, z, flags(x, G, z) if z < HZ else K.Mix(K.WORN, 6419, 4.0, 0.2)(x, G, z))
    K.ground_blob(t, G, 15, 17.5, 14.5, 13.0, worn, 6421, fray=0.25, only_open=True)

    # --- the outer walls, broken; the arch of the north gate -------------------------------------------
    north = [(x, WZ0) for x in range(WX0, WX1 + 1)]
    south = [(x, WZ1) for x in range(WX0, WX1 + 1)]
    west = [(WX0, z) for z in range(WZ0 + 1, WZ1)]
    east = [(WX1, z) for z in range(WZ0 + 1, WZ1)]

    def outer_h(i, x, z):
        if WZ0 < z < WZ1 and 12 <= x <= 18 and z == WZ0:
            return 0
        if z == WZ0 and 12 <= x <= 18:
            return 0  # the gate
        base = 1 + int(round(4.6 * hn.at(x, 0, z)))
        if z >= HZ:
            base += 2  # the hall's walls stand higher
        if (x <= 9 and z == WZ0) or (x == WX0 and 10 <= z <= 14) or (x == WX1 and 14 <= z <= 16):
            base = 1 if (x + z) % 3 else 0  # collapsed stretches, their stones fallen outward
        return max(0, min(6, base))
    ruin_wall(t, north, F, outer_h, bricks, rng, moss, outward=(0, -1))
    ruin_wall(t, south, F, outer_h, bricks, rng, moss, outward=(0, 1))
    ruin_wall(t, west, F, outer_h, bricks, rng, moss, outward=(-1, 0))
    ruin_wall(t, east, F, outer_h, bricks, rng, moss, outward=(1, 0))
    rubble(t, F, 7, WZ0 - 1, 3.0, rng, (0, -1))
    rubble(t, F, WX0 - 1, 12, 3.0, rng, (-1, 0))
    rubble(t, F, WX1 + 1, 15, 2.5, rng, (1, 0))
    # the gate arch: two pillars, the lintel broken at the east end, a carved keystone
    for x in (12, 18):
        K.column(t, x, F, F + 4, WZ0, K.ruin_pillar('y'))
        t.set(x, G, WZ0, K.RUIN)
    for x in range(12, 17):
        t.set(x, F + 5, WZ0, K.RUIN_BRICKS if x != 15 else K.RUIN_CARVED)
    t.set(13, F + 4, WZ0, S('or:ruin_brick_stairs', facing='east', half='top', shape='straight', waterlogged=False))
    t.set(17, F + 1, WZ0 - 1, K.RUIN_CRACKED)
    t.set(17, F, WZ0 - 2, K.slab('or:ruin_brick_slab'))
    K.support(t, 17, F, WZ0 - 2, K.GRAVEL)
    K.support(t, 17, F + 1, WZ0 - 1, K.GRAVEL)
    t.set(13, F + 3, WZ0 + 1, K.lantern(True)) if False else None
    t.set(14, F + 4, WZ0, K.chain('y'))
    t.set(14, F + 3, WZ0, K.lantern(True))
    K.banner(t, 16, F + 4, WZ0 + 1, 'south', 'red')
    for x in range(13, 18):
        for y in (F, F + 1, F + 2):
            if t.get(x, y, WZ0) is None:
                t.set(x, y, WZ0, AIR)

    # --- the hall: its front wall with a wide door, the tarp roof, Lisk's counting room ---------------------------
    front = [(x, HZ) for x in range(WX0 + 1, WX1)]

    def front_h(i, x, z):
        if 14 <= x <= 16:
            return 0  # the door
        return max(3, min(6, 4 + int(round(2.4 * (hn.at(x, 5, z) - 0.5)))))
    ruin_wall(t, front, F, front_h, bricks, rng, moss, outward=(0, -1))
    for x in (13, 17):
        K.column(t, x, F, F + 4, HZ, K.ruin_pillar('y'))
    for x in range(13, 18):
        t.set(x, F + 4, HZ, K.RUIN_BRICKS) if x in (14, 15, 16) else None
    t.set(15, F + 4, HZ, K.RUIN_CARVED)
    # a curtain half drawn across the door
    t.set(16, F + 3, HZ, K.CANVAS_CRIMSON)
    t.set(16, F + 2, HZ, K.CANVAS_CRIMSON)
    for x in range(14, 17):
        for y in (F, F + 1):
            if t.get(x, y, HZ) is None:
                t.set(x, y, HZ, AIR)
    # the tarp roof on beams laid across the hall walls, sagging between them, torn in places
    RY = F + 6
    for x in range(WX0, WX1 + 1):
        for z in range(HZ, WZ1 + 1):
            wall = x in (WX0, WX1) or z in (HZ, WZ1)
            if x % 4 == 1 and not wall:
                t.set(x, RY - 1, z, K.beam('z'))
            if wall:
                if t.get(x, RY - 1, z) is None and t.get(x, RY - 2, z) is not None:
                    t.set(x, RY - 1, z, K.RUIN_BRICKS if (x + z) % 3 else K.RUIN_CRACKED)
                continue
            if x % 4 == 1 or moss.at(x * 2, 9, z * 2) > 0.74:
                continue  # a beam, or a tear
            bay = (x - 2) // 4
            t.set(x, RY - 1, z, (K.CANVAS_NAVY, K.CANVAS_CRIMSON, K.CANVAS, K.CANVAS_NAVY, K.CANVAS_CRIMSON)[bay % 5])
    for x in range(WX0, WX1 + 1, 4):
        for z in (HZ, WZ1):
            if t.get(x, RY - 1, z) is None:
                t.set(x, RY - 1, z, K.RUIN)
    # fill the hall's walls up to the roof where they stand
    for (x, z) in front + south + [(WX0, z) for z in range(HZ, WZ1)] + [(WX1, z) for z in range(HZ, WZ1)]:
        top = max([y for y in range(F, RY) if t.get(x, y, z) not in (None, AIR)] or [0])
        if top >= F + 2 and not (14 <= x <= 16 and z == HZ):
            for y in range(top + 1, RY - 1):
                t.set(x, y, z, K.CANVAS if (x + y) % 3 else K.CANVAS_NAVY)
    for x in range(WX0 + 1, WX1):
        for z in range(HZ + 1, WZ1):
            t.set(x, G, z, K.RUIN if (x * 5 + z) % 6 else K.RUIN_CRACKED)
            for y in range(F, RY - 1):
                if t.get(x, y, z) is None:
                    t.set(x, y, z, AIR)
    # inside: the ledger on its lectern on a dais at the back, the desk, the strongbox, shelves of goods
    for x in range(12, 19):
        t.set(x, F, WZ1 - 1, K.slab('or:ruin_brick_slab', 'top') if x in (12, 18) else K.slab('or:pale_tile_slab', 'top'))
    mod.lectern(t, 15, F + 1, WZ1 - 1, facing='north', book=True)
    t.anchor('BOOK', 15, F + 1, WZ1 - 1)
    t.set(15, F, WZ1 - 2, S('or:pale_tile_stairs', facing='south', half='bottom', shape='straight', waterlogged=False))
    for x in (13, 17):
        t.set(x, F + 1, WZ1 - 1, K.candle(3, lit=True) if x == 13 else K.candle(2, lit=True))
    for x in range(11, 20):
        for z in (WZ1 - 3, WZ1 - 4):
            if (x + z) % 4 and t.get(x, F, z) is None:
                t.set(x, F, z, K.carpet('red' if x % 3 else 'brown'))
    t.set(19, F, HZ + 2, K.PLATING_SLAB_TOP)
    t.set(20, F, HZ + 2, K.PLATING_SLAB_TOP)
    t.set(19, F + 1, HZ + 2, K.candle(1, lit=True))
    t.set(20, F + 1, HZ + 2, K.cluster())
    t.set(21, F, HZ + 2, K.CANVAS_SLAB)
    for (x, h) in ((7, 3), (8, 2), (9, 3), (21, 2), (22, 3), (23, 2)):
        K.crate_pile(t, [(x, WZ1 - 1, h)], F, rng, kinds=((K.CRATE, 3), (K.CRATE_IMP, 1), (K.CRATE_REP, 1)))
    for x in (8, 21, 23):
        t.set(x, F + (2 if x != 22 else 3), WZ1 - 1, K.cluster())
    t.set(7, F, WZ1 - 2, K.CRATE_IMP)
    t.set(8, F, WZ1 - 2, K.CRATE_IMP)
    t.set(7, F + 1, WZ1 - 2, K.chain('x'))
    t.set(8, F + 1, WZ1 - 2, K.chain('x'))
    t.set(7, F, HZ + 1, S('or:engineering_bench')) if False else None
    t.set(23, F, HZ + 1, S('amethyst_block'))
    t.set(23, F + 1, HZ + 1, S('large_amethyst_bud', facing='up', waterlogged=False))
    # his counting table, his records on shelves at the west end, his cot at the east end
    for x in range(9, 13):
        for z in (22, 23):
            t.set(x, F, z, K.PLATING_SLAB_TOP if (x + z) % 3 else S('spruce_trapdoor', facing='north', half='top',
                                                                         open=False, powered=False, waterlogged=False))
    t.set(9, F + 1, 22, K.candle(2, lit=True))
    t.set(10, F + 1, 23, K.carpet('white'))
    t.set(11, F + 1, 22, K.carpet('white'))
    t.set(12, F + 1, 23, S('heavy_weighted_pressure_plate', power=0))
    t.set(11, F + 1, 23, K.cluster())
    t.set(10, F, 21, K.CANVAS_SLAB)
    t.set(13, F, 24, K.CANVAS_SLAB)
    for z in range(HZ + 2, WZ1 - 1):
        for y in (F, F + 1):
            t.set(WX0 + 1, y, z, S('bookshelf') if (z + y) % 3 else K.CRATE)
    for (x, z) in ((22, 24), (23, 24), (22, 25), (23, 25)):
        t.set(x, F, z, K.carpet('blue'))
    K.bedroll(t, 23, F, 23, color='red', along='x', length=1)
    t.set(22, F, 23, K.carpet('red'))
    t.set(24, F, 25, K.PLATING_SLAB_TOP) if t.get(24, F, 25) is None else None
    # a brazier and hanging lamps; the light topped up
    t.set(10, F, HZ + 3, K.COBBLE)
    t.set(10, F + 1, HZ + 3, K.campfire(lit=True, kind='soul_campfire') if False else K.lantern())
    for x in (9, 21):
        t.set(x, RY - 2, 23, K.chain('y')) if t.get(x, RY - 1, 23) not in (None, AIR) else None
        t.set(x, RY - 3, 23, K.lantern(True)) if t.get(x, RY - 2, 23) not in (None, AIR) else None
    mod.light_fill(t, (WX0 + 1, F, HZ + 1, WX1 - 1, F + 3, WZ1 - 1))

    # --- the broken watchtower in the north-east corner, Lisk's lamp on top ---------------------------------
    TX0, TZ0, TX1, TZ1 = 21, WZ0, WX1, WZ0 + 4
    tops = {}
    for x in range(TX0, TX1 + 1):
        for z in range(TZ0, TZ1 + 1):
            if x in (TX0, TX1) or z in (TZ0, TZ1):
                h = 9 + int(round(3 * hn.at(x * 3, 7, z * 3)))
                if x == TX0 and z == TZ1:
                    h = 6
                tops[(x, z)] = h
                t.set(x, G, z, K.RUIN)
                for y in range(F, F + h):
                    s = K.RUIN if y == F else (K.RUIN_MOSSY if y <= F + 1 and moss.at(x, y, z) > 0.6 else bricks(x, y, z))
                    t.set(x, y, z, s)
            else:
                t.set(x, G, z, K.RUIN)
                for y in range(F, F + 11):
                    t.set(x, y, z, AIR)
    # door from the courtyard, windows, the platform and the lamp
    for y in (F, F + 1, F + 2):
        t.set(TX0 + 2, y, TZ1, AIR)
    t.set(TX0 + 2, F + 3, TZ1, K.RUIN_CARVED)
    for (x, z) in ((TX1, TZ0 + 2), (TX0 + 2, TZ0)):
        t.set(x, F + 6, z, AIR)
        t.set(x, F + 7, z, AIR)
    for x in range(TX0 + 1, TX1):
        for z in range(TZ0 + 1, TZ1):
            t.set(x, F + 8, z, K.DECK_SLAB_TOP if (x + z) % 2 else K.GRATE)
    t.set(TX0 + 1, F + 8, TZ0 + 1, AIR)
    for y in range(F, F + 9):
        t.set(TX0 + 1, y, TZ0 + 1, K.ladder('south'))
    t.set(TX1 - 1, F + 9, TZ0 + 1, K.CRATE)
    t.set(TX1 - 1, F + 10, TZ0 + 1, K.lantern())
    t.set(TX0 + 2, F + 9, TZ1 - 1, K.CANVAS_SLAB)
    K.column(t, TX1, F + tops[(TX1, TZ0)], F + tops[(TX1, TZ0)] + 1, TZ0, K.beam('y'))
    t.set(TX1, F + tops[(TX1, TZ0)] + 2, TZ0, K.LIGHT_AMBER)
    t.set(TX1, F + tops[(TX1, TZ0)] + 3, TZ0, K.rod())
    rubble(t, F, TX0 - 1, TZ1 + 1, 2.0, rng, (-1, 1))
    mod.light_fill(t, (TX0 + 1, F, TZ0 + 1, TX1 - 1, F + 9, TZ1 - 1))

    # --- the courtyard: the fire, the cistern, market stalls, a guard's lean-to ------------------------------
    fx, fz = 14, 14
    K.fire_pit(t, fx, F, fz, rng, stones=0.45)
    t.anchor('FIRE', fx, F, fz)
    t.set(fx - 2, F, fz + 1, K.slab('or:ruin_brick_slab'))
    t.set(fx + 2, F, fz - 1, K.slab('or:ruin_brick_slab'))
    t.set(fx + 1, F, fz + 2, K.log(axis='x'))
    # the cistern: a ring of carved stone round still water
    for (x, z) in ((19, 13), (20, 13), (21, 13), (19, 14), (21, 14), (19, 15), (20, 15), (21, 15)):
        t.set(x, F, z, K.RUIN_CARVED if (x + z) % 2 else K.RUIN_BRICKS)
        t.set(x, G, z, K.RUIN)
    t.set(20, F, 14, S('water', level=0))
    t.set(20, G, 14, K.RUIN)
    t.set(19, F + 1, 13, K.chain('y')) if False else None
    t.set(21, F + 1, 15, S('cauldron'))
    # stalls along the west wall: cloth, crystals, scrap parts
    stall(t, 7, 10, 10, 12, F, rng, K.CANVAS_CRIMSON, [(K.cluster(), 2), (S('small_amethyst_bud', facing='up', waterlogged=False), 1)])
    stall(t, 7, 14, 10, 16, F, rng, K.CANVAS_NAVY, [(K.pot(), 1), (K.candle(2, lit=False), 1), (S('chipped_anvil', facing='east'), 0)])
    t.set(8, F, 17, K.CRATE)
    t.set(9, F, 17, K.CRATE_REP)
    K.lamp_post(t, 11, F, 17, height=3, arm='west')
    # a guard's lean-to inside the east wall, his bedroll
    K.lean_to(t, 22, 14, 24, 17, F, 'east', 3)
    for z in (14, 17):
        K.column(t, 24, F, F + 2, z, K.beam('y')) if t.get(24, F, z) is None else None
    K.bedroll(t, 23, F, 15, color='gray', along='z', length=2)

    # --- people -------------------------------------------------------------------------------------------------
    K.spot(t, 'FENCE', 17, F, WZ1 - 3)      # Lisk before his dais
    K.spot(t, 'CREW_1', 15, F, WZ0 + 2)     # inside the gate
    K.spot(t, 'CREW_2', fx - 1, F, fz - 2)  # at the fire
    K.spot(t, 'CREW_3', 23, F, TZ1 + 2)     # by the tower door
    K.spot(t, 'CREW_4', 11, F, 13)          # among the stalls
    K.spot(t, 'HEART', 15, F, HZ - 2)       # before the hall's door

    K.litter(t, F, (WX0 + 1, WZ0 + 1, WX1 - 1, HZ - 1), [(K.DEBRIS, 3), (K.slab('or:ruin_brick_slab'), 1)], 0.03, rng)
    return t


def main():
    a = K.args()
    for tid, build in (('mission/fence_camp', build_pavilion), ('mission/fence_camp_2', build_waystation)):
        if a.only and a.only not in tid:
            continue
        K.save(build(), tid, ANCHORS, META)


if __name__ == '__main__':
    main()
