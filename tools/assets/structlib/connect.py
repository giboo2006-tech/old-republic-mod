"""Neighbour-dependent block properties, set the way the game would set them.

Template.save() runs finish() first, so fences, walls, panes and iron bars join their neighbours and
stairs take their corner shapes (inner and outer) without the generator working them out by hand.
A cell's explicit properties are overwritten only for these connection properties.
"""
from . import blocks as B

DIRS = (('north', 0, -1), ('east', 1, 0), ('south', 0, 1), ('west', -1, 0))
_NOT_STURDY_SUFFIX = ('_slab', '_stairs', '_fence', '_fence_gate', '_wall', '_pane', '_bars', '_door', '_trapdoor',
                      '_carpet', '_button', '_pressure_plate', '_sign', '_banner', '_torch', '_lantern', '_candle',
                      '_chain', '_rod', '_head', '_skull', '_bed', '_leaves', '_sapling', '_flower', '_tulip',
                      '_mushroom', '_roots', '_vines', '_campfire', 'ladder', 'vine', 'glow_lichen', 'chest',
                      'scaffolding', '_cluster', '_bud', 'flower_pot', 'lectern', 'anvil', 'bell', 'cobweb',
                      'grass', 'fern', 'dead_bush', 'lily_pad', 'snow', 'rail', 'scorch_mark', 'debris')
_SOFT = {'minecraft:air', 'minecraft:cave_air', 'minecraft:water', 'minecraft:lava', 'minecraft:glass',
         'minecraft:jigsaw', 'minecraft:structure_void', 'minecraft:barrier', 'minecraft:light'}


def _sturdy(name):
    if name is None or name in _SOFT:
        return False
    path = name.split(':', 1)[1]
    if path.startswith('potted_') or path.endswith('stained_glass') or 'glass' in path and 'pane' not in path:
        return 'tinted' in path
    for s in _NOT_STURDY_SUFFIX:
        if path.endswith(s) or path == s:
            return False
    return True


def _kind(name):
    if name is None:
        return None
    path = name.split(':', 1)[1]
    if path.endswith('_fence_gate'):
        return 'gate'
    if path.endswith('_fence'):
        return 'nether_fence' if path == 'nether_brick_fence' else 'fence'
    if path.endswith('_wall') and not any(path.endswith(s) for s in ('_wall_torch', '_wall_sign', '_wall_banner',
                                                                     '_wall_head', '_wall_skull', '_wall_fan',
                                                                     '_wall_hanging_sign')):
        return 'wall'
    if path.endswith('_pane') or path.endswith('_bars') or path == 'iron_bars':
        return 'pane'
    if path.endswith('_stairs'):
        return 'stairs'
    return None


def finish(t):
    """Set connection properties on every fence, wall, pane, bar and stair in the template."""
    names = {p: B.name_of(s) for p, s in t.cells.items()}
    updates = {}
    for p, s in t.cells.items():
        n = names[p]
        k = _kind(n)
        if k is None or k == 'gate':
            continue
        x, y, z = p
        name, props = B.parse(s)
        if k == 'stairs':
            props['shape'] = _stair_shape(t, p, props)
        elif k in ('fence', 'nether_fence'):
            for d, dx, dz in DIRS:
                nn = names.get((x + dx, y, z + dz))
                nk = _kind(nn)
                props[d] = 'true' if (nk == k or (nk == 'fence' and k == 'fence') or
                                      (nk == 'gate' and _gate_faces(t, (x + dx, y, z + dz), d)) or
                                      _sturdy(nn)) else 'false'
        elif k == 'pane':
            for d, dx, dz in DIRS:
                nn = names.get((x + dx, y, z + dz))
                nk = _kind(nn)
                props[d] = 'true' if (nk in ('pane', 'wall') or _sturdy(nn)) else 'false'
        elif k == 'wall':
            above = names.get((x, y + 1, z))
            tall_above = _sturdy(above) or _kind(above) == 'wall'
            con = {}
            for d, dx, dz in DIRS:
                nn = names.get((x + dx, y, z + dz))
                nk = _kind(nn)
                c = nk in ('wall', 'pane') or (nk == 'gate' and _gate_faces(t, (x + dx, y, z + dz), d)) or _sturdy(nn)
                con[d] = c
                props[d] = ('tall' if tall_above else 'low') if c else 'none'
            straight_ns = con['north'] and con['south'] and not con['east'] and not con['west']
            straight_ew = con['east'] and con['west'] and not con['north'] and not con['south']
            props['up'] = 'false' if (straight_ns or straight_ew) and not tall_above else 'true'
            if 'waterlogged' not in props:
                props['waterlogged'] = 'false'
        updates[p] = B.build(name, props)
    t.cells.update(updates)


def _gate_faces(t, gp, d):
    """A fence gate connects when its wall runs across the direction we look from."""
    gs = t.cells.get(gp)
    _, gprops = B.parse(gs)
    f = gprops.get('facing', 'north')
    return (f in ('north', 'south')) == (d in ('east', 'west'))


def _stair_at(t, p):
    s = t.cells.get(p)
    if s is None:
        return None
    n, props = B.parse(s)
    return props if n.endswith('_stairs') else None


def _ccw(d):
    return {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}[d]


def _axis(d):
    return 'x' if d in ('east', 'west') else 'z'


def _rel(p, d):
    dx, dz = B.step(d)
    return (p[0] + dx, p[1], p[2] + dz)


def _can_take(t, p, props, face):
    n = _stair_at(t, _rel(p, face))
    return n is None or n.get('facing') != props.get('facing') or n.get('half') != props.get('half')


def _stair_shape(t, p, props):
    d = props.get('facing', 'north')
    half = props.get('half', 'bottom')
    behind = _stair_at(t, _rel(p, d))
    if behind is not None and behind.get('half', 'bottom') == half:
        d2 = behind.get('facing', 'north')
        if _axis(d2) != _axis(d) and _can_take(t, p, props, B.opposite(d2)):
            return 'outer_left' if d2 == _ccw(d) else 'outer_right'
    front = _stair_at(t, _rel(p, B.opposite(d)))
    if front is not None and front.get('half', 'bottom') == half:
        d3 = front.get('facing', 'north')
        if _axis(d3) != _axis(d) and _can_take(t, p, props, d3):
            return 'inner_left' if d3 == _ccw(d) else 'inner_right'
    return 'straight'
