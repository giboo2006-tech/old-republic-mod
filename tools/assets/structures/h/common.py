"""Shared helpers for worker H's Chapter Four hubs: the Greenwatch, the Proving and Zarvos's Sepulchre
(docs/MILESTONE_6.md 5.7).

Every script in this folder imports this module:

    from common import *          # Template, S, AIR, shapes, nature, mod, meta, B, Noise, Palette, helpers

Previews are rendered only when the environment variable H_PREVIEW names a folder (never the
repository), so `build_all.py` run by the lead writes nothing but the data files.

Everything random goes through a template's seeded ``t.rng`` or a ``Noise(seed)``: two runs write
byte-identical files.

Toolkit notes:
- The build kit's multipart walls (watch_stone_wall, tomb_brick_wall, ash_brick_wall...) once named only
  low/tall and up=true in their blockstates, so tools/check_m6_structures.py flagged a joined wall (none /
  up=false). The blockstates now carry a "gap" part for those values; USE_KIT_WALLS turns the kit walls on
  (set it False to fall back to vanilla walls if that part is ever dropped).
- Gravity blocks (sand, gravel, concrete powder) are kept out of the ground layer: on the raised-platform
  fallback the layer below a hub is air and they would fall.

The ground skirt (skirt() below) steps the hub's outer rings down below the town and leaves the cells above
each step unset, so the land (or the placer's fill) decides how much of it shows. RAMP_RINGS is what the
hubs' site meta asks of the placer's own ramp; see its comment.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402,F401
from structlib.nature import Noise  # noqa: E402,F401
from structlib.palettes import Palette  # noqa: E402,F401

ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
PREVIEW = os.environ.get('H_PREVIEW')

# The kit's multipart walls (see the module doc).
USE_KIT_WALLS = True

# ramp_rings in the hubs' site meta. The placer's ramp (CampPoolElement.ramp, run by SiteFit.after) carves the
# outer rings down toward the ground outside, replacing what stands there with the biome's top block, and runs
# before SiteFit's apron raises the ground outside the box to a one-to-one slope from the stand: with more
# than one ring the carved edge ends below the apron (a ditch) and the skirt's gate stairs are carved away.
# One ring keeps the edge flush with the apron. Raise it to the skirt's depth (4) once the ramp keeps built
# blocks and the apron starts from the ramped edge (reported to the lead).
RAMP_RINGS = 1

GROUND = 2   # the ground layer of both hubs (buried 3: the feet layer is y 3)
FEET = 3


# ---------------------------------------------------------------- block ids

def _known_blocks():
    names = set()
    with open(os.path.join(ROOT, 'tools', 'vanilla_blocks_26_2.txt')) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                names.add('minecraft:' + line)
    bs = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'oldrepublic', 'blockstates')
    for fn in os.listdir(bs):
        if fn.endswith('.json'):
            names.add('oldrepublic:' + fn[:-5])
    return names


KNOWN = _known_blocks()


def check_blocks(t):
    """Fails loudly on a block id that does not exist in 26.2 or the mod (a typo in a generator)."""
    bad = sorted({B.name_of(s) for s in t.cells.values()} - KNOWN)
    if bad:
        raise ValueError('%s uses unknown blocks: %s' % (t.name, ', '.join(bad)))


GRAVITY = ('minecraft:sand', 'minecraft:red_sand', 'minecraft:gravel', 'minecraft:suspicious_sand',
           'minecraft:suspicious_gravel')


def support_gravity(t, support='minecraft:dirt'):
    """Gravity blocks over an unset or air cell get a support block below (or turn to `support` at y 0)."""
    for (x, y, z), s in sorted(t.cells.items()):
        n = B.name_of(s)
        if n in GRAVITY or n.endswith('_concrete_powder'):
            if y == 0:
                t.set(x, y, z, support)
            elif t.is_air(x, y - 1, z):
                t.set(x, y - 1, z, support)


# ---------------------------------------------------------------- states

def stairs(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def lantern(hanging=False, soul=False):
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def chain(axis='y'):
    return S('iron_chain', axis=axis, waterlogged=False)


def leaves(name='oak_leaves'):
    return S(name, distance=1, persistent=True, waterlogged=False)


def wall_torch(facing, kind='wall_torch'):
    return S(kind, facing=facing)


def campfire(lit=True, facing='north', soul=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=False, waterlogged=False)


def barrel(facing='up'):
    return S('barrel', facing=facing, open=False)


def candle(n=1, colour=None, lit=True):
    return S(('%s_candle' % colour) if colour else 'candle', candles=n, lit=lit, waterlogged=False)


def trapdoor(name, facing, half='top', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def ladder(facing):
    return S('ladder', facing=facing, waterlogged=False)


def pane(name='glass_pane'):
    return S(name, waterlogged=False)


def fence(name):
    return S(name, waterlogged=False)


def wall_block(name):
    """A wall post (Template.finish joins it to its neighbours)."""
    return S(name, up=True, waterlogged=False)


def grass(name='grass_block'):
    return S(name, snowy=False)


# ---------------------------------------------------------------- pieces of furniture and dressing

def bed(t, x, y, z, facing, colour='white'):
    """A bed whose foot is (x, y, z) and whose head lies one cell toward `facing`."""
    dx, dz = B.step(facing)
    t.set(x, y, z, S('%s_bed' % colour, facing=facing, occupied=False, part='foot'))
    t.set(x + dx, y, z + dz, S('%s_bed' % colour, facing=facing, occupied=False, part='head'))


def door(t, x, y, z, facing, wood='spruce', hinge='left', open_=False):
    """A door standing in (x, y, z) and the cell above; facing is the side it opens toward."""
    for half, yy in (('lower', y), ('upper', y + 1)):
        t.set(x, yy, z, S('%s_door' % wood, facing=facing, half=half, hinge=hinge, open=open_, powered=False))


def hang_lantern(t, x, y, z, up_to, soul=False):
    """A lantern hanging at (x, y, z) on a chain that climbs to the cell below up_to (a ceiling at up_to)."""
    t.set(x, y, z, lantern(True, soul))
    for yy in range(y + 1, up_to):
        t.set(x, yy, z, chain())


def lamp_post(t, x, y, z, height=3, post='spruce_fence', soul=False, base=None):
    """A fence post standing on (x, y - 1, z) with a lantern on top; y is the feet layer."""
    if base is not None:
        t.set(x, y, z, base)
        y += 1
        height -= 1
    for yy in range(y, y + height):
        t.set(x, yy, z, fence(post))
    t.set(x, y + height, z, lantern(False, soul))


def bracket_lamp(t, x, y, z, out, arm='spruce_fence', soul=False):
    """A lantern hanging from a short arm sticking out of a wall: the arm is at (x, y, z) + out, lantern below."""
    dx, dz = B.step(out)
    t.set(x + dx, y, z + dz, fence(arm))
    t.set(x + dx, y - 1, z + dz, lantern(True, soul))


def table(t, x, y, z, top='spruce_slab', leg=None, plate=None):
    """A table: a top slab at seat height, or a fence leg with an optional pressure plate on it."""
    if leg:
        t.set(x, y, z, fence(leg))
        if plate:
            t.set(x, y + 1, z, S(plate, powered=False))
    else:
        t.set(x, y, z, slab(top, 'top'))


def chair(t, x, y, z, facing, wood='spruce'):
    """A seat: a stair whose back is toward `facing` (one sits looking the other way)."""
    t.set(x, y, z, stairs('%s_stairs' % wood, facing))


def crate_stack(t, x, y, z, rng, kinds, height=None):
    h = height if height is not None else rng.choice((1, 1, 2, 2, 3))
    for k in range(h):
        t.set(x, y + k, z, rng.choice(kinds))


def potted(rng, kinds=('potted_fern', 'potted_red_mushroom', 'potted_lily_of_the_valley', 'potted_azure_bluet',
                       'potted_oxeye_daisy', 'potted_spruce_sapling', 'potted_birch_sapling')):
    return 'minecraft:' + rng.choice(kinds)


def banner(t, x, y, z, facing, base, patterns=(), wall=True):
    mod.banner(t, x, y, z, facing, base=base, patterns=patterns, wall=wall)


# ---------------------------------------------------------------- building shells

def opening(t, x0, y0, z0, x1, y1, z1):
    """Cut a doorway or window: explicit air (it clears the world there too)."""
    t.fill(x0, y0, z0, x1, y1, z1, AIR)


def gable_roof(t, x0, z0, x1, z1, y, stair, ridge='x', overhang=1, gable=None, body=None, attic=True, slab_cap=None):
    """A gable roof over the rectangle with eaves at y (shapes.gable_roof), and explicit air in the attic so the
    room under it is clear of the world."""
    shapes.gable_roof(t, x0, z0, x1, z1, y, stair, body=body, slab=slab_cap, ridge=ridge, overhang=overhang,
                      gable=gable)
    if not attic:
        return
    xa, xb = sorted((x0, x1))
    za, zb = sorted((z0, z1))
    if ridge == 'x':
        w = (zb + overhang) - (za - overhang) + 1
        for i in range((w + 1) // 2):
            front, back = za - overhang + i + 1, zb + overhang - i - 1
            if front > back:
                break
            for zz in range(max(front, za + 1), min(back, zb - 1) + 1):
                for xx in range(xa + 1, xb):
                    if (xx, y + i, zz) not in t.cells:
                        t.set(xx, y + i, zz, AIR)
    else:
        w = (xb + overhang) - (xa - overhang) + 1
        for i in range((w + 1) // 2):
            front, back = xa - overhang + i + 1, xb + overhang - i - 1
            if front > back:
                break
            for xx in range(max(front, xa + 1), min(back, xb - 1) + 1):
                for zz in range(za + 1, zb):
                    if (xx, y + i, zz) not in t.cells:
                        t.set(xx, y + i, zz, AIR)


def stilt_columns(n, spacing=8):
    """The local x (or z) of PlanetHubs' raised-platform stilts: every `spacing` from 0, and the far edge."""
    return sorted(set(list(range(0, n, spacing)) + [n - 1]))


