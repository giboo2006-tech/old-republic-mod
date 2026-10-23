"""tyrvann/mire_causeway (worldgen, Tyrvann mire; docs/MILESTONE_6.md 5.6): the drowned road and its fallen gate.

Before the Hymn came, the silenced city's road ran out across the mire on a causeway of black basalt and
stormstone: big flags between basalt kerbs, song-posts every few strides whose copper pipes the wind
still plays, and halfway across a gatehouse, two towers and an arch, where the city took its toll. The
mire has had it since. The causeway has sunk, so the road goes under the black water and comes up
again; flags are lost to the mud, roots climb through the kerbs, and the gate is down.

Two starts:
  causeway_a  57 long: the road sinks at both ends and once in the middle; the west tower of the gate
              still stands with its toll room (the chest) and half the arch; the east tower snapped and
              lies along the road in the water; one copper door leaf lies flat in the road, the other
              leans on the tower; a stormwood grows from the bank; a song-stone by the road.
  causeway_b  47 long: the road breaks in a wide breach (a fallen trunk and stepping stones across it);
              both towers snapped to stumps, the arch fell north across the road in one piece and lies
              there in pieces; a stormwood grows out of the west stump; the toll chest in the east stump.

Frame (buried 2): y 0 the footings and the sunk flags, y 1 the water line (the road's flags), y 2 the walk.
Every column that carries anything is solid at y 0 (the pool element's supports go on to the bed).
"""
from common import *  # noqa: F401,F403
import choir_ruin

NAME = 'tyrvann/mire_causeway'
SALT = 960353
BURIED = 2
WL, WK = 1, 2
SIZES = {'a': (21, 22, 57), 'b': (21, 20, 47)}
VARIANTS = ('a', 'b')
XC = 10
ROAD = tuple(range(7, 14))
KERBS = (6, 14)
TOWER_W, TOWER_E = (1, 5), (15, 19)
GZ = 30                                    # the gate's middle row in both starts
SPRING, PARAPET, TOWER_TOP = 7, 13, 15
LOOT_ID = 'oldrepublic:chests/tyrvann/mire_causeway'
CAMERAS = {}

FLAGS = [('minecraft:deepslate_tiles', 3), ('or:stormstone', 3), ('or:stormstone_bricks', 2),
         ('minecraft:polished_deepslate', 2), ('minecraft:smooth_basalt', 2), ('minecraft:polished_basalt[axis=y]', 1)]
FOOT = [('minecraft:cobbled_deepslate', 3), ('minecraft:tuff', 1), ('minecraft:mossy_cobblestone', 1),
        ('minecraft:basalt[axis=y]', 1)]
KERB = [('minecraft:polished_basalt[axis=z]', 4), ('minecraft:smooth_basalt', 2), ('minecraft:basalt[axis=z]', 1)]
KERB_WALL = [('minecraft:polished_blackstone_wall', 3), ('minecraft:deepslate_brick_wall', 2),
             ('minecraft:cobbled_deepslate_wall', 1)]
SHOULDER = [('minecraft:cobbled_deepslate', 3), ('minecraft:mossy_cobblestone', 2), ('minecraft:basalt[axis=y]', 2),
            ('minecraft:tuff', 1), ('minecraft:smooth_basalt', 1)]
FALLEN = [('or:stormstone_bricks', 4), ('minecraft:cracked_deepslate_bricks', 2), ('or:stormstone', 2),
          ('or:stormstone_brick_slab[type=bottom]', 2), ('minecraft:cobbled_deepslate', 1)]
DIRT = {'minecraft:mud', 'minecraft:moss_block', 'minecraft:muddy_mangrove_roots', 'minecraft:rooted_dirt',
        'minecraft:coarse_dirt', 'minecraft:podzol', 'minecraft:dirt', 'minecraft:grass_block'}
COPPER_LEAF = 'minecraft:oxidized_copper_trapdoor[facing=%s,half=bottom,open=%s,powered=false,waterlogged=false]'


def no_giant(x, z, state):
    """#dirt never in a cell of even x and z (no open 2x2 for the stormwood giant): peat takes its place."""
    if x % 2 == 0 and z % 2 == 0 and B.name_of(state) in DIRT:
        return 'or:peat'
    return state


