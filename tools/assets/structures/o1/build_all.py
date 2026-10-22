"""Build every template of worker O1 (the overworld landmarks), then the worldgen that places them.

    python3 tools/assets/structures/o1/build_all.py [--only NAME] [--preview DIR] [--check]

    jedi_ruin.py     courtyard, courtyard_2, stair, listening_hall, pilgrim_niche, anchor_cache
    sith_tomb.py     gate, gate_2, path, dig_camp, rubble_room, processional, burial_chamber, pilgrim_niche
    wreck_field.py   hull, hull_2, path, salvage_camp, scar
    crystal_cave.py  camp, camp_2, descent, heart
    worldgen_o1.py   the pools, the four structures (ids, sets and salts unchanged), crystal_cave_weathering

Deterministic: a rerun writes byte-identical files. The pieces reworked in place (the listening hall, the
processional, the burial chamber, the wreck's puzzle, the old camp's poachers, the descent, the heart) are
read from the pristine copies in base/, never from the repository. --check also assembles every structure
(every start variant and rotation) with structlib.jigsaw, walks the composed jedi ruin and crystal cave
routes and counts each landmark's harvestable resources (lumen moss, funerary urns, wreck salvage) against
spec 3.2. Previews go to --preview (a scratch folder).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import common as C  # noqa: E402
import jedi_ruin  # noqa: E402
import sith_tomb  # noqa: E402
import wreck_field  # noqa: E402
import crystal_cave  # noqa: E402
import worldgen_o1  # noqa: E402


# the harvestable resources each landmark holds (MILESTONE_3 spec 3.2): (resource, the pieces every run
# places besides its start, how many times each, the start variants, the range per structure)
RESOURCES = [
    ('jedi_ruin', 'or:lumen_moss', {'jedi_ruin/stair': 1, 'jedi_ruin/listening_hall': 1,
                                    'jedi_ruin/pilgrim_niche': 1, 'jedi_ruin/anchor_cache': 1},
     ('jedi_ruin/courtyard', 'jedi_ruin/courtyard_2'), (10, 14)),
    ('sith_tomb', 'or:funerary_urn', {'sith_tomb/processional': 1, 'sith_tomb/burial_chamber': 1},
     ('sith_tomb/gate', 'sith_tomb/gate_2'), (8, 10)),
    ('sith_tomb rubble room', 'or:funerary_urn', {'sith_tomb/rubble_room': 1}, (), (2, 2)),
    ('wreck_field', 'or:wreck_salvage', {'wreck_field/path': 1, 'wreck_field/salvage_camp': 1, 'wreck_field/scar': 2},
     ('wreck_field/hull', 'wreck_field/hull_2'), (6, 8)),
    ('crystal_cave', 'or:lumen_moss', {'crystal_cave/descent': 1, 'crystal_cave/heart': 1},
     ('crystal_cave/camp', 'crystal_cave/camp_2'), (0, 0)),
]


def resource_check():
    """Count the resources of every structure, per start variant, against the spec's range."""
    bad = 0
    for label, block, pieces, starts, (lo, hi) in RESOURCES:
        rest = sum(n * C.resource_count(C.load_saved(tid), block) for tid, n in pieces.items())
        for start in starts or (None,):
            total = rest + (C.resource_count(C.load_saved(start), block) if start else 0)
            ok = lo <= total <= hi
            bad += 0 if ok else 1
            print('%s %s (%s): %s %d, the spec wants %d to %d' % ('RESOURCES' if ok else 'CHECK RESOURCES', label,
                                                               start or 'the room', block, total, lo, hi))
    return bad


def main(argv):
    check = '--check' in argv
    a = C.args([v for v in argv if v != '--check'])
    for family in (jedi_ruin, sith_tomb, wreck_field, crystal_cave):
        family.build(a)
    names = list(worldgen_o1.FAMILIES)
    for n in names:
        worldgen_o1.FAMILIES[n]()
    if check:
        bad = worldgen_o1.check(names)
        bad += resource_check()
        jedi_ruin.structure_check()           # the courtyard's doorway, the stair, the hall and the cache, composed
        bad += len(crystal_cave.structure_check())   # the tunnel, the heart, the seal, the stone and the chest
        sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main(sys.argv[1:])