def footings(t, state, top=GROUND - 1, top_of=None):
    """Fill y 0..top under the stilt grid, so stilts under a raised platform meet the ground layer.
    top_of(x, z) gives a column's own top instead (under a skirt step)."""
    for x in stilt_columns(t.sx):
        for z in stilt_columns(t.sz):
            for y in range(0, (top_of(x, z) if top_of else top) + 1):
                if (x, y, z) not in t.cells:
                    t.set(x, y, z, state)


# ---------------------------------------------------------------- the ground skirt

def ring_distance(t, inset, x, z):
    """How many rings outside the town's wall rectangle (its outer face `inset` cells in from every edge) a
    column lies: 0 inside the rectangle, 1 just outside its face, `inset` at the template's edge."""
    dx = max(inset - x, 0, x - (t.sx - 1 - inset))
    dz = max(inset - z, 0, z - (t.sz - 1 - inset))
    return max(dx, dz)


LOOSE = ('minecraft:air', 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:dirt', 'minecraft:coarse_dirt',
         'minecraft:moss_block', 'minecraft:rooted_dirt', 'minecraft:dirt_path', 'minecraft:packed_mud',
         'minecraft:mud', 'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern',
         'minecraft:moss_carpet', 'minecraft:bush', 'minecraft:leaf_litter', 'minecraft:brown_mushroom',
         'minecraft:red_mushroom', 'minecraft:lily_of_the_valley', 'minecraft:azure_bluet', 'minecraft:oxeye_daisy',
         'minecraft:dead_bush', 'minecraft:short_dry_grass', 'minecraft:tall_dry_grass', 'oldrepublic:silverleaf',
         'oldrepublic:ashroot', 'oldrepublic:debris', 'oldrepublic:scorch_mark')


