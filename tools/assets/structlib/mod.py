"""The mod's blocks that carry data, and lighting, for templates (docs/STRUCTURES.md 4 and 5).

    from structlib import mod
    mod.spawner(t, 12, 1, 9, 'dig_team', count=3, anchor='SQUAD')
    mod.hub_spawner(t, 30, 3, 7, 'host', anchor='HOST')
    mod.spot(t, 'HEART', 15, 1, 15)
    mod.pedestal(t, 15, 1, 20, 'jedi', anchor='PEDESTAL_JEDI')
    mod.light_fill(t, (1, 1, 1, 29, 8, 29))

Every helper takes template-local coordinates and leaves the cells it needs clear (the air above a
spawner, the two cells of a spot).
"""
from collections import deque

from . import blocks as B
from .nbtio import IntArray, TagList, TAG_COMPOUND

# SpawnerGroups (world/site/SpawnerGroups.java and the foes that register their own).
SPAWNER_GROUPS = ('dig_team', 'salvage_crew', 'diggers', 'scavengers', 'watcher', 'tomb', 'security_droids',
                  'hollowhorns', 'ash_hounds', 'cartel_enforcers', 'stormbacks', 'scour_brood', 'hask_warriors',
                  'hymnsworn', 'storm_callers')

# HubSpawners roles (planet/HubSpawners.java); 'vendor:<vendor id>' is also a role.
HUB_ROLES = ('host', 'apprentice', 'acolyte', 'guard', 'droid', 'enforcer', 'warrior',
             'varra', 'teel', 'doorman', 'bartender', 'dorrun', 'vessba')

PEDESTAL_KINDS = ('jedi', 'sith', 'wayfarer')

SITE_SPAWNER = 'oldrepublic:site_spawner'
HUB_SPAWNER = 'oldrepublic:hub_spawner'
PEDESTAL = 'oldrepublic:holocron_pedestal'
URN_KEEPER = 'oldrepublic:urn_keeper'
AIR = 'minecraft:air'

# Block light of the light sources templates use (vanilla's values; the build kit's from BuildBlocks).
LIGHT_SOURCES = {
    'minecraft:torch': 14, 'minecraft:wall_torch': 14, 'minecraft:soul_torch': 10, 'minecraft:soul_wall_torch': 10,
    'minecraft:copper_torch': 14, 'minecraft:copper_wall_torch': 14,
    'minecraft:redstone_torch': 7, 'minecraft:redstone_wall_torch': 7,
    'minecraft:lantern': 15, 'minecraft:soul_lantern': 10, 'minecraft:copper_lantern': 15,
    'minecraft:exposed_copper_lantern': 15, 'minecraft:weathered_copper_lantern': 15,
    'minecraft:oxidized_copper_lantern': 15, 'minecraft:waxed_copper_lantern': 15,
    'minecraft:waxed_exposed_copper_lantern': 15, 'minecraft:waxed_weathered_copper_lantern': 15,
    'minecraft:waxed_oxidized_copper_lantern': 15,
    'minecraft:glowstone': 15, 'minecraft:sea_lantern': 15, 'minecraft:shroomlight': 15, 'minecraft:jack_o_lantern': 15,
    'minecraft:ochre_froglight': 15, 'minecraft:verdant_froglight': 15, 'minecraft:pearlescent_froglight': 15,
    'minecraft:end_rod': 14, 'minecraft:beacon': 15, 'minecraft:conduit': 15, 'minecraft:lava': 15,
    'minecraft:magma_block': 3, 'minecraft:crying_obsidian': 10, 'minecraft:glow_lichen': 7,
    'minecraft:amethyst_cluster': 5, 'minecraft:large_amethyst_bud': 4, 'minecraft:medium_amethyst_bud': 2,
    'minecraft:sculk_catalyst': 6, 'minecraft:respawn_anchor': 0, 'minecraft:brewing_stand': 1,
    'minecraft:enchanting_table': 7, 'minecraft:ender_chest': 7, 'minecraft:small_amethyst_bud': 1,
    'oldrepublic:light_panel': 14, 'oldrepublic:light_panel_amber': 13, 'oldrepublic:light_panel_red': 9,
    'oldrepublic:console': 6, 'oldrepublic:holo_screen': 9, 'oldrepublic:lumen_moss': 4,
    'oldrepublic:kyber_cluster': 6, 'oldrepublic:crystal_veined_stone': 2,
}
# Lit only when the state says so.
LIT_SOURCES = {'minecraft:campfire': 15, 'minecraft:soul_campfire': 10, 'minecraft:furnace': 13,
               'minecraft:blast_furnace': 13, 'minecraft:smoker': 13, 'minecraft:redstone_lamp': 15,
               'minecraft:copper_bulb': 15, 'minecraft:exposed_copper_bulb': 12, 'minecraft:weathered_copper_bulb': 8,
               'minecraft:oxidized_copper_bulb': 4, 'minecraft:waxed_copper_bulb': 15,
               'minecraft:waxed_exposed_copper_bulb': 12, 'minecraft:waxed_weathered_copper_bulb': 8,
               'minecraft:waxed_oxidized_copper_bulb': 4}