def core(x, z):
    """What shows where a flag is lost: the causeway's fill of gravel, mud and broken stone."""
    if x % 2 == 0 and z % 2 == 0:
        return 'minecraft:gravel'
    return ('minecraft:mud', 'minecraft:cobbled_deepslate', 'minecraft:mud', 'minecraft:gravel',
            'minecraft:mossy_cobblestone', 'minecraft:mud')[(x * 7 + z * 13) % 6]


def causeway(t, rng, seed, sink, lost_rate=0.05):
    """The road along z: big flags at the water line on footings, or down on the bed where it has sunk;
    basalt kerbs with a low wall in runs; the embankment's stones breaking the water at the shoulders.
    sink(z) in 0..1. Returns (hi, lo): road and kerb cells at the water line and on the bed."""
    sx, sy, sz = t.size
    edge = Noise(seed + 1, 3.0)
    lostn = Noise(seed + 2, 2.2)
    wallrun = Noise(seed + 3, 4.0)
    shoul = Noise(seed + 4, 2.0)
    hi, lo = set(), set()
    for z in range(sz):
        s = sink(z)
        for x in ROAD + KERBS:
            (lo if s + 0.45 * (edge.at(x, z) - 0.5) > 0.5 else hi).add((x, z))
    for (x, z) in sorted(hi):
        t.set(x, 0, z, pick(rng, FOOT))
    road_hi = sorted(c for c in hi if c[0] in ROAD)
    road_lo = sorted(c for c in lo if c[0] in ROAD)
    flag_floor(t, road_hi, WL, seed + 5, FLAGS, size=2, origin=(ROAD[0], 0),
               lost=lambda x, z, h: lostn.at(x, z) > 0.72 or h.random() < lost_rate, lost_state=core)
    flag_floor(t, road_lo, 0, seed + 5, FLAGS, size=2, origin=(ROAD[0], 0),
               lost=lambda x, z, h: h.random() < 0.35, lost_state=core)
    for z in range(sz):
        for x in KERBS:
            if (x, z) in hi:
                if rng.random() < 0.07:
                    t.set(x, WL, z, core(x, z))
                    continue
                t.set(x, WL, z, pick(rng, KERB))
                if wallrun.at(x * 3, z) > 0.55 and rng.random() < 0.85:
                    t.set(x, WK, z, pick(rng, KERB_WALL))
            else:
                t.set(x, 0, z, pick(rng, KERB) if rng.random() < 0.7 else pick(rng, FOOT))
        for (x, d) in ((5, 1), (4, 2), (3, 3), (15, 1), (16, 2), (17, 3)):
            v = shoul.at(x, z) + 0.12 * (2 - d)
            if d == 1 or v > 0.45:
                t.set(x, 0, z, no_giant(x, z, pick(rng, SHOULDER + [('minecraft:mud', 2)])))
            if d <= 2 and v > 0.6 and sink(z) < 0.55:
                t.set(x, WL, z, no_giant(x, z, pick(rng, SHOULDER + [('minecraft:moss_block', 1),
                                                                    ('minecraft:muddy_mangrove_roots', 1)])))
    return hi, lo


def song_post(t, rng, x, z, kind='pipe', fallen=None):
    """A song-post on the kerb: a basalt shaft, a chiseled collar and a copper pipe the wind still plays (or
    a cold soul lantern). fallen ('west' or 'east') lies across the shoulder in the water, a stump left."""
    t.set(x, 0, z, pick(rng, FOOT))
    t.set(x, WL, z, 'minecraft:polished_basalt[axis=y]')
    if fallen:
        dx = -1 if fallen == 'west' else 1
        t.set(x, WK, z, 'minecraft:polished_basalt[axis=y]')
        t.set(x, WK + 1, z, AIR)
        for k in range(1, 6):
            xx = x + dx * k
            t.set(xx, 0, z, pick(rng, FOOT))
            st = ('minecraft:polished_basalt[axis=x]' if k < 3 else 'minecraft:chiseled_deepslate' if k == 3
                  else COPPER['grate'] if k == 4 else rod(facing=fallen))
            t.set(xx, WL, z, st)
        return
    for y in (WK, WK + 1):
        t.set(x, y, z, 'minecraft:polished_basalt[axis=y]')
    t.set(x, WK + 2, z, 'minecraft:chiseled_deepslate')
    if kind == 'pipe':
        t.set(x, WK + 3, z, COPPER['grate'])
        t.set(x, WK + 4, z, rod())
        t.set(x, WK + 5, z, rod())
    else:
        t.set(x, WK + 3, z, lantern(soul=True))


