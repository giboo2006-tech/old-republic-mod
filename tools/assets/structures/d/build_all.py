#!/usr/bin/env python3
"""Rebuilds every template of worker D (docs/MILESTONE_6.md 5.8), one structure at a time:

  greater_tomb.py   greater_tomb/gate, descent, urn_vault, sealed_crypt and the greater_tombs worldgen
  reliquary.py      boss/reliquary_surface, boss/reliquary_hall
  hymnal.py         site/hymnal_arch, site/hymnal
  expeditions.py    expedition/singing_hollow_surface, singing_hollow_grotto, duskglass_rift_surface,
                    duskglass_rift_rift
  hunting_camp.py   boss/hunting_camp

Each writes its templates, anchor sidecars and site meta; the kept templates are rebuilt from their
pristine copies in base/, so a rerun writes byte-identical files. Previews are rendered only when the
environment variable D_PREVIEW names a (scratch) folder.

Usage: python3 tools/assets/structures/d/build_all.py [greater_tomb] [reliquary] [hymnal] [expeditions] [hunting_camp]
"""
import importlib
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ORDER = ['greater_tomb', 'reliquary', 'hymnal', 'expeditions', 'hunting_camp']


def main(argv):
    names = [n for n in ORDER if not argv or n in argv]
    for name in names:
        importlib.import_module(name).main()        # each save() prints its template's summary line


if __name__ == '__main__':
    main(sys.argv[1:])
