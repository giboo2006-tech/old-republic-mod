"""Write the worldgen JSON for jigsaw structures in the repository's style (2-space indent, sorted keys).

    from structlib import worldgen as W
    W.pool('dune_camp/start', [W.element('dune_camp/main', adapted=dict(support_depth=8, ramp_rings=2))])
    W.structure('dune_camp', start_pool='dune_camp/start', biome_tag='has_structure/dune_camp', size=3,
                max_distance=64, step='surface_structures', start_height=0)
    W.structure_set('ghessar/dune_camp', [('ghessar/dune_camp', 1)], spacing=34, separation=12, salt=960301,
                    placement='clear_of_origin', min_distance=320)
    W.biome_tag('has_structure/dune_camp', ['oldrepublic:ghessar_dunes'])

Ids without a namespace are oldrepublic ids. Files are only rewritten when their content changes.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'oldrepublic')
WORLDGEN = os.path.join(DATA, 'worldgen')
TAGS = os.path.join(DATA, 'tags', 'worldgen')


def _id(v):
    return v if ':' in v else 'oldrepublic:' + v


def _write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, indent=2, sort_keys=True) + '\n'
    if os.path.exists(path):
        with open(path) as f:
            if f.read() == text:
                return path
    with open(path, 'w') as f:
        f.write(text)
    return path


def element(location, processors='minecraft:empty', projection='rigid', adapted=None, weight=1):
    """One pool entry. adapted: a dict of oldrepublic:adapted_pool_element settings
    (yard_stand, clear_from, clear_inset, support_depth, ramp_rings) to fit the terrain to the piece;
    None makes a plain single_pool_element. location 'minecraft:empty' makes an empty element."""
    if location == 'minecraft:empty':
        return {'element': {'element_type': 'minecraft:empty_pool_element'}, 'weight': weight}
    e = {'location': _id(location), 'processors': _id(processors) if processors != 'minecraft:empty' else processors,
         'projection': projection}
    if adapted is not None:
        e['element_type'] = 'oldrepublic:adapted_pool_element'
        e.update(adapted)
    else:
        e['element_type'] = 'minecraft:single_pool_element'
    return {'element': e, 'weight': weight}


def pool(pool_id, elements, fallback='minecraft:empty'):
    return _write(os.path.join(WORLDGEN, 'template_pool', pool_id + '.json'),
                  {'elements': elements, 'fallback': _id(fallback) if fallback != 'minecraft:empty' else fallback})


def structure(structure_id, start_pool, biome_tag, size=1, max_distance=64, step='surface_structures',
              start_height=0, heightmap='WORLD_SURFACE_WG', terrain_adaptation='none', no_monsters=True,
              start_jigsaw_name=None, use_expansion_hack=False, liquid_settings='ignore_waterlogging',
              spawn_overrides=None):
    """A minecraft:jigsaw structure. heightmap None keeps start_height absolute (underground starts)."""
    s = {
        'type': 'minecraft:jigsaw',
        'biomes': '#' + _id(biome_tag),
        'liquid_settings': liquid_settings,
        'max_distance_from_center': max_distance,
        'size': size,
        'start_height': {'absolute': start_height},
        'start_pool': _id(start_pool),
        'step': step,
        'terrain_adaptation': terrain_adaptation,
        'use_expansion_hack': use_expansion_hack,
    }
    if heightmap:
        s['project_start_to_heightmap'] = heightmap
    if start_jigsaw_name:
        s['start_jigsaw_name'] = _id(start_jigsaw_name)
    if spawn_overrides is not None:
        s['spawn_overrides'] = spawn_overrides
    elif no_monsters:
        s['spawn_overrides'] = {'monster': {'bounding_box': 'piece', 'spawns': []}}
    return _write(os.path.join(WORLDGEN, 'structure', structure_id + '.json'), s)


def structure_set(set_id, structures, spacing, separation, salt, exclusion=None, frequency=None,
                  placement='random_spread', min_distance=None):
    """A structure set. structures: [(structure_id, weight)]. exclusion: (other_set, chunks).
    placement 'random_spread' (vanilla) or 'clear_of_origin' (the mod's random spread that never starts
    within min_distance blocks of (0, 0), default 256: use it for every planet set)."""
    if placement not in ('random_spread', 'clear_of_origin'):
        raise ValueError('placement must be random_spread or clear_of_origin, not %r' % placement)
    kind = 'minecraft:random_spread' if placement == 'random_spread' else 'oldrepublic:clear_of_origin'
    placement = {'type': kind, 'salt': salt, 'separation': separation, 'spacing': spacing}
    if min_distance is not None:
        if kind != 'oldrepublic:clear_of_origin':
            raise ValueError('min_distance needs placement clear_of_origin')
        placement['min_distance'] = min_distance
    if exclusion:
        placement['exclusion_zone'] = {'other_set': _id(exclusion[0]), 'chunk_count': exclusion[1]}
    if frequency is not None:
        placement['frequency'] = frequency
    return _write(os.path.join(WORLDGEN, 'structure_set', set_id + '.json'),
                  {'placement': placement, 'structures': [{'structure': _id(s), 'weight': w} for s, w in structures]})


def processor_list(list_id, rules):
    """A processor list of one minecraft:rule processor. rules: [(input_block, probability, output_state)]."""
    from . import blocks as B
    out = []
    for block, prob, state in rules:
        name, props = B.parse(state)
        o = {'Name': name}
        if props:
            o['Properties'] = props
        out.append({
            'input_predicate': {'block': B.full_name(block), 'predicate_type': 'minecraft:random_block_match',
                                'probability': prob},
            'location_predicate': {'predicate_type': 'minecraft:always_true'},
            'output_state': o,
        })
    return _write(os.path.join(WORLDGEN, 'processor_list', list_id + '.json'),
                  {'processors': [{'processor_type': 'minecraft:rule', 'rules': out}]})


def biome_tag(tag_path, values):
    return _write(os.path.join(TAGS, 'biome', tag_path + '.json'), {'values': list(values)})