def song_stone(t, rng, x0, z0, side):
    """A song-stone on a little landing off the road: a basalt stele with stormglass grown on its top,
    black candles at its foot, a kneeling step turned to it. side 'west' or 'east' of the road."""
    xs = range(x0, x0 + 3)
    for x in xs:
        for z in range(z0, z0 + 3):
            t.set(x, 0, z, pick(rng, FOOT))
            t.set(x, WL, z, pick(rng, [('minecraft:polished_deepslate', 3), ('minecraft:deepslate_tiles', 2),
                                      ('minecraft:cracked_deepslate_tiles', 1)]))
    spur = range(x0 + 3, KERBS[0]) if side == 'west' else range(KERBS[1] + 1, x0)
    for x in spur:
        for z in range(z0, z0 + 3):
            t.set(x, 0, z, pick(rng, FOOT))
            t.set(x, WL, z, pick(rng, FLAGS))
    sxx = x0 if side == 'west' else x0 + 2
    stele(t, rng, sxx, z0 + 1, WK, 3, top='minecraft:chiseled_deepslate')
    t.set(sxx, WK + 4, z0 + 1, cluster('up'))
    kx = x0 + 1
    kneeler(t, kx, WK, z0 + 1, side)
    candles_at(t, rng, [(sxx, WK, z0), (sxx, WK, z0 + 2)])
    for (x, z) in ((kx, z0), (kx, z0 + 2)):
        if rng.random() < 0.6:
            t.set(x, WK, z, candle(rng.randint(1, 3), 'black'))


