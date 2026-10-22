"""Block states as canonical strings, and their rotation and mirroring.

A state is written the way commands write it: ``minecraft:oak_stairs[facing=north,half=bottom]``.
``S('oak_stairs', facing='north')`` builds one; a bare name means ``minecraft:``, ``or:`` means
``oldrepublic:``. Properties are kept sorted so equal states are equal strings (one palette entry).

Rotation follows Minecraft's ``Rotation.CLOCKWISE_90`` seen from above: north -> east -> south -> west.
"""

DIRS = ['north', 'east', 'south', 'west']
_CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}
_MIRROR_X = {'east': 'west', 'west': 'east'}   # Mirror.FRONT_BACK flips x
_MIRROR_Z = {'north': 'south', 'south': 'north'}  # Mirror.LEFT_RIGHT flips z

NS = {'or': 'oldrepublic', 'mc': 'minecraft'}


def full_name(name):
    if ':' not in name:
        return 'minecraft:' + name
    ns, _, path = name.partition(':')
    return NS.get(ns, ns) + ':' + path


def S(name, **props):
    """A canonical state string. Values may be str, int or bool."""
    name = full_name(name)
    if not props:
        return name
    parts = []
    for k in sorted(props):
        v = props[k]
        if isinstance(v, bool):
            v = 'true' if v else 'false'
        parts.append('%s=%s' % (k, v))
    return '%s[%s]' % (name, ','.join(parts))


def parse(state):
    """Split a state string into (name, props dict)."""
    if state is None:
        return None, {}
    if '[' not in state:
        return full_name(state), {}
    name, rest = state.split('[', 1)
    props = {}
    for kv in rest.rstrip(']').split(','):
        if kv:
            k, v = kv.split('=', 1)
            props[k.strip()] = v.strip()
    return full_name(name), props


def build(name, props):
    return S(name, **props) if props else full_name(name)


def name_of(state):
    return parse(state)[0]


def with_props(state, **props):
    """The same block with some properties replaced."""
    n, p = parse(state)
    p.update({k: ('true' if v is True else 'false' if v is False else str(v)) for k, v in props.items()})
    return build(n, p)


def _rot_dir(d, quarter):
    for _ in range(quarter % 4):
        d = _CW.get(d, d)
    return d


def rotate(state, quarter):
    """Rotate a state clockwise by quarter * 90 degrees (seen from above)."""
    quarter %= 4
    if quarter == 0 or state is None:
        return state
    n, p = parse(state)
    if not p:
        return state
    out = dict(p)
    if 'facing' in p:
        out['facing'] = _rot_dir(p['facing'], quarter)
    if 'horizontal_facing' in p:
        out['horizontal_facing'] = _rot_dir(p['horizontal_facing'], quarter)
    if 'axis' in p and quarter % 2 == 1 and p['axis'] in ('x', 'z'):
        out['axis'] = 'z' if p['axis'] == 'x' else 'x'
    if 'rotation' in p:  # banners, signs, skulls: 0..15, 4 steps per quarter
        out['rotation'] = str((int(p['rotation']) + 4 * quarter) % 16)
    if 'orientation' in p:  # jigsaw: e.g. north_up, up_east
        a, _, b = p['orientation'].partition('_')
        out['orientation'] = _rot_dir(a, quarter) + '_' + _rot_dir(b, quarter)
    sides = [d for d in DIRS if d in p]
    if sides:
        for d in DIRS:
            if d in p:
                out[_rot_dir(d, quarter)] = p[d]
    if 'shape' in p and n.endswith('rail'):
        out['shape'] = _rot_rail(p['shape'], quarter)
    return build(n, out)


_RAIL_CW = {'north_south': 'east_west', 'east_west': 'north_south', 'ascending_north': 'ascending_east',
            'ascending_east': 'ascending_south', 'ascending_south': 'ascending_west',
            'ascending_west': 'ascending_north', 'north_east': 'south_east', 'south_east': 'south_west',
            'south_west': 'north_west', 'north_west': 'north_east'}


def _rot_rail(shape, quarter):
    for _ in range(quarter % 4):
        shape = _RAIL_CW.get(shape, shape)
    return shape


def mirror_x(state):
    """Mirror across the x axis (east <-> west), as Mirror.FRONT_BACK."""
    return _mirror(state, _MIRROR_X, {4: 12, 12: 4, 1: 15, 15: 1, 2: 14, 14: 2, 3: 13, 13: 3, 5: 11, 11: 5, 6: 10,
                                      10: 6, 7: 9, 9: 7})


def mirror_z(state):
    """Mirror across the z axis (north <-> south), as Mirror.LEFT_RIGHT."""
    return _mirror(state, _MIRROR_Z, {0: 8, 8: 0, 1: 7, 7: 1, 2: 6, 6: 2, 3: 5, 5: 3, 9: 15, 15: 9, 10: 14, 14: 10,
                                      11: 13, 13: 11})


def _mirror(state, table, rot16):
    if state is None:
        return state
    n, p = parse(state)
    if not p:
        return state
    out = dict(p)
    for key in ('facing', 'horizontal_facing'):
        if key in p:
            out[key] = table.get(p[key], p[key])
    if 'rotation' in p:
        r = int(p['rotation'])
        out['rotation'] = str(rot16.get(r, r))
    for d in DIRS:
        if d in p:
            out[table.get(d, d)] = p[d]
    if n.endswith('_stairs') and 'shape' in p:
        out['shape'] = {'inner_left': 'inner_right', 'inner_right': 'inner_left', 'outer_left': 'outer_right',
                        'outer_right': 'outer_left'}.get(p['shape'], p['shape'])
    if n.endswith('_door') and 'hinge' in p:
        out['hinge'] = 'right' if p['hinge'] == 'left' else 'left'
    return build(n, out)


def opposite(d):
    return {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'down', 'down': 'up'}[d]


def step(d):
    """Unit (dx, dz) for a horizontal direction."""
    return {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[d]


def left_of(d):
    return {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}[d]


def right_of(d):
    return {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}[d]


AIR = 'minecraft:air'
CAVE_AIR = 'minecraft:cave_air'
VOID = None  # an unset cell: the template leaves the world as it is there
