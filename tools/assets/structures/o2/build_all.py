"""Builds everything O2 owns, one structure at a time: the two outposts and the five wilds
(homestead, watch_hermitage, war_scar, downed_fighter, jungle_shrine) with their worldgen files,
site anchors and meta, and loot tables. Every build is deterministic (seeded), so a rerun rewrites
the same bytes.

Run: python3 tools/assets/structures/o2/build_all.py [--preview DIR]
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = ['outposts.py', 'homestead.py', 'watch_hermitage.py', 'war_scar.py', 'downed_fighter.py', 'jungle_shrine.py']


def main():
    extra = sys.argv[1:]
    for name in SCRIPTS:
        print('==', name, flush=True)
        subprocess.check_call([sys.executable, os.path.join(HERE, name)] + extra)


if __name__ == '__main__':
    main()
