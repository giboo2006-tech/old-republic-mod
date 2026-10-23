#!/usr/bin/env python3
"""Builds every QS story site (docs/MILESTONE_6.md 3.5 and 5.3), deterministically:

  camps.py          story/jedi_camp, story/sith_camp            (7 x 5 x 7, kept)
  crossing.py       story/crossing, story/jedi_encampment, story/sith_encampment
  raider_camp.py    story/raider_camp
  couriers_fall.py  story/couriers_fall

Each writes its templates, site_anchors sidecars and site meta. Run from anywhere:
    python3 tools/assets/structures/qs/build_all.py [--preview <dir>]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True
sys.path.insert(0, HERE)

import camps  # noqa: E402
import couriers_fall  # noqa: E402
import crossing  # noqa: E402
import raider_camp  # noqa: E402


def main(argv):
    for module in (camps, crossing, raider_camp, couriers_fall):
        module.main(argv)


if __name__ == '__main__':
    main(sys.argv[1:])