# Cells light passes through (and people can stand in) without being full blocks.
_SEE_THROUGH_SUFFIX = ('_slab', '_stairs', '_fence', '_fence_gate', '_wall', '_pane', '_bars', '_door', '_trapdoor',
                       '_carpet', '_button', '_pressure_plate', '_sign', '_banner', '_torch', 'torch', '_lantern',
                       'lantern', '_candle', 'candle', 'chain', '_rod', '_head', '_skull', '_bed', '_leaves', '_sapling',
                       'flower', '_tulip', '_mushroom', '_roots', 'vines', 'campfire', 'ladder', 'vine', 'lichen',
                       'chest', 'scaffolding', '_cluster', '_bud', 'flower_pot', 'lectern', 'anvil', 'bell', 'cobweb',
                       'grass', 'fern', 'bush', 'lily_pad', 'snow', 'rail', 'glass', 'scorch_mark', 'debris',
                       'durasteel_grate', 'light', 'air', 'water', 'jigsaw', 'structure_void', 'dripleaf', 'kelp',
                       'seagrass', 'cactus', 'bamboo', 'sugar_cane', 'pointed_dripstone', 'end_rod', 'brewing_stand',
                       'enchanting_table', 'cauldron', 'hopper', 'composter', 'grindstone', 'stonecutter', 'decorated_pot',
                       'conduit', 'beacon', 'spawner', 'heavy_core', 'pickles', 'egg', 'sniffer_egg', 'petals',
                       'wildflowers', 'leaf_litter', 'dead_bush', 'cobweb', 'hanging_sign', 'repeater', 'comparator',
                       'redstone_wire', 'tripwire', 'tripwire_hook', 'lever', 'sculk_vein', 'pale_moss_carpet')
# Cells a person can stand in (feet or head) as well as air.
_WALK_THROUGH = ('_carpet', '_button', '_pressure_plate', 'torch', 'light', 'air', 'scorch_mark', 'debris', 'grass',
                 'fern', 'flower', '_tulip', 'snow', 'rail', 'redstone_wire', 'lichen', 'petals', 'wildflowers',
                 'leaf_litter', 'dead_bush', 'vine', 'sculk_vein', 'pale_moss_carpet', 'bush')


def _path(state):
    return B.name_of(state).split(':', 1)[1] if state else ''


def see_through(state):
    """Light passes the cell (unset cells count as open: the template does not change them)."""
    if state is None:
        return True
    p = _path(state)
    return any(p.endswith(s) or p == s for s in _SEE_THROUGH_SUFFIX)


def walk_through(state):
    if state is None:
        return True
    p = _path(state)
    return any(p.endswith(s) or p == s for s in _WALK_THROUGH)


