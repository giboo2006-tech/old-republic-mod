"""Rebuilds every QS2 template: the three echo shrines and the two ship sites, with their anchors and
site meta. Deterministic (running it twice writes byte-identical files); renders no previews.

    python3 tools/assets/structures/qs2/build_all.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, HERE)

import echo_shrines  # noqa: E402
import ship_sites  # noqa: E402


def main():
    echo_shrines.main(['jedi', 'sith', 'wayfarer'])
    ship_sites.main(['cradle', 'beacon_pad'])


if __name__ == '__main__':
    main()
