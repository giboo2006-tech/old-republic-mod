"""Worldgen for worker O1's landmarks: the pools (with the new _2 start variants), the four structures (ids,
sets and salts unchanged), the weathering processor lists. Run after the templates are built.

    python3 tools/assets/structures/o1/worldgen_o1.py [--check]

--check assembles every structure (every start variant, every rotation) with structlib.jigsaw and prints
the report; it fails when a piece never fits or a box leaves max_distance.
"""
import sys

import common as C
from common import W


def adapted(height, stand, ramp=2, support=8, inset=0):
    """oldrepublic:adapted_pool_element settings: the yard's stand level and clear_from = the template
    height (everything above the template's top is cleared, never the template itself)."""
    return dict(yard_stand=stand, clear_from=height, clear_inset=inset, ramp_rings=ramp, support_depth=support)


def jedi_ruin():
    W.pool('jedi_ruin/start', [
        W.element('jedi_ruin/courtyard', 'jedi_ruin_weathering', adapted=adapted(30, 4)),
        W.element('jedi_ruin/courtyard_2', 'jedi_ruin_weathering', adapted=adapted(30, 4)),
    ])
    W.pool('jedi_ruin/underground', [
        W.element('jedi_ruin/stair', 'jedi_ruin_weathering'),
        W.element('jedi_ruin/listening_hall', 'jedi_ruin_weathering'),
    ])
    W.pool('jedi_ruin/side', [W.element('jedi_ruin/anchor_cache', 'jedi_ruin_weathering')])
    W.pool('jedi_ruin/niche', [W.element('jedi_ruin/pilgrim_niche', 'jedi_ruin_weathering')])
    W.structure('jedi_ruin', start_pool='jedi_ruin/start', biome_tag='has_structure/jedi_ruin', size=5,
                max_distance=64, step='surface_structures', start_height=1 - 4)


def sith_tomb():
    W.pool('sith_tomb/start', [
        W.element('sith_tomb/gate', 'sith_tomb_weathering', adapted=adapted(30, 6)),
        W.element('sith_tomb/gate_2', 'sith_tomb_weathering', adapted=adapted(30, 6)),
    ])
    W.pool('sith_tomb/path', [W.element('sith_tomb/path', 'minecraft:empty', projection='terrain_matching')])
    W.pool('sith_tomb/side', [
        W.element('sith_tomb/dig_camp', 'sith_tomb_weathering', adapted=adapted(12, 1, ramp=2, support=3)),
        W.element('sith_tomb/rubble_room', 'sith_tomb_weathering'),
    ])
    W.pool('sith_tomb/underground', [
        W.element('sith_tomb/processional', 'sith_tomb_weathering'),
        W.element('sith_tomb/burial_chamber', 'sith_tomb_weathering'),
    ])
    W.pool('sith_tomb/niche', [W.element('sith_tomb/pilgrim_niche', 'sith_tomb_weathering')])
    W.structure('sith_tomb', start_pool='sith_tomb/start', biome_tag='has_structure/sith_tomb', size=4,
                max_distance=96, step='surface_structures', start_height=1 - 6)


def wreck_field():
    # hull_2 is the old, cold wreck: no weathering processor (it would scorch the plating, and scorched
    # plating smokes); the fresh hull keeps wreck_weathering
    W.pool('wreck_field/start', [
        W.element('wreck_field/hull', 'wreck_weathering', adapted=adapted(26, 4)),
        W.element('wreck_field/hull_2', 'minecraft:empty', adapted=adapted(26, 4)),
    ])
    W.pool('wreck_field/path', [W.element('wreck_field/path', 'minecraft:empty', projection='terrain_matching')])
    W.pool('wreck_field/side', [
        W.element('wreck_field/salvage_camp', 'wreck_weathering', adapted=adapted(12, 1, ramp=2, support=3)),
        W.element('wreck_field/scar', 'wreck_weathering', projection='terrain_matching'),
    ])
    W.structure('wreck_field', start_pool='wreck_field/start', biome_tag='has_structure/wreck_field', size=3,
                max_distance=64, step='surface_structures', start_height=1 - 4)


def camp_element(location):
    """oldrepublic:camp_pool_element (CampPoolElement: the yard at local y 5, cleared from y 13); W.element
    cannot write this element type."""
    return {'element': {'element_type': 'oldrepublic:camp_pool_element', 'location': 'oldrepublic:' + location,
                        'processors': 'minecraft:empty', 'projection': 'rigid'}, 'weight': 1}


def crystal_cave():
    W.pool('crystal_cave/camp', [camp_element('crystal_cave/camp'), camp_element('crystal_cave/camp_2')])
    W.pool('crystal_cave/descent', [W.element('crystal_cave/descent')])
    W.pool('crystal_cave/heart', [W.element('crystal_cave/heart', 'crystal_cave_weathering')])
    # the heart's skin is mixed in patches by the generator now: the processor only adds a little per-cave
    # variety (it used to pepper a tenth of all stone with tuff)
    W.processor_list('crystal_cave_weathering', [
        ('minecraft:stone', 0.04, 'minecraft:tuff'),
        ('minecraft:stone', 0.03, 'minecraft:andesite'),
        ('minecraft:stone', 0.02, 'oldrepublic:crystal_veined_stone'),
        ('minecraft:chiseled_stone_bricks', 0.2, 'minecraft:cracked_stone_bricks'),
        ('minecraft:deepslate_tiles', 0.08, 'minecraft:cracked_deepslate_tiles'),
    ])
    # the yard (local y 4) level with the ground: start_height = 1 - YARD_STAND (was -5, the yard sunk a block)
    W.structure('crystal_cave', start_pool='crystal_cave/camp', biome_tag='has_structure/crystal_cave', size=3,
                max_distance=96, step='underground_decoration', start_height=1 - 5)


FAMILIES = {'jedi_ruin': jedi_ruin, 'sith_tomb': sith_tomb, 'wreck_field': wreck_field, 'crystal_cave': crystal_cave}


def check(names):
    from structlib import jigsaw as JG
    bad = 0
    for n in names:
        args = JG.structure_args('oldrepublic:' + n)
        res = JG.assemble(**args)
        print(res.report())
        txt = res.report()
        if 'never fits' in txt or 'outside' in txt.lower():
            bad += 1
        print('%s: max_distance %d, the runs need %d' % (n, args['max_distance'], need(res)))
    return bad


def need(res):
    """The largest distance from the start box's centre to any piece box, over every run."""
    worst = 0
    for start, rot, pieces in res.runs:
        b = pieces[0].box
        cx = (b[0][0] + b[1][0] + 1) // 2
        cz = (b[0][2] + b[1][2] + 1) // 2
        cy = b[0][1] + 1
        for pc in pieces:
            (x0, y0, z0), (x1, y1, z1) = pc.box
            worst = max(worst, cx - x0, x1 - cx, cz - z0, z1 - cz, cy - y0, y1 - cy)
    return worst


def main(argv):
    names = [a for a in argv if not a.startswith('--')] or list(FAMILIES)
    for n in names:
        FAMILIES[n]()
    if '--check' in argv:
        sys.exit(1 if check(names) else 0)


if __name__ == '__main__':
    main(sys.argv[1:])