def emitted(state):
    """The block light a state gives off."""
    if state is None:
        return 0
    name, props = B.parse(state)
    if name in LIGHT_SOURCES:
        return LIGHT_SOURCES[name]
    if name in LIT_SOURCES:
        return LIT_SOURCES[name] if props.get('lit', 'false') == 'true' else 0
    if name == 'minecraft:light':
        return int(props.get('level', 15))
    if name.endswith('candle') or name.endswith('_candle'):
        return 3 * int(props.get('candles', 1)) if props.get('lit', 'false') == 'true' else 0
    return 0


# ---------------------------------------------------------------- block entities

def spawner(t, x, y, z, group, count=0, bound=False, anchor=None, replace_with=AIR):
    """A site spawner: spawns `group` once when a player comes near (count 0 is the group's own size).
    bound spawns only for the site's owner. The cell above is cleared for the spawn."""
    if group not in SPAWNER_GROUPS:
        raise ValueError('unknown spawner group %r (known: %s)' % (group, ', '.join(SPAWNER_GROUPS)))
    from .nbtio import Byte
    t.set(x, y, z, SITE_SPAWNER, nbt={'id': SITE_SPAWNER, 'group': group, 'count': int(count),
                                      'bound': Byte(1 if bound else 0), 'replace_with': {'Name': B.full_name(replace_with)}})
    t.set(x, y + 1, z, AIR)
    if anchor:
        t.anchor(anchor, x, y, z)


def hub_spawner(t, x, y, z, role, anchor=None):
    """A hub person's spawn point; the cell above stays clear."""
    if role not in HUB_ROLES and not role.startswith('vendor:'):
        raise ValueError('unknown hub role %r (known: %s, vendor:<id>)' % (role, ', '.join(HUB_ROLES)))
    t.set(x, y, z, HUB_SPAWNER, nbt={'id': HUB_SPAWNER, 'role': role})
    t.set(x, y + 1, z, AIR)
    if anchor:
        t.anchor(anchor, x, y, z)


def pedestal(t, x, y, z, kind, always_ready=False, anchor=None, fixed_id=None):
    """A holocron pedestal (jedi, sith or wayfarer). fixed_id names the holocron it always gives."""
    if kind not in PEDESTAL_KINDS:
        raise ValueError('unknown pedestal kind %r' % kind)
    nbt = {'id': PEDESTAL}
    if fixed_id:
        nbt['fixed_id'] = fixed_id
    t.set(x, y, z, B.S(PEDESTAL, kind=kind, ready=False, always_ready=bool(always_ready)), nbt=nbt)
    if anchor:
        t.anchor(anchor, x, y, z)


def urn_keeper(t, x, y, z, urns, facing='north', anchor='URN_KEEPER'):
    """The urn keeper and its four echo urns, given as template cells (stored as offsets from the keeper)."""
    offsets = []
    for (ux, uy, uz) in urns:
        offsets += [ux - x, uy - y, uz - z]
    t.set(x, y, z, B.S(URN_KEEPER, facing=facing, solved=False), nbt={'id': URN_KEEPER, 'urns': IntArray(offsets)})
    if anchor:
        t.anchor(anchor, x, y, z)


def banner(t, x, y, z, facing, base='white', patterns=(), wall=True):
    """A banner of colour `base` with pattern layers [(pattern id, colour)], e.g.
    [('oldrepublic:republic_chevron', 'blue')]. wall False makes a standing banner (facing is then ignored)."""
    if wall:
        state = B.S('%s_wall_banner' % base, facing=facing)
    else:
        state = B.S('%s_banner' % base, rotation={'south': 0, 'west': 4, 'north': 8, 'east': 12}.get(facing, 0))
    layers = TagList(TAG_COMPOUND, [{'pattern': p if ':' in p else 'minecraft:' + p, 'color': c} for p, c in patterns])
    t.set(x, y, z, state, nbt={'id': 'minecraft:banner', 'patterns': layers})


