#!/usr/bin/env python3
"""Milestone 6 structure check (docs/STRUCTURES.md 8, docs/MILESTONE_6.md 7.2 and 9). Run on CI.

Checks every structure template, site anchor sidecar, site meta file and worldgen file of the mod:

  templates   block ids exist (vanilla 26.2 in tools/vanilla_blocks_26_2.txt, or the mod's blockstates) and their
              properties are sane; the DataVersion is 4903; sizes are at most 128 and at most 60,000 cells are not
              air; spawner groups, hub roles and pedestal kinds are known; chests name existing loot tables and
              barrels name none; jigsaws name existing pools and leave final_state air; no signs (they would carry
              no text); a template that is not in any pool and not linked from a meta file has site meta
  anchors     every name is in SiteAnchors.Anchor; every cell is inside the template; a feet anchor (air) has air
              above and a block below it, and a spawner anchor has air above; every anchor of the template's
              baseline (tools/m6_baseline.json) and every extra required one is still there; a <name>_2 variant has
              the anchors of <name>
  baseline    keep_size templates keep their size, same_size pairs share a footprint (x and z), "mechanism" cells
              hold the same block
  meta        known fields and values (structlib/meta.py), the biome tag exists, linked templates exist
  worldgen    pools name existing templates, processors and fallbacks; structures have biomes, spawn_overrides and a
              step, name existing pools and biome tags, keep max_distance_from_center at most 128 and size at
              most 20; structure sets name existing structures, have unique salts and a known placement; a set
              whose structures only spawn in the mod's biomes (a planet set) uses oldrepublic:clear_of_origin;
              processor lists output known blocks

Usage:
  python3 tools/check_m6_structures.py                 # everything; exits 1 on any problem
  python3 tools/check_m6_structures.py --only story/   # only templates, sidecars, meta and worldgen under a path prefix
  python3 tools/check_m6_structures.py --report        # print the problems but exit 0 (CI before the rebuild lands)

Only stdlib and tools/assets/structlib.
"""
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'assets'))

from structlib import nbtio  # noqa: E402
from structlib import meta as M  # noqa: E402
from structlib import mod  # noqa: E402

RES = os.path.join(ROOT, 'src', 'main', 'resources')
DATA = os.path.join(RES, 'data')
OR = os.path.join(DATA, 'oldrepublic')
STRUCTURES = os.path.join(OR, 'structure')
ANCHORS = os.path.join(OR, 'site_anchors')
META = os.path.join(OR, 'site_meta')
WORLDGEN = os.path.join(OR, 'worldgen')
BLOCKSTATES = os.path.join(RES, 'assets', 'oldrepublic', 'blockstates')
ANCHOR_JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'oldrepublic', 'world', 'site', 'SiteAnchors.java')
BASELINE = os.path.join(ROOT, 'tools', 'm6_baseline.json')
VANILLA = os.path.join(ROOT, 'tools', 'vanilla_blocks_26_2.txt')

DATA_VERSION = 4903
MAX_AXIS = 128
MAX_CELLS = 60000
AIRS = ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')
CHESTS = ('minecraft:chest', 'minecraft:trapped_chest')
DIRECTIONS = {'north', 'south', 'east', 'west', 'up', 'down'}
BOOLEAN_PROPS = {'waterlogged', 'lit', 'open', 'powered', 'hanging', 'up', 'down', 'attached', 'occupied', 'triggered',
                 'enabled', 'locked', 'snowy', 'persistent', 'in_wall', 'extended', 'short', 'inverted', 'disarmed',
                 'signal_fire', 'has_book', 'drag', 'unstable', 'bottom', 'conditional', 'has_record', 'eye', 'berries',
                 'tip', 'natural', 'cracked', 'ominous', 'can_summon', 'shrieking', 'crafting', 'has_bottle_0',
                 'has_bottle_1', 'has_bottle_2'}
INT_PROPS = {'level', 'age', 'rotation', 'distance', 'layers', 'candles', 'power', 'delay', 'note', 'moisture', 'bites',
             'charges', 'hatch', 'pickles', 'eggs', 'stage', 'honey_level', 'dusted', 'flower_amount',
             'segment_amount'}
SIGN_SUFFIX = ('_sign', '_wall_sign', '_hanging_sign', '_wall_hanging_sign')
# Anchors that mark a corner of a region rather than a cell to stand in or a block.
REGION_ANCHORS = {'UNDERCROFT_MIN', 'UNDERCROFT_MAX'}
PLANET_PREFIXES = ('selvaren/', 'varkhast/', 'ghessar/', 'tyrvann/')


