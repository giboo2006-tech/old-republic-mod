#!/usr/bin/env python3
"""Builds every O3 wilds structure (docs/MILESTONE_6.md 5.2, second half), deterministically:

  smuggler_cove.py     wilds/smuggler_cove/{cove,arch}            salt 960150
  stilt_hamlet.py      wilds/stilt_hamlet/{reedholm,oakhollow}    salt 960151
  poacher_dig.py       wilds/poacher_dig/{cut,shaft}              salt 960152
  treaty_stone.py      wilds/treaty_stone/{court,avenue}          salt 960153
  sunken_freighter.py  wilds/sunken_freighter/{broken_back,nose_down}  salt 960154

Each writes its templates, site_anchors sidecars, start pool, structure, structure set, biome tag and loot
tables. One structure at a time, so a run stays light on a shared machine:
    python3 tools/assets/structures/o3/build_all.py [--preview <dir>] [--structures treaty_stone,poacher_dig]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, HERE)

import poacher_dig  # noqa: E402
import smuggler_cove  # noqa: E402
import stilt_hamlet  # noqa: E402
import sunken_freighter  # noqa: E402
import treaty_stone  # noqa: E402

MODULES = [('smuggler_cove', smuggler_cove), ('stilt_hamlet', stilt_hamlet), ('poacher_dig', poacher_dig),
           ('treaty_stone', treaty_stone), ('sunken_freighter', sunken_freighter)]


def main():
    pick = None
    if '--structures' in sys.argv:
        i = sys.argv.index('--structures')
        pick = set(sys.argv[i + 1].split(','))
        del sys.argv[i:i + 2]
    if '--only' in sys.argv:
        raise SystemExit('build_all builds whole structures: use --structures <name,...> (or run one script with --only)')
    for name, module in MODULES:
        if pick and name not in pick:
            continue
        module.main()


if __name__ == '__main__':
    main()