def skirt(t, inset, ground_y, top, face, gates=(), stair=None, parapet=None, stair_base=None, loose=LOOSE):
    """A stepped ground skirt in the outer `inset` rings of the template, below the town's ground layer.

    Ring d (1 next to the wall, `inset` at the edge) has its top block at ground_y - d, so the rings step
    down one block each away from the town; the outermost ring is a retaining face from y 0 up to its top.
    Nothing is set above a step or inside the bank: on level land the world's own ground buries the steps
    (and the template cuts nothing), where the land falls away the steps and the face show, and the placer's
    support fills below. top(x, y, z, d) and face(x, y, z) give the blocks.

    The town's ground, plants and air in the rings go (`loose` names, at the ground layer and the cell above);
    standing work there (a gate pier) stays and is founded down to its step.

    gates: (side, a, b) spans of the town's gates (x for north/south, z for west/east, template coordinates):
    each gets a flight of stairs from its threshold down through the rings, flanked by parapets."""
    sx, sz = t.sx, t.sz
    loose = {B.full_name(n) for n in loose}
    gate_cells = {}
    for side, a, b in gates:
        for d in range(1, inset + 1):
            for v in range(a - 1, b + 2):
                if side == 'north':
                    p = (v, inset - d)
                elif side == 'south':
                    p = (v, sz - 1 - inset + d)
                elif side == 'west':
                    p = (inset - d, v)
                else:
                    p = (sx - 1 - inset + d, v)
                gate_cells[p] = (side, d, 'parapet' if v in (a - 1, b + 1) else 'stair')
    for x in range(sx):
        for z in range(sz):
            d = ring_distance(t, inset, x, z)
            if d == 0:
                continue
            ty = ground_y - d
            lowest = None
            for y in range(ty + 1, t.sy):
                s = t.get(x, y, z)
                if s is None:
                    continue
                if (y <= ground_y + 1 and B.name_of(s) in loose) or B.name_of(s) == 'minecraft:air':
                    t.set(x, y, z, None)
                elif lowest is None:
                    lowest = y
            if lowest is not None and lowest <= ground_y:
                for y in range(ty + 1, lowest):
                    t.set(x, y, z, face(x, y, z))
            if d == inset:
                for y in range(0, ty):
                    t.set(x, y, z, face(x, y, z))
            g = gate_cells.get((x, z))
            if g is not None and stair is not None and lowest is None:
                side, gd, part = g
                inward = {'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}[side]
                t.set(x, ty, z, stair_base(x, ty, z) if stair_base else face(x, ty, z))
                if part == 'stair':
                    t.set(x, ty + 1, z, stairs(stair, inward))
                else:
                    t.set(x, ty + 1, z, face(x, ty + 1, z))
                    t.set(x, ty + 2, z, wall_block(parapet) if parapet else face(x, ty + 2, z))
            else:
                t.set(x, ty, z, top(x, ty, z, d))


# ---------------------------------------------------------------- nature

def silver_tree(t, x, y, z, height, rng, trunk='birch_log', leaf='oak_leaves', wide=False, crown=3.6):
    """A Selvaren highwood "silver tree": a birch trunk (2x2 when wide), a few short branches and loose
    oak-leaf clouds. y is the first trunk cell."""
    cells = [(x, z)] + ([(x + 1, z), (x, z + 1), (x + 1, z + 1)] if wide else [])
    for (cx, cz) in cells:
        for yy in range(y, y + height):
            t.set(cx, yy, cz, log(trunk))
    mx, mz = (x + 0.5, z + 0.5) if wide else (x, z)
    top = y + height
    blobs = [(mx, top, mz, crown)]
    for k in range(rng.randint(3, 5)):
        ang = rng.uniform(0, math.tau)
        by = y + int(height * rng.uniform(0.5, 0.85))
        ln = rng.randint(2, 4 if wide else 3)
        ex, ez = mx + math.cos(ang) * ln, mz + math.sin(ang) * ln
        shapes.line(t, (int(round(mx + math.cos(ang))), by, int(round(mz + math.sin(ang)))),
                    (int(round(ex)), by + 1, int(round(ez))), log(trunk, 'x' if abs(math.cos(ang)) > 0.7 else 'z'))
        blobs.append((ex, by + 2, ez, crown * rng.uniform(0.55, 0.8)))
    noise = Noise(rng.randint(0, 1 << 30), 2.2)
    for (bx, by, bz, r) in blobs:
        for yy in range(int(by - r * 0.6), int(by + r * 0.8) + 1):
            for xx in range(int(bx - r - 1), int(bx + r + 2)):
                for zz in range(int(bz - r - 1), int(bz + r + 2)):
                    d = ((xx - bx) / r) ** 2 + ((yy - by) / (r * 0.75)) ** 2 + ((zz - bz) / r) ** 2
                    if d <= 1.0 - 0.35 * noise.at(xx, yy, zz) and t.is_air(xx, yy, zz) and t.inside(xx, yy, zz):
                        t.set(xx, yy, zz, leaves(leaf))


def sprinkle(t, region, choices, density, on, rng=None, max_y=None):
    """nature.scatter with block names given short."""
    nature.scatter(t, region, choices, density, rng=rng or t.rng, on=on, max_y=max_y)


def tall_plant(t, x, y, z, name):
    t.set(x, y, z, S(name, half='lower'))
    t.set(x, y + 1, z, S(name, half='upper'))


# ---------------------------------------------------------------- output

def save(t, template_id, preview_name=None, cut=None, plans='auto', views=('se', 'nw')):
    check_blocks(t)
    path = t.save(template_id)
    if PREVIEW and preview_name:
        from structlib import preview as P
        img = P.sheet(t.to_nbt(), title=template_id, views=views, plans=plans, cut=cut)
        os.makedirs(PREVIEW, exist_ok=True)
        img.convert('RGB').save(os.path.join(PREVIEW, preview_name + '.png'))
    return path


def report(t):
    n = t.count()
    print('%s: %dx%dx%d, %d non-air cells, %d anchors' % (t.name, t.sx, t.sy, t.sz, n,
                                                          sum(len(v) for v in t.anchors.values())))
    return n
