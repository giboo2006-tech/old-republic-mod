"""Shared helpers of the QS2 runtime sites: the echo shrines and the ship sites (docs/MILESTONE_6.md 5.3).

Every script in this folder builds its templates with structlib and writes the template, its
site_anchors sidecar and its site meta. Everything is deterministic: randomness comes from the
template's seeded t.rng or from Noise(seed), so a rebuild writes byte-identical files.

Conventions used by every builder here:
  * x east, y up, z south; the entrance is the north side (z = 0).
  * `buried` B: layer B - 1 is the ground the player walks on, layer B is the feet layer.
  * Natural ground the template does not need to change is left unset, so the biome's own top
    block shows; only worn ground, paving and the buried shell are written.
  * Previews are only rendered when a script is run with --preview DIR (the lead's rebuild does not).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(HERE, '..', '..'))

from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta  # noqa: E402,F401
from structlib import blocks as B  # noqa: E402
from structlib.nature import Noise  # noqa: E402
from structlib.palettes import Palette  # noqa: E402,F401

# Toolkit workaround (reported): structlib/mod.py lists lumen moss at light 6 and the kyber cluster at 7,
# but PuzzleBlocks.LUMEN_MOSS gives 4 and WorldBlocks.KYBER_CLUSTER gives 6. light_fill must use the
# game's values or it leaves floors darker than it thinks.
mod.LIGHT_SOURCES['oldrepublic:lumen_moss'] = 4
mod.LIGHT_SOURCES['oldrepublic:kyber_cluster'] = 6
mod.LIGHT_SOURCES['oldrepublic:crystal_veined_stone'] = 2


# ---------------------------------------------------------------- block states

def stairs(name, facing, half='bottom'):
    return S(name, facing=facing, half=half, shape='straight', waterlogged=False)


def slab(name, kind='bottom'):
    return S(name, type=kind, waterlogged=False)


def wall(name):
    return S(name, north='none', east='none', south='none', west='none', up=True, waterlogged=False)


def fence(name):
    return S(name, north=False, east=False, south=False, west=False, waterlogged=False)


def bars(name='iron_bars'):
    return S(name, north=False, east=False, south=False, west=False, waterlogged=False)


def log(name, axis='y'):
    return S(name, axis=axis)


def trapdoor(name, facing, half='bottom', open_=False):
    return S(name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def lantern(hanging=False, soul=False):
    return S('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def chain(axis='y'):
    return S('iron_chain', axis=axis, waterlogged=False)


def candle(color=None, n=1, lit=True):
    return S('%s_candle' % color if color else 'candle', candles=n, lit=lit, waterlogged=False)


def campfire(lit=True, facing='north', soul=False, signal=False):
    return S('soul_campfire' if soul else 'campfire', facing=facing, lit=lit, signal_fire=signal, waterlogged=False)


def kyber(facing='up'):
    return S('or:kyber_cluster', facing=facing)


def vine(**faces):
    props = {'north': False, 'east': False, 'south': False, 'west': False, 'up': False}
    props.update(faces)
    return S('vine', **props)


def lichen(**faces):
    props = {'north': False, 'east': False, 'south': False, 'west': False, 'up': False, 'down': False,
             'waterlogged': False}
    props.update(faces)
    return S('glow_lichen', **props)


def skull(rotation=0, wither=False):
    return S('wither_skeleton_skull' if wither else 'skeleton_skull', powered=False, rotation=rotation)


def wall_skull(facing, wither=False):
    return S('wither_skeleton_wall_skull' if wither else 'skeleton_wall_skull', facing=facing, powered=False)


def light(level=15):
    return S('light', level=level, waterlogged=False)


def pot(facing='north', cracked=False):
    return S('decorated_pot', cracked=cracked, facing=facing, waterlogged=False)


def hay(axis='y'):
    return S('hay_block', axis=axis)


def bed(color, facing, part):
    """One half of a bed; facing points from the foot to the head."""
    return S('%s_bed' % color, facing=facing, occupied=False, part=part)


def place_bed(t, x, y, z, color, facing):
    """A bed with its foot at (x, y, z) and its head one step toward `facing`."""
    dx, dz = B.step(facing)
    t.set(x, y, z, bed(color, facing, 'foot'))
    t.set(x + dx, y, z + dz, bed(color, facing, 'head'))


# ---------------------------------------------------------------- the counterweight circuit

# The echo shrines' puzzle, cell for cell as the M3 shrine built it (gen_structures_m3.py build_shrine,
# checked against the old templates before they were replaced). Offsets are from the counterweight C.
# C faces north into the room; sunk, it powers its back cell W1; the wire runs W1 -> W2, and W2 powers
# the junction block J under it, which wakes the pedestal P below it and the upper seal S2 north of it
# (the seal group S1 + S2 opens together). Every other neighbour of the wiring is plain solid stone.
MECH_WIRE_1 = 'minecraft:redstone_wire[east=side,north=side,power=0,south=none,west=none]'
MECH_WIRE_2 = 'minecraft:redstone_wire[east=side,north=none,power=0,south=none,west=side]'
MECH_OFFSETS = {
    'C': (0, 0, 0), 'W1': (0, 0, 1), 'W2': (1, 0, 1), 'S1': (1, -2, 0), 'S2': (1, -1, 0),
    'P': (1, -2, 1), 'J': (1, -1, 1), 'SUPPORT': (0, -1, 1),
}
# Cells that must be open in front of the mechanism (the room side).
MECH_FRONT = [(0, 0, -1), (1, -2, -1), (1, -1, -1)]
# Blocks that react to redstone or give it off: never next to the circuit.
REDSTONE_WORDS = ('redstone', 'repeater', 'comparator', 'piston', 'observer', 'lamp', 'bulb', '_door', 'trapdoor',
                  'fence_gate', 'note_block', 'dispenser', 'dropper', 'hopper', 'tnt', 'rail', 'lever', 'button',
                  'pressure_plate', 'tripwire', 'daylight', 'target', 'bell', 'sculk_sensor', 'crafter', 'lectern',
                  'puzzle_seal', 'holocron_pedestal', 'counterweight', 'step_stone', 'veiled_stone', 'trapped_chest')


def mech_cells(c):
    cx, cy, cz = c
    return {k: (cx + dx, cy + dy, cz + dz) for k, (dx, dy, dz) in MECH_OFFSETS.items()}


def place_mechanism(t, c, kind, skin, stone, junction):
    """The counterweight C at `c` (facing north), its wiring, the two seals and the pedestal of `kind`.
    `stone` (a state or callable) fills the solid neighbourhood around the wiring; `junction` is the
    block over the pedestal. Anchors COUNTERWEIGHT and PEDESTAL."""
    m = mech_cells(c)
    cx, cy, cz = c
    # the inert block of stone the wiring lives in: x - 1 .. x + 2, y - 2 .. y + 1, behind the wall plane
    for x in range(cx - 1, cx + 3):
        for y in range(cy - 2, cy + 2):
            for z in range(cz + 1, cz + 3):
                t.set(x, y, z, stone(x, y, z) if callable(stone) else stone)
    t.set(*m['C'], S('or:counterweight_stone', age=0, facing='north', skin=skin, sunk=False))
    t.set(*m['W1'], MECH_WIRE_1)
    t.set(*m['W2'], MECH_WIRE_2)
    for k in ('S1', 'S2'):
        t.set(*m[k], S('or:puzzle_seal', age=0, open=False, skin=skin))
    mod.pedestal(t, *m['P'], kind, anchor='PEDESTAL')
    t.set(*m['J'], junction)
    t.anchor('COUNTERWEIGHT', *m['C'])
    return m


def verify_mechanism(t, c, kind, skin):
    """Raise when the circuit is not exactly the M3 one, when a front cell is not open, or when anything
    that takes or gives redstone touches the wiring (other than the circuit's own cells)."""
    m = mech_cells(c)
    want = {
        'C': S('or:counterweight_stone', age=0, facing='north', skin=skin, sunk=False),
        'W1': MECH_WIRE_1, 'W2': MECH_WIRE_2,
        'S1': S('or:puzzle_seal', age=0, open=False, skin=skin),
        'S2': S('or:puzzle_seal', age=0, open=False, skin=skin),
        'P': S('or:holocron_pedestal', always_ready=False, kind=kind, ready=False),
    }
    for k, state in want.items():
        got = t.get(*m[k])
        if got != B.build(*B.parse(state)):
            raise AssertionError('mechanism %s at %r is %r, expected %r' % (k, m[k], got, state))
    if t.nbt.get(m['P'], {}).get('id') != mod.PEDESTAL:
        raise AssertionError('the pedestal lost its block entity data')
    for k in ('J', 'SUPPORT'):
        got = t.get(*m[k])
        if got is None or not t.is_solid(*m[k]) or not nature._full_block(B.name_of(got)) \
                or any(w in B.name_of(got) for w in REDSTONE_WORDS):
            raise AssertionError('mechanism %s at %r must be a plain full block, is %r' % (k, m[k], got))
    cx, cy, cz = c
    for (dx, dy, dz) in MECH_FRONT:
        p = (cx + dx, cy + dy, cz + dz)
        if not t.is_air(*p) or p not in t.cells:
            raise AssertionError('the cell %r in front of the mechanism must be explicit air' % (p,))
    own = set(m.values())
    powered = [m['W1'], m['W2'], m['J'], m['SUPPORT'], (m['W2'][0] + 1, m['W2'][1], m['W2'][2])]
    for p in powered:
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if q in own:
                continue
            s = t.get(*q)
            n = B.name_of(s) if s else ''
            if any(w in n for w in REDSTONE_WORDS):
                raise AssertionError('%s at %r touches the wiring cell %r' % (n, q, p))
    east = (m['W2'][0] + 1, m['W2'][1], m['W2'][2])
    if not t.is_solid(*east) or not nature._full_block(B.name_of(t.get(*east))):
        raise AssertionError('the cell east of W2 %r must be a plain full block' % (east,))
    return True


# ---------------------------------------------------------------- growth that stays inside the build

def open_cell(t, p, ground_feet):
    """A cell growth may fill: explicit air the template sets, or an unset cell at or above the surface
    feet layer (unset cells below it are the world's ground, which growth must never replace)."""
    if not t.inside(*p):
        return False
    s = t.cells.get(p)
    if s is None:
        return p[1] >= ground_feet
    return B.name_of(s) in ('minecraft:air', 'minecraft:cave_air')


def grow_vines(t, region, chance, ground_feet, max_len=3, walls=None, rng=None, allow=None):
    """Vines down the faces of full blocks in region, only into open cells (open_cell). walls: block
    names that take vines (None: any full block). allow: a test a vine cell must also pass."""
    rng = rng or t.rng
    (x0, y0, z0), (x1, y1, z1) = region
    names = {B.full_name(n) for n in walls} if walls else None
    faces = (('north', 0, -1), ('south', 0, 1), ('east', 1, 0), ('west', -1, 0))
    for p in sorted(t.cells, key=lambda q: (-q[1], q[2], q[0])):
        x, y, z = p
        if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
            continue
        s = t.cells[p]
        n = B.name_of(s)
        if not t.is_solid(*p) or not nature._full_block(n) or (names is not None and n not in names):
            continue
        for d, dx, dz in faces:
            q = (x + dx, y, z + dz)
            if not open_cell(t, q, ground_feet) or rng.random() >= chance / 4:
                continue
            face = B.opposite(d)
            for k in range(rng.randint(1, max_len)):
                qq = (q[0], q[1] - k, q[2])
                if not open_cell(t, qq, ground_feet) or not t.is_solid(x, y - k, z) or (allow and not allow(qq)):
                    break
                t.set(*qq, vine(**{face: True}))


# ---------------------------------------------------------------- lighting and checks

def dark_floors(t, region=None, level=8):
    """Floor cells (mod.floor_cells) in region whose block light from the template is under `level`."""
    lit = mod.light_map(t)
    return [p for p in mod.floor_cells(t, region) if lit.get(p, 0) < level]


def check_anchor_spot(t, name):
    """Raise when a feet anchor is not explicit air over a solid block with air above."""
    for (x, y, z) in t.anchors.get(name, []):
        if not (t.is_air(x, y, z) and (x, y, z) in t.cells and t.is_air(x, y + 1, z)
                and (x, y + 1, z) in t.cells and t.is_solid(x, y - 1, z)):
            raise AssertionError('anchor %s at %r is not a clear feet cell' % (name, (x, y, z)))


# ---------------------------------------------------------------- saving and previews

def save(t, template_id, preview_dir=None, views=('se', 'nw'), cut=None, plans='auto', ground=None, extra=None):
    """Writes the template (and its sidecar). With preview_dir, renders a sheet there (and the
    `extra` sheets: a list of (suffix, kwargs) for preview.sheet)."""
    t.save(template_id)
    out = []
    if preview_dir:
        from structlib import preview as P
        os.makedirs(preview_dir, exist_ok=True)
        data = t.to_nbt()
        name = template_id.replace('/', '_')
        sheets = [('', dict(views=views, cut=cut, plans=plans, ground=ground))] + list(extra or [])
        for suffix, kw in sheets:
            path = os.path.join(preview_dir, name + suffix + '.png')
            P.sheet(data, title=template_id + suffix, **kw).convert('RGB').save(path)
            out.append(path)
    return out


def scene(t, ground, margin=6, top='grass_block', fill='dirt', base='stone'):
    """For previews only: the template set into flat terrain (ground layer `ground`, `margin` blocks
    of it all round), the way the runtime placer stamps it. Unset template cells show the terrain,
    explicit air carves it."""
    sx, sy, sz = t.size
    s = Template(sx + 2 * margin, sy, sz + 2 * margin)
    for x in range(sx + 2 * margin):
        for z in range(sz + 2 * margin):
            for y in range(0, ground + 1):
                s.cells[(x, y, z)] = B.full_name(top if y == ground else fill if y >= ground - 2 else base)
    for (x, y, z), st in t.cells.items():
        s.cells[(x + margin, y, z + margin)] = st
    return s


def scene_sheets(t, preview_dir, name, ground, margin=6, cuts=(), views=('se', 'nw'), top='grass_block'):
    """Renders the template in its terrain (scene()) from two corners, plus cutaways at `cuts`."""
    from structlib import preview as P
    os.makedirs(preview_dir, exist_ok=True)
    data = scene(t, ground, margin, top=top).to_nbt()
    path = os.path.join(preview_dir, name + '_scene.png')
    P.sheet(data, title=name + ' in terrain', views=views, plans=[], cut=list(cuts) or None).convert('RGB').save(path)
    return path


def preview_arg(argv):
    """The --preview DIR argument of a script (None when absent), and the remaining arguments."""
    rest, out = [], None
    it = iter(argv)
    for a in it:
        if a == '--preview':
            out = next(it, None)
        else:
            rest.append(a)
    return out, rest