class Problems:
    def __init__(self):
        self.items = []

    def add(self, where, text):
        self.items.append('%s: %s' % (where, text))


def rel(path, base):
    return os.path.relpath(path, base).replace(os.sep, '/')


def load_json(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def split_id(v, default='oldrepublic'):
    if ':' in v:
        ns, _, p = v.partition(':')
        return ns, p
    return default, v


def data_file(ns, folder, path, ext='.json'):
    return os.path.join(DATA, ns, folder, path + ext)


# ---------------------------------------------------------------- reference data

def vanilla_ids():
    with open(VANILLA, encoding='utf-8') as f:
        return {'minecraft:' + line.strip() for line in f if line.strip() and not line.startswith('#')}


def mod_blocks():
    """oldrepublic block id -> {property: set of values} named by its blockstate file."""
    out = {}
    for path in glob.glob(os.path.join(BLOCKSTATES, '*.json')):
        props = {}
        data = load_json(path)
        for key in data.get('variants', {}):
            for kv in key.split(','):
                if '=' in kv:
                    k, v = kv.split('=', 1)
                    props.setdefault(k, set()).add(v)

        def when(w):
            if 'OR' in w:
                for x in w['OR']:
                    when(x)
                return
            if 'AND' in w:
                for x in w['AND']:
                    when(x)
                return
            for k, v in w.items():
                props.setdefault(k, set()).update(str(v).split('|'))
        for part in data.get('multipart', []):
            if 'when' in part:
                when(part['when'])
        if path.endswith('_wall.json') and 'multipart' in data:
            # A wall's blockstate (like vanilla's) never names up=false or <side>=none: the states with no part.
            props.setdefault('up', set()).update({'true', 'false'})
            for side in ('north', 'east', 'south', 'west'):
                props.setdefault(side, set()).update({'none', 'low', 'tall'})
        out['oldrepublic:' + os.path.basename(path)[:-5]] = props
    return out


def anchor_names():
    with open(ANCHOR_JAVA, encoding='utf-8') as f:
        text = f.read()
    body = text[text.index('public enum Anchor {') + len('public enum Anchor {'):]
    body = re.sub(r'//[^\n]*', '', body)
    body = body[:body.index(';')]
    return {n.strip() for n in body.split(',') if n.strip()}


def vendor_ids():
    ids = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(OR, 'vendor', '*.json'))}
    ids |= {os.path.basename(p)[len('vendor_'):-5] for p in glob.glob(os.path.join(OR, 'dialogue', 'vendor_*.json'))}
    return ids


def loot_exists(table):
    ns, p = split_id(table, 'minecraft')
    if ns == 'minecraft':
        return True  # vanilla tables are not in the repository
    return os.path.exists(data_file(ns, 'loot_table', p))


def biome_tag_exists(tag):
    ns, p = split_id(tag.lstrip('#'), 'minecraft')
    if ns != 'oldrepublic':
        return True
    return os.path.exists(data_file(ns, os.path.join('tags', 'worldgen', 'biome'), p))


def pool_exists(pool):
    ns, p = split_id(pool, 'minecraft')
    if pool == 'minecraft:empty':
        return True
    if ns == 'minecraft':
        return True
    return os.path.exists(data_file(ns, os.path.join('worldgen', 'template_pool'), p))


def template_exists(tid):
    ns, p = split_id(tid)
    if ns == 'minecraft':
        return True
    return os.path.exists(data_file(ns, 'structure', p, '.nbt'))


# ---------------------------------------------------------------- property sanity