def lectern(t, x, y, z, facing='north', book=False):
    """A lectern, with a blank book on it when book is True."""
    nbt = {'id': 'minecraft:lectern'}
    if book:
        nbt.update({'Book': {'id': 'minecraft:writable_book', 'count': 1}, 'Page': 0})
    t.set(x, y, z, B.S('lectern', facing=facing, has_book=bool(book), powered=False), nbt=nbt)


def spot(t, anchor, x, y, z):
    """A feet anchor: two cells of air (feet and head) over whatever stands below."""
    t.set(x, y, z, AIR)
    t.set(x, y + 1, z, AIR)
    t.anchor(anchor, x, y, z)


# ---------------------------------------------------------------- lighting

def _spread(t, level, queue, extra):
    sx, sy, sz = t.size
    while queue:
        p = queue.popleft()
        v = level[p] - 1
        if v <= 0:
            continue
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if not (0 <= q[0] < sx and 0 <= q[1] < sy and 0 <= q[2] < sz) or level.get(q, 0) >= v:
                continue
            if not see_through(extra.get(q, t.cells.get(q))):
                continue
            level[q] = v
            queue.append(q)


def light_map(t, extra=None):
    """Block light (0 to 15) of every cell of the template from its own light sources (no sky), as a dict
    {cell: level}. extra: {cell: state} laid over the template's cells."""
    extra = extra or {}
    level = {}
    queue = deque()
    for p, s in list(t.cells.items()) + list(extra.items()):
        e = emitted(s)
        if e > level.get(p, 0):
            level[p] = e
            queue.append(p)
    _spread(t, level, queue, extra)
    return level


def floor_cells(t, region=None):
    """Cells a person can stand in: walk-through feet and head cells the template sets, over a cell it sets
    that is not walk-through (open ground the template leaves alone is lit by the sky)."""
    sx, sy, sz = t.size
    x0, y0, z0, x1, y1, z1 = region or (0, 1, 0, sx - 1, sy - 2, sz - 1)
    out = []
    for x in range(max(0, x0), min(sx - 1, x1) + 1):
        for z in range(max(0, z0), min(sz - 1, z1) + 1):
            for y in range(max(1, y0), min(sy - 2, y1) + 1):
                feet = t.cells.get((x, y, z))
                head = t.cells.get((x, y + 1, z))
                below = t.cells.get((x, y - 1, z))
                if feet is None or head is None or below is None:
                    continue
                if walk_through(feet) and walk_through(head) and not walk_through(below):
                    out.append((x, y, z))
    return out


def light_fill(t, region=None, level=8, strength=13):
    """Adds invisible minecraft:light blocks (of `strength`) at head height over dark floor cells in `region`
    (x0, y0, z0, x1, y1, z1; default the whole template) until every floor cell there has block light
    `level` or more. Returns the cells it lit. Place the visible lamps first: this only fills the gaps."""
    if strength < level + 1:
        raise ValueError('light_fill: strength %d cannot light floors to %d' % (strength, level))
    floors = floor_cells(t, region)
    added = {}
    lit = light_map(t)
    for _ in range(4096):
        dark = [p for p in floors if lit.get(p, 0) < level]
        if not dark:
            break
        # The 5x5 column bucket with the most dark floor, then the dark cell nearest its middle.
        buckets = {}
        for p in dark:
            buckets.setdefault((p[0] // 5, p[1] // 4, p[2] // 5), []).append(p)
        cells = max(sorted(buckets.items()), key=lambda kv: len(kv[1]))[1]
        mx = sum(p[0] for p in cells) / len(cells)
        mz = sum(p[2] for p in cells) / len(cells)
        x, y, z = min(cells, key=lambda p: ((p[0] - mx) ** 2 + (p[2] - mz) ** 2, p))
        head = (x, y + 1, z)
        if B.name_of(t.cells.get(head)) not in ('minecraft:air', 'minecraft:cave_air') or head in added:
            head = (x, y, z)
        added[head] = B.S('light', level=strength)
        if lit.get(head, 0) < strength:
            lit[head] = strength
            _spread(t, lit, deque([head]), added)
    for p, s in added.items():
        t.set(*p, s)
    return sorted(added)