def tower(t, rng, xr, z0, z1, top, mat, door_x=None, ceiling=None, slit=True):
    """A gate tower: walls of masonry on footings from the bed to top(x, z) (a broken crown), basalt
    corner shafts, a floor at the water line inside and open air over it, a doorway at door_x onto the
    gate passage, an optional stone ceiling over the room, arrow slits."""
    x0, x1 = xr
    zc = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, pick(rng, FOOT))
            h = top(x, z)
            edge = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            if edge:
                for y in range(WL, h + 1):
                    st = HYMN['pillar'] if corner else ('minecraft:polished_deepslate' if y in (WL, SPRING)
                                                        else mat(x, y, z))
                    t.set(x, y, z, st)
            else:
                t.set(x, WL, z, pick(rng, HYMN.options('floor')))
                for y in range(WK, max(h, WK) + 1):
                    t.set(x, y, z, AIR)
                if ceiling and ceiling < h:
                    t.set(x, ceiling, z, slab('or:stormstone_brick_slab', 'top'))
    if door_x is not None and top(door_x, zc) >= WK:
        for y in range(WK, min(top(door_x, zc), WK + 1) + 1):
            t.set(door_x, y, zc, AIR)
        if top(door_x, zc) >= WK + 2:
            t.set(door_x, WK + 2, zc, HYMN['chisel'])
    if slit:
        for (x, z) in ((x0, zc), (x1, zc), ((x0 + x1) // 2, z0), ((x0 + x1) // 2, z1)):
            if x == door_x:
                continue
            for y in (5, 6):
                if top(x, z) > y + 1 and t.is_solid(x, y, z):
                    t.set(x, y, z, S('iron_bars'))


def arch_cells():
    """The gate's arch over the passage (x 6..14, rows GZ-1..GZ+1): a round opening 9 wide springing
    at y 7, the crown at 12, a crenellated parapet at 13. Returns {(x, y, z): role}."""
    out = {}
    for x in range(KERBS[0], KERBS[1] + 1):
        for y in range(SPRING, PARAPET + 1):
            r = math.hypot(x - XC, y - SPRING + 0.5)
            if r < 4.6:
                continue
            if y == PARAPET and (x - XC) % 2:
                continue
            ring = r < 5.7 and y < PARAPET - 1
            for z in range(GZ - 1, GZ + 2):
                out[(x, y, z)] = 'key' if (x == XC and y == PARAPET - 1) else 'ring' if ring else 'wall'
    return out


def arch_state(rng, mat, role, x, y, z):
    if role == 'key':
        return HYMN['chisel']
    if role == 'ring':
        return pick(rng, [('minecraft:polished_deepslate', 3), ('minecraft:deepslate_tiles', 1)])
    return mat(x, y, z)


def land(t, x, z, y_from, state):
    """Drop a fallen stone onto the first solid cell under (x, z) at or below y_from (on a column the
    template sets); returns the y it came to rest at, or None."""
    y = y_from
    while y > 0 and not t.is_solid(x, y - 1, z):
        y -= 1
    if y <= 0 or not t.is_solid(x, y - 1, z) or not t.inside(x, y, z):
        return None
    t.set(x, y, z, state)
    return y


def stormwood_on_bank(t, rng, x, z, h, crown=True, lean=(0, 0)):
    """A stormwood on a hummock of the bank: a mound of mud and roots on the bed, mangrove roots arching
    into the water round the foot, the dark trunk and its crown (choir_ruin's tree)."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            t.set(x + dx, 0, z + dz, 'minecraft:mud')
            st = 'minecraft:muddy_mangrove_roots' if (dx or dz) else 'minecraft:rooted_dirt'
            t.set(x + dx, WL, z + dz, no_giant(x + dx, z + dz, st))
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2), (-2, 1), (2, -1)):
        if rng.random() < 0.8:
            t.set(x + dx, 0, z + dz, 'minecraft:mud')
            t.set(x + dx, WL, z + dz, roots(False))
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        if rng.random() < 0.7:
            t.set(x + dx, WK, z + dz, roots(False))
    choir_ruin.stormwood(t, rng, x, z, WK, h, crown=crown, lean=lean)


def cart(t, rng, x, z):
    """A hand cart left on the road by someone fleeing the city: a plank bed on dark trapdoor wheels,
    a barrel and a sack still on it, one wheel off."""
    for dz in range(3):
        t.set(x, WK, z + dz, slab('minecraft:spruce_slab', 'top'))
        t.set(x + 1, WK, z + dz, slab('minecraft:spruce_slab', 'top'))
    t.set(x - 1, WK, z + 1, 'minecraft:dark_oak_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]')
    t.set(x + 3, WK, z + 2, 'minecraft:dark_oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
    t.set(x, WK + 1, z, barrel('up'))
    t.set(x + 1, WK + 1, z + 1, 'minecraft:brown_wool')
    t.set(x, WK, z + 3, S('dark_oak_fence'))
    t.set(x + 1, WK, z + 3, S('dark_oak_fence'))


def finish(t, rng, hi, lo, keep_clear=()):
    """Open air over the road (clears the world's reeds and terrain), moss and roots on it, pale moss
    under the arch and the trees, stray blocks pruned, the tree guard over it all."""
    sx, sy, sz = t.size
    for (x, z) in sorted(hi | lo):
        for y in range(WK, min(sy, WK + 6)):
            if (x, y, z) not in t.cells:
                t.set(x, y, z, AIR)
    for (x, z) in sorted(hi):
        if t.is_air(x, WK, z) and (x, WK, z) in t.cells and t.is_solid(x, WL, z) and rng.random() < 0.05:
            t.set(x, WK, z, pick(rng, [('minecraft:moss_carpet', 3), ('minecraft:leaf_litter[facing=north,segment_amount=2]', 2)]))
    hang_moss(t, rng, ((0, WK + 1, 0), (sx - 1, sy - 1, sz - 1)), chance=0.10, max_len=3)
    gone = prune_floating(t)
    if gone:
        print('  pruned %d in %s' % (len(gone), t.name))
    cols = {(x, z) for (x, y, z) in t.cells if t.is_solid(x, y, z)}
    guard_trees(t, columns=cols)


# ---------------------------------------------------------------- the two starts

def causeway_a(seed):
    tid = NAME + '/causeway_a'
    sx, sy, sz = SIZES['a']
    t = Template(sx, sy, sz, seed=seed, name=tid)
    rng = t.rng
    L = sz

    def sink(z):
        return max(1.0 - z / 6.0, 1.0 - (L - 1 - z) / 6.0, 0.95 - abs(z - 18) / 3.2, 0.0)
    hi, lo = causeway(t, rng, seed, sink)
    mat = patch(Noise(seed + 10, 3.0), HYMN.options('wall'), jitter=Noise(seed + 11, 0.9), mix=0.35)
    crown = Noise(seed + 12, 2.0)
    # the gate: the west tower standing to a broken crown with its toll room; the east snapped low
    tower(t, rng, TOWER_W, GZ - 2, GZ + 2, lambda x, z: TOWER_TOP - 3 + int(crown.at(x, z) * 4.5), mat,
          door_x=TOWER_W[1], ceiling=SPRING + 1)
    tower(t, rng, TOWER_E, GZ - 2, GZ + 2, lambda x, z: WK + int(crown.at(x + 40, z) * 3.0), mat, slit=False)
    for x in KERBS:
        for z in range(GZ - 2, GZ + 3):
            if t.name_at(x, WK, z) and t.name_at(x, WK, z).endswith('_wall'):
                t.set(x, WK, z, AIR)
    t.chest(TOWER_W[0] + 1, WK, GZ + 1, 'east', loot=LOOT_ID)
    t.set(TOWER_W[0] + 1, WK, GZ - 1, barrel('up'))
    t.set(TOWER_W[0] + 2, WK, GZ - 1, S('lectern', facing='south', has_book=False, powered=False))
    hanging_lantern(t, TOWER_W[0] + 2, SPRING + 1, GZ, 2, soul=True)
    # half the arch: the west springing stands, the rest lies in the passage
    standing = Noise(seed + 13, 1.5)
    for (x, y, z), role in sorted(arch_cells().items()):
        if x <= 8 or (x == 9 and standing.at(x, y, z) > 0.25) or (x == 10 and y >= PARAPET - 1 and standing.at(x, y, z) > 0.6):
            t.set(x, y, z, arch_state(rng, mat, role, x, y, z))
        elif rng.random() < 0.22:
            px = x + rng.choice((-1, 0, 0, 1))
            pz = z + rng.choice((-2, -1, 0, 1, 2))
            if KERBS[0] <= px <= KERBS[1] + 2:
                land(t, px, pz, WK + 1, pick(rng, FALLEN))
    # the east tower lies along the road to the south, broken in two, its crown crumbled at the end
    gap = Noise(seed + 14, 1.7)
    for c in range(0, 11):                                  # tower courses y 4..14 lying at z GZ+3+c
        z = GZ + 3 + c
        shift = 1 if c >= 5 else 0
        if c == 4:
            continue
        for x in range(TOWER_E[0], TOWER_E[1] + 1):
            for y in range(0, 5):
                ring = x in TOWER_E or y in (0, 4)
                xx = min(x + shift, sx - 1)
                if not ring:
                    if y >= WK:
                        t.set(xx, y, z, AIR)
                    continue
                if y >= 3 and gap.at(xx, y, z) > 0.64 - c * 0.02:
                    continue
                if c >= 9 and rng.random() < 0.45:
                    continue
                corner = x in TOWER_E and y in (0, 4)
                st = 'minecraft:polished_basalt[axis=z]' if corner else ('minecraft:polished_deepslate' if c == 3 else mat(xx, y, z))
                t.set(xx, y, z, st)
    for k in range(10):
        land(t, TOWER_E[0] + rng.randint(0, 5), GZ + 13 + rng.randint(0, 3), 4, pick(rng, FALLEN))
    for (x, z) in ((17, GZ + 14), (18, GZ + 15)):
        t.set(x, 0, z, pick(rng, FOOT))
        t.set(x, WL, z, COPPER['grate'])
        t.set(x, WK, z, rod(facing='south'))
    # the doors: one leaf flat in the road before the gate, one leaning on the west tower
    for x in range(7, 11):
        for z in range(GZ - 8, GZ - 3):
            if t.is_solid(x, WL, z) and not t.is_solid(x, WK, z) and rng.random() < 0.92:
                t.set(x, WK, z, COPPER_LEAF % ('north', 'false'))
    for x in range(TOWER_W[0] + 1, TOWER_W[1]):
        t.set(x, 0, GZ - 3, pick(rng, FOOT))
        t.set(x, WL, GZ - 3, pick(rng, SHOULDER))
        for y in range(WK, WK + 4 - (x == TOWER_W[0] + 1)):
            t.set(x, y, GZ - 3, COPPER_LEAF % ('north', 'true'))
    # the road's furniture: song-posts, the song-stone, the cart, the trees
    for (x, z, kind, fallen) in ((6, 9, 'pipe', None), (14, 9, 'lamp', None), (6, 25, 'lamp', None),
                                 (14, 25, 'pipe', None), (6, 38, 'pipe', 'west'), (14, 44, 'lamp', None),
                                 (6, 48, 'pipe', None), (14, 50, 'pipe', 'east')):
        if (x, z) in hi:
            song_post(t, rng, x, z, kind, fallen)
    song_stone(t, rng, 1, 11, 'west')
    cart(t, rng, 9, 41)
    stormwood_on_bank(t, rng, 17, 21, 9, lean=(1, 0))
    stormwood_on_bank(t, rng, 3, 46, 7, crown=False)
    for (x0, z0, n) in ((9, 26, 5), (13, 12, 4), (7, 34, 6)):
        root_veins(t, rng, (x0, WL, z0), n, down=False, state=roots(False))
    finish(t, rng, hi, lo)
    CAMERAS[tid] = ((XC, WK + 1, 11), (9, 8, GZ))
    return t


def causeway_b(seed):
    tid = NAME + '/causeway_b'
    sx, sy, sz = SIZES['b']
    t = Template(sx, sy, sz, seed=seed, name=tid)
    rng = t.rng
    L = sz

    def sink(z):
        return max(1.0 - z / 5.0, 1.0 - (L - 1 - z) / 5.0, 1.6 - abs(z - 15.5) / 2.2, 0.0)
    hi, lo = causeway(t, rng, seed, sink)
    # the breach: the flags gone to the mud, stepping stones across, a fallen trunk laid over it
    for (x, z) in sorted(lo):
        if 13 <= z <= 18 and x in ROAD and rng.random() < 0.6:
            t.set(x, 0, z, core(x, z))
    for (x, z) in ((8, 13), (9, 14), (8, 15), (9, 16), (10, 17), (11, 18), (12, 14), (12, 16)):
        if (x, z) in lo:
            t.set(x, 0, z, pick(rng, FOOT))
            t.set(x, WL, z, pick(rng, FLAGS))
    for z in range(11, 21):
        t.set(11, WK, z, log('minecraft:dark_oak_log', 'z'))
        if not t.is_solid(11, WL, z):
            if z in (11, 12, 19, 20):
                t.set(11, 0, z, pick(rng, FOOT))
                t.set(11, WL, z, pick(rng, FLAGS))
    for z in (13, 15, 17):
        t.set(11, 0, z, pick(rng, FOOT))
        t.set(11, WL, z, roots(False))
    mat = patch(Noise(seed + 10, 3.0), HYMN.options('wall'), jitter=Noise(seed + 11, 0.9), mix=0.35)
    crown = Noise(seed + 12, 2.0)
    tower(t, rng, TOWER_W, GZ - 2, GZ + 2, lambda x, z: WK + 1 + int(crown.at(x, z) * 4.0), mat, slit=False)
    tower(t, rng, TOWER_E, GZ - 2, GZ + 2, lambda x, z: WK + int(crown.at(x + 40, z) * 3.5), mat, door_x=TOWER_E[0],
          slit=False)
    for x in KERBS:
        for z in range(GZ - 9, GZ + 3):
            if t.name_at(x, WK, z) and t.name_at(x, WK, z).endswith('_wall'):
                t.set(x, WK, z, AIR)
    t.chest(TOWER_E[1] - 1, WK, GZ - 1, 'west', loot=LOOT_ID)
    # the arch fell north across the road whole and broke where it lay: rows y -> z GZ-2-(y-SPRING)
    broke = Noise(seed + 13, 1.6)
    for (x, y, z), role in sorted(arch_cells().items()):
        if z == GZ + 1:
            continue
        lz = GZ - 2 - (y - SPRING)
        ly = WK + (z - (GZ - 1))
        if broke.at(x, lz, ly) > 0.74 or (ly > WK and rng.random() < 0.2):
            continue
        if ly > WK and not t.is_solid(x, ly - 1, lz):
            continue
        t.set(x, ly, lz, arch_state(rng, mat, role, x, y, z))
        if ly == WK and not t.is_solid(x, WL, lz):
            t.set(x, 0, lz, pick(rng, FOOT))
            t.set(x, WL, lz, core(x, lz))
    t.set(XC, WK, GZ - 7, cluster('up'))
    for k in range(12):
        x = rng.randint(KERBS[0], KERBS[1])
        z = rng.randint(GZ - 3, GZ + 2)
        land(t, x, z, WK + 2, pick(rng, FALLEN))
    # a stormwood grown out of the west stump, its roots down the stump into the water
    choir_ruin.stormwood(t, rng, 3, GZ, WK, 11, crown=True, lean=(0, 1))
    for (x, z) in ((1, GZ - 1), (5, GZ + 1), (2, GZ + 2), (4, GZ - 2), (1, GZ + 1)):
        y = top_solid(t, x, z, y_from=WK + 5)
        if y is not None:
            root_veins(t, rng, (x, y, z), 5, down=True, state=roots(False))
    # the road's furniture
    for (x, z, kind, fallen) in ((6, 6, 'pipe', None), (14, 6, 'lamp', None), (14, 22, 'pipe', 'east'),
                                 (6, 22, 'lamp', None), (6, 36, 'pipe', None), (14, 40, 'pipe', None)):
        if (x, z) in hi:
            song_post(t, rng, x, z, kind, fallen)
    song_stone(t, rng, 17, 36, 'east')
    stormwood_on_bank(t, rng, 17, 9, 8, crown=False)
    for (x0, z0, n) in ((8, 24, 5), (12, 38, 5)):
        root_veins(t, rng, (x0, WL, z0), n, down=False, state=roots(False))
    finish(t, rng, hi, lo)
    CAMERAS[tid] = ((XC, WK + 1, 6), (XC, 5, 26))
    return t


def build(variant):
    seed = SALT * 10 + VARIANTS.index(variant) + 1
    t = causeway_a(seed) if variant == 'a' else causeway_b(seed)
    eye = CAMERAS[t.name][0]
    assert t.name_at(*eye) == 'minecraft:air', ('camera eye', t.name, eye, t.name_at(*eye))
    chest = (TOWER_W[0] + 1, WK, GZ + 1) if variant == 'a' else (TOWER_E[1] - 1, WK, GZ - 1)
    require_reach(t, (XC, WK, GZ - 4), [('toll chest', chest, 'touch'), ('north end', (XC, WK, 8), 'feet'),
                                        ('south end', (XC, WK, t.size[2] - 9), 'feet')])
    return t


def write_worldgen():
    els = [W.element(NAME + '/causeway_' + v, adapted=dict(support_depth=10, clear_from=SIZES[v][1], clear_inset=0,
                                                           yard_stand=WK, ramp_rings=0)) for v in VARIANTS]
    W.pool(NAME + '/start', els)
    W.structure(NAME, NAME + '/start', 'has_structure/' + NAME, size=1, max_distance=40, start_height=1 - BURIED)
    W.structure_set(NAME, [(NAME, 1)], 36, 12, SALT, placement='clear_of_origin', min_distance=256)
    W.biome_tag('has_structure/' + NAME, ['oldrepublic:tyrvann_mire'])
    loot_table('chests/tyrvann/mire_causeway', [
        ((2, 4), [('oldrepublic:credit_chit', 5, 1, 4), ('paper', 3, 1, 3), ('iron_nugget', 3, 2, 6),
                  ('copper_ingot', 2, 1, 3), ('oldrepublic:stormglass_shard', 2, 1, 2), ('black_candle', 2, 1, 3),
                  ('string', 2, 1, 3)]),
        (1, [('oldrepublic:credit_chit', 3, 3, 6), ('compass', 1, 1, 1), ('name_tag', 1, 1, 1),
             ('gold_ingot', 1, 1, 2), ('oldrepublic:lumen_thread', 2, 1, 2)]),
    ])


def main(opts):
    ts = {}
    for v in VARIANTS:
        tid = NAME + '/causeway_' + v
        t = build(v)
        ts[tid] = t
        if wanted(opts, tid):
            save(t, tid, opts, views=('se', 'nw'), plans=[0, WL, WK])
    write_worldgen()
    res = jigsaw.assemble(NAME + '/start', max_distance=40, size=1, start_height=1 - BURIED)
    if res.problems:
        print(res.report())
        raise SystemExit('jigsaw problems in %s' % NAME)
    return ts


if __name__ == '__main__':
    main(args())