def property_problems(name, props, mods):
    out = []
    path = name.split(':', 1)[1]
    if name in mods:
        known = mods[name]
        for k, v in props.items():
            if k in known and v not in known[k]:
                out.append('%s[%s=%s]: the blockstate file only knows %s' % (name, k, v, sorted(known[k])))
        return out
    for k, v in props.items():
        if not re.fullmatch(r'[a-z0-9_]+', str(v)):
            out.append('%s[%s=%s]: not a property value' % (name, k, v))
        elif k == 'facing' and v not in DIRECTIONS:
            out.append('%s[facing=%s]' % (name, v))
        elif k == 'axis' and v not in ('x', 'y', 'z'):
            out.append('%s[axis=%s]' % (name, v))
        elif k == 'half' and v not in ('top', 'bottom', 'upper', 'lower'):
            out.append('%s[half=%s]' % (name, v))
        elif k in BOOLEAN_PROPS and v not in ('true', 'false'):
            out.append('%s[%s=%s]: not true or false' % (name, k, v))
        elif k in INT_PROPS and not v.isdigit():
            out.append('%s[%s=%s]: not a number' % (name, k, v))
        elif path.endswith('_slab') and k == 'type' and v not in ('top', 'bottom', 'double'):
            out.append('%s[type=%s]' % (name, v))
        elif path.endswith('_stairs') and k == 'shape' and v not in (
                'straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
            out.append('%s[shape=%s]' % (name, v))
        elif path.endswith('_wall') and k in ('north', 'south', 'east', 'west') and v not in ('none', 'low', 'tall'):
            out.append('%s[%s=%s]: walls take none, low or tall' % (name, k, v))
    return out


# ---------------------------------------------------------------- templates

def walkable(entry):
    """A cell a person can stand in: absent, air or a walk-through block."""
    return entry is None or mod.walk_through(entry['Name'])


def check_template(tpath, problems, ctx):
    where = 'structure/%s.nbt' % tpath
    try:
        data = nbtio.load(os.path.join(STRUCTURES, tpath + '.nbt'))
    except Exception as e:  # noqa: BLE001 - any read failure is the problem
        problems.add(where, 'cannot be read: %s' % e)
        return None
    if data.get('DataVersion') != DATA_VERSION:
        problems.add(where, 'DataVersion %s, expected %d' % (data.get('DataVersion'), DATA_VERSION))
    size = tuple(data['size'])
    if any(s > MAX_AXIS or s < 1 for s in size):
        problems.add(where, 'size %s: every axis must be 1 to %d' % (list(size), MAX_AXIS))
    palette = data['palette']
    cells = {}
    solid_count = 0
    for i, entry in enumerate(palette):
        name = entry['Name']
        if name not in ctx['vanilla'] and name not in ctx['mods']:
            problems.add(where, 'unknown block %s' % name)
        for p in property_problems(name, entry.get('Properties', {}), ctx['mods']):
            problems.add(where, p)
        if name.split(':', 1)[1].endswith(SIGN_SUFFIX):
            problems.add(where, '%s: no signs in templates (they would carry no text)' % name)
    for b in data['blocks']:
        pos = tuple(b['pos'])
        if not all(0 <= pos[i] < size[i] for i in range(3)):
            problems.add(where, 'block at %s is outside the size %s' % (list(pos), list(size)))
        entry = palette[b['state']]
        cells[pos] = entry
        if entry['Name'] not in AIRS:
            solid_count += 1
        nbt = b.get('nbt')
        name = entry['Name']
        if name == mod.SITE_SPAWNER:
            group = (nbt or {}).get('group', '')
            if group not in mod.SPAWNER_GROUPS:
                problems.add(where, 'site_spawner at %s: unknown group %r' % (list(pos), group))
        elif name == mod.HUB_SPAWNER:
            role = (nbt or {}).get('role', '')
            if role.startswith('vendor:'):
                if role[7:] not in ctx['vendors']:
                    problems.add(where, 'hub_spawner at %s: unknown vendor %r' % (list(pos), role[7:]))
            elif role not in mod.HUB_ROLES:
                problems.add(where, 'hub_spawner at %s: unknown role %r' % (list(pos), role))
        elif name in CHESTS:
            table = (nbt or {}).get('LootTable')
            if table and not loot_exists(table):
                problems.add(where, 'chest at %s: loot table %s does not exist' % (list(pos), table))
        elif name == 'minecraft:barrel':
            if (nbt or {}).get('LootTable'):
                problems.add(where, 'barrel at %s carries loot (barrels never do; use a chest)' % list(pos))
        elif name == 'minecraft:jigsaw':
            pool = (nbt or {}).get('pool', 'minecraft:empty')
            if not pool_exists(pool):
                problems.add(where, 'jigsaw at %s: pool %s does not exist' % (list(pos), pool))
            final = (nbt or {}).get('final_state', 'minecraft:air')
            if final.split('[')[0] not in ('minecraft:air', 'minecraft:structure_void'):
                problems.add(where, 'jigsaw at %s: final_state %s (use minecraft:air)' % (list(pos), final))
    if solid_count > MAX_CELLS:
        problems.add(where, '%d cells that are not air (budget %d: split it into jigsaw pieces)' % (solid_count, MAX_CELLS))
    return {'size': size, 'cells': cells}


def check_anchors(tpath, info, problems, ctx):
    side = os.path.join(ANCHORS, tpath + '.json')
    where = 'site_anchors/%s.json' % tpath
    anchors = {}
    if os.path.exists(side):
        try:
            anchors = load_json(side)
        except ValueError as e:
            problems.add(where, 'not JSON: %s' % e)
            anchors = {}
    size, cells = info['size'], info['cells']
    for name, positions in anchors.items():
        if name not in ctx['anchors']:
            problems.add(where, '%s is not a SiteAnchors.Anchor name (ask the lead for a new one)' % name)
        for pos in positions:
            if not (isinstance(pos, list) and len(pos) == 3 and all(0 <= pos[i] < size[i] for i in range(3))):
                problems.add(where, '%s %s is outside the template %s' % (name, pos, list(size)))
                continue
            if name in REGION_ANCHORS:
                continue
            x, y, z = pos
            here = cells.get((x, y, z))
            above = cells.get((x, y + 1, z))
            below = cells.get((x, y - 1, z))
            if here is None or here['Name'] in AIRS:
                if here is None:
                    problems.add(where, '%s %s: a feet anchor must be an explicit air cell (mod.spot)' % (name, pos))
                if above is None or not walkable(above):
                    problems.add(where, '%s %s: the cell above a feet anchor must be air' % (name, pos))
                if y > 0 and (below is None or walkable(below)):
                    problems.add(where, '%s %s: a feet anchor needs a block below it' % (name, pos))
            elif here['Name'] in (mod.SITE_SPAWNER, mod.HUB_SPAWNER):
                if above is None or not walkable(above):
                    problems.add(where, '%s %s: the cell above a spawner must be air' % (name, pos))
    base = ctx['baseline']['templates']
    need = set(base.get(tpath, {}).get('anchors', []))
    need |= set(ctx['baseline']['required'].get(tpath, []))
    if tpath.endswith('_2') and tpath[:-2] in base:
        need |= set(base[tpath[:-2]].get('anchors', []))
    for name in sorted(need - set(anchors)):
        problems.add(where, 'required anchor %s is missing' % name)


def check_baseline(tpath, info, problems, ctx):
    bl = ctx['baseline']
    entry = bl['templates'].get(tpath)
    where = 'structure/%s.nbt' % tpath
    if entry and tpath in bl['keep_size'] and list(info['size']) != entry['size']:
        problems.add(where, 'size %s, the plan keeps %s' % (list(info['size']), entry['size']))
    if entry and 'mechanism' in entry:
        for key, name in entry['mechanism'].items():
            pos = tuple(int(v) for v in key.split(','))
            got = info['cells'].get(pos)
            if got is None or got['Name'] != name:
                problems.add(where, 'mechanism cell %s must stay %s (is %s)' % (list(pos), name,
                                                                                  got['Name'] if got else 'unset'))


# ---------------------------------------------------------------- worldgen

def biome_values(tag, seen=None):
    """The biome ids a biome tag resolves to (other namespaces' tags stay as '#tag')."""
    seen = seen or set()
    ns, p = split_id(tag.lstrip('#'), 'minecraft')
    if ns != 'oldrepublic' or tag in seen:
        return {tag}
    seen.add(tag)
    path = data_file(ns, os.path.join('tags', 'worldgen', 'biome'), p)
    if not os.path.exists(path):
        return set()
    out = set()
    for v in load_json(path).get('values', []):
        v = v['id'] if isinstance(v, dict) else v
        out |= biome_values(v, seen) if v.startswith('#') else {v}
    return out


def check_worldgen(only, problems, ctx):
    salts = {}
    for path in sorted(glob.glob(os.path.join(WORLDGEN, 'template_pool', '**', '*.json'), recursive=True)):
        pid = rel(path, os.path.join(WORLDGEN, 'template_pool'))[:-5]
        where = 'template_pool/%s.json' % pid
        data = load_json(path)
        for e in data.get('elements', []):
            el = e.get('element', {})
            loc = el.get('location')
            if loc and loc.startswith('oldrepublic:'):
                ctx['pooled'].add(split_id(loc)[1])
            if not pid.startswith(only):
                continue
            if loc and not template_exists(loc):
                problems.add(where, 'template %s does not exist' % loc)
            proc = el.get('processors', 'minecraft:empty')
            if isinstance(proc, str) and proc != 'minecraft:empty':
                ns, p = split_id(proc)
                if ns == 'oldrepublic' and not os.path.exists(data_file(ns, os.path.join('worldgen', 'processor_list'), p)):
                    problems.add(where, 'processor list %s does not exist' % proc)
        if pid.startswith(only) and not pool_exists(data.get('fallback', 'minecraft:empty')):
            problems.add(where, 'fallback pool %s does not exist' % data.get('fallback'))

    structures = {}
    for path in sorted(glob.glob(os.path.join(WORLDGEN, 'structure', '**', '*.json'), recursive=True)):
        sid = rel(path, os.path.join(WORLDGEN, 'structure'))[:-5]
        data = load_json(path)
        structures['oldrepublic:' + sid] = data
        if not sid.startswith(only):
            continue
        where = 'structure/%s.json' % sid
        for key in ('biomes', 'spawn_overrides', 'step'):
            if key not in data:
                problems.add(where, 'no %s (the game refuses a structure without it)' % key)
        if data.get('type') == 'minecraft:jigsaw':
            if not pool_exists(data.get('start_pool', '')):
                problems.add(where, 'start pool %s does not exist' % data.get('start_pool'))
            if data.get('max_distance_from_center', 0) > 128:
                problems.add(where, 'max_distance_from_center %s is over 128' % data.get('max_distance_from_center'))
            if not 0 <= data.get('size', 0) <= 20:
                problems.add(where, 'size %s is outside 0 to 20' % data.get('size'))
        biomes = data.get('biomes')
        if isinstance(biomes, str) and biomes.startswith('#') and not biome_tag_exists(biomes):
            problems.add(where, 'biome tag %s does not exist' % biomes)

    for path in sorted(glob.glob(os.path.join(WORLDGEN, 'structure_set', '**', '*.json'), recursive=True)):
        set_id = rel(path, os.path.join(WORLDGEN, 'structure_set'))[:-5]
        data = load_json(path)
        placement = data.get('placement', {})
        salt = placement.get('salt')
        salts.setdefault(salt, []).append(set_id)
        if not set_id.startswith(only):
            continue
        where = 'structure_set/%s.json' % set_id
        kind = placement.get('type')
        if kind not in ('minecraft:random_spread', 'oldrepublic:clear_of_origin', 'minecraft:concentric_rings'):
            problems.add(where, 'unknown placement type %s' % kind)
        if kind in ('minecraft:random_spread', 'oldrepublic:clear_of_origin'):
            if not placement.get('spacing', 0) > placement.get('separation', 0) >= 0:
                problems.add(where, 'spacing %s must be more than separation %s' % (placement.get('spacing'),
                                                                                    placement.get('separation')))
        if 'min_distance' in placement and kind != 'oldrepublic:clear_of_origin':
            problems.add(where, 'min_distance needs the oldrepublic:clear_of_origin placement')
        planet = True
        for s in data.get('structures', []):
            sid = s.get('structure', '')
            if sid not in structures:
                problems.add(where, 'structure %s does not exist' % sid)
                planet = False
                continue
            biomes = structures[sid].get('biomes', '')
            values = biome_values(biomes) if isinstance(biomes, str) else set(biomes)
            if not values or any(not v.startswith('oldrepublic:') for v in values):
                planet = False
        if (planet or set_id.startswith(PLANET_PREFIXES)) and kind != 'oldrepublic:clear_of_origin':
            problems.add(where, 'a planet set must use "type": "oldrepublic:clear_of_origin" (it spawns only on planets)')
    for salt, sets in salts.items():
        if len(sets) > 1 and any(s.startswith(only) for s in sets):
            problems.add('structure_set', 'salt %s is shared by %s' % (salt, ', '.join(sets)))

    for path in sorted(glob.glob(os.path.join(WORLDGEN, 'processor_list', '**', '*.json'), recursive=True)):
        lid = rel(path, os.path.join(WORLDGEN, 'processor_list'))[:-5]
        if not lid.startswith(only):
            continue
        text = json.dumps(load_json(path))
        for name in set(re.findall(r'"Name": "([a-z0-9_:]+)"', text)):
            if ':' not in name:
                name = 'minecraft:' + name
            if name not in ctx['vanilla'] and name not in ctx['mods']:
                problems.add('processor_list/%s.json' % lid, 'unknown block %s' % name)

    for path in sorted(glob.glob(os.path.join(OR, 'tags', 'worldgen', 'biome', '**', '*.json'), recursive=True)):
        tid = rel(path, os.path.join(OR, 'tags', 'worldgen', 'biome'))[:-5]
        if not tid.startswith(only) and not tid.startswith('has_structure/' + only):
            continue
        for v in load_json(path).get('values', []):
            v = v['id'] if isinstance(v, dict) else v
            if v.startswith('oldrepublic:') and not os.path.exists(data_file('oldrepublic', os.path.join('worldgen', 'biome'),
                                                                             split_id(v)[1])):
                problems.add('tags/worldgen/biome/%s.json' % tid, 'biome %s does not exist' % v)
            if v.startswith('#') and not biome_tag_exists(v):
                problems.add('tags/worldgen/biome/%s.json' % tid, 'tag %s does not exist' % v)


def check_meta(only, problems, ctx):
    for path in sorted(glob.glob(os.path.join(META, '**', '*.json'), recursive=True)):
        tpath = rel(path, META)[:-5]
        where = 'site_meta/%s.json' % tpath
        try:
            data = load_json(path)
        except ValueError as e:
            problems.add(where, 'not JSON: %s' % e)
            continue
        for link in (data.get('links') or {}) if isinstance(data, dict) else {}:
            ctx['linked'].add(split_id(link)[1])
        if not tpath.startswith(only):
            continue
        ctx['meta'].add(tpath)
        if not template_exists(tpath):
            problems.add(where, 'there is no template %s' % tpath)
        for p in M.problems(data):
            problems.add(where, p)
        if isinstance(data, dict):
            if isinstance(data.get('biomes'), str) and not biome_tag_exists(data['biomes']):
                problems.add(where, 'biome tag %s does not exist' % data['biomes'])
            for link in data.get('links') or {}:
                if not template_exists(link):
                    problems.add(where, 'linked template %s does not exist' % link)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--only', default='', help='check only paths under this prefix (e.g. story/ or selvaren/)')
    ap.add_argument('--report', action='store_true', help='print the problems but exit 0')
    args = ap.parse_args()
    only = args.only

    ctx = {'vanilla': vanilla_ids(), 'mods': mod_blocks(), 'anchors': anchor_names(), 'vendors': vendor_ids(),
           'baseline': load_json(BASELINE), 'pooled': set(), 'linked': set(), 'meta': set()}
    problems = Problems()
    check_worldgen(only, problems, ctx)
    check_meta('', Problems(), ctx)  # collects links and meta files everywhere (problems only under the prefix below)
    ctx['meta'] = set()
    check_meta(only, problems, ctx)

    templates = sorted(rel(p, STRUCTURES)[:-4] for p in glob.glob(os.path.join(STRUCTURES, '**', '*.nbt'), recursive=True))
    sizes = {}
    checked = 0
    for tpath in templates:
        if not tpath.startswith(only):
            continue
        checked += 1
        info = check_template(tpath, problems, ctx)
        if info is None:
            continue
        sizes[tpath] = info['size']
        check_anchors(tpath, info, problems, ctx)
        check_baseline(tpath, info, problems, ctx)
        runtime = tpath not in ctx['pooled'] and tpath not in ctx['linked'] and tpath not in ctx['baseline']['exempt_meta']
        if runtime and not os.path.exists(os.path.join(META, tpath + '.json')):
            problems.add('structure/%s.nbt' % tpath, 'a runtime template needs site meta (meta.write, docs/STRUCTURES.md 7)')
    for pair in ctx['baseline']['same_size']:
        if all(p in sizes for p in pair) and len({(sizes[p][0], sizes[p][2]) for p in pair}) > 1:
            problems.add(' and '.join(pair), 'must have the same footprint (are %s)' % ', '.join(
                str(list(sizes[p])) for p in pair))
    for tpath in ctx['baseline']['templates']:
        if tpath.startswith(only) and tpath not in templates:
            problems.add('structure/%s.nbt' % tpath, 'was removed (template ids are never renamed or removed)')
    for tpath in ctx['baseline']['required']:
        if tpath.startswith(only) and tpath not in templates:
            problems.add('structure/%s.nbt' % tpath, 'the plan adds this template; it does not exist yet')

    for p in problems.items:
        print('check_m6_structures:', p)
    print('check_m6_structures: %d templates checked%s, %d problem(s)' % (checked, ' under ' + only if only else '',
                                                                          len(problems.items)))
    if problems.items and not args.report:
        sys.exit(1)


if __name__ == '__main__':
    main()
