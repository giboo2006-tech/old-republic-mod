#!/usr/bin/env python3
"""Generates the Milestone 3 game test fixtures (gzipped structure NBT) and their anchor sidecars.

Deterministic: the same script always writes byte-identical files. Uses the NBT writer and Template
class of gen_structures.py. Fixtures are small test stand-ins built from vanilla blocks only; they
are never shipped in the mod jar (they live in the gametest source set).

Writes src/gametest/resources/data/oldrepublic_test/:

  structure/arena_24x7x24.nbt   24 x 7 x 24  boss fight arena: a floor, four pylon stand-ins
                                             (amethyst on lodestone), a 3 x 3 dais and two cover
                                             columns
  structure/urn_fixture.nbt     11 x 4 x 11  urn puzzle: a keeper (lodestone) and four urns
                                             (decorated pots), a sentinel spot
  structure/seal_fixture.nbt     7 x 5 x 5   a wall with a 1 x 2 doorway (the seal) and a
                                             counterweight stand-in
  structure/pedestal_fixture.nbt 7 x 4 x 7   a pedestal stand-in, a guard spot and a sentinel spot
  structure/terminal_fixture.nbt 15 x 4 x 15 a bounty terminal stand-in, the officer's and four
                                             garrison spots
  structure/anchor_fixture.nbt   5 x 3 x 7   asymmetric marker blocks for the rotation test
  site_anchors/<name>.json                   {"<ANCHOR>": [[x, y, z], ...]}, template-local

Rules (frozen in docs/MILESTONE_3.md 15.1):
  * Every fixture is at most 7 blocks high (game tests run them with skyAccess = true).
  * Layer y = 0 is a floor; every other cell is written (air included), so a fixture replaces what
    was there.
  * Anchors are sidecars, not blocks: an anchor cell holds the real stand-in block (or air for a
    spot where something spawns or stands).

Usage: python3 tools/assets/gen_test_fixtures_m3.py
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_structures import AIR, Template  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "gametest", "resources", "data", "oldrepublic_test")
MAX_HEIGHT = 7

FLOOR = "minecraft:smooth_stone"


def base(sx, sy, sz, floor=FLOOR):
    """A fixture with a floor at y = 0 and explicit air above it."""
    t = Template(sx, sy, sz)
    t.fill(0, 0, 0, sx - 1, 0, sz - 1, floor)
    t.fill(0, 1, 0, sx - 1, sy - 1, sz - 1, AIR)
    return t


def arena():
    t = base(24, 7, 24, "minecraft:polished_andesite")
    anchors = {}
    # The dais: a 3 x 3 platform at the centre; the boss stands on it.
    t.fill(11, 1, 11, 13, 1, 13, "minecraft:polished_blackstone")
    anchors["DAIS"] = [(12, 2, 12)]
    # Four pylon stand-ins on lodestone plinths; the anchor is the pylon block.
    for i, (x, z) in enumerate(((5, 5), (18, 5), (18, 18), (5, 18)), start=1):
        t.set(x, 1, z, "minecraft:lodestone")
        t.set(x, 2, z, "minecraft:amethyst_block")
        anchors[f"PYLON_{i}"] = [(x, 2, z)]
    # The way in, on the north edge.
    anchors["THRESHOLD"] = [(12, 1, 1)]
    # Two cover columns, floor to top; the anchor is the column's lowest block.
    for i, (x, z) in enumerate(((8, 16), (16, 16)), start=1):
        t.fill(x, 1, z, x, 6, z, "minecraft:stone_bricks")
        anchors[f"COLUMN_{i}"] = [(x, 1, z)]
    return t, anchors


def urns():
    t = base(11, 4, 11, "minecraft:polished_blackstone_bricks")
    anchors = {"URN_KEEPER": [(5, 1, 5)]}
    t.set(5, 1, 5, "minecraft:lodestone")
    for i, (x, z) in enumerate(((2, 2), (8, 2), (8, 8), (2, 8)), start=1):
        t.set(x, 1, z, "minecraft:decorated_pot", {"facing": "north", "cracked": "false", "waterlogged": "false"})
        anchors[f"URN_{i}"] = [(x, 1, z)]
    anchors["SENTINEL_1"] = [(5, 1, 9)]
    return t, anchors


def seal():
    t = base(7, 5, 5, "minecraft:stone_bricks")
    # A wall across z = 2 with a 1 x 2 doorway at x = 3; the seal anchor is the doorway's lower cell.
    t.fill(0, 1, 2, 6, 4, 2, "minecraft:stone_bricks")
    t.fill(3, 1, 2, 3, 2, 2, AIR)
    t.set(5, 1, 0, "minecraft:polished_andesite")
    return t, {"DOOR_SEAL": [(3, 1, 2)], "COUNTERWEIGHT": [(5, 1, 0)]}


def pedestal():
    t = base(7, 4, 7)
    t.set(3, 1, 3, "minecraft:chiseled_stone_bricks")
    return t, {"PEDESTAL": [(3, 1, 3)], "GUARD": [(3, 1, 6)], "SENTINEL_1": [(0, 1, 0)]}


def terminal():
    t = base(15, 4, 15, "minecraft:smooth_stone")
    t.set(7, 1, 7, "minecraft:cartography_table")
    anchors = {"TERMINAL": [(7, 1, 7)], "OFFICER": [(7, 1, 9)]}
    for i, (x, z) in enumerate(((4, 4), (10, 4), (10, 10), (4, 10)), start=1):
        anchors[f"GARRISON_{i}"] = [(x, 1, z)]
    return t, anchors


def anchor_test():
    """No two markers share an axis value pattern, so every rotation moves each one differently."""
    t = base(5, 3, 7)
    t.set(1, 1, 5, "minecraft:gold_block")
    t.set(3, 1, 1, "minecraft:diamond_block")
    t.set(4, 2, 6, "minecraft:emerald_block")
    t.set(0, 1, 0, "minecraft:lapis_block")
    t.set(2, 1, 3, "minecraft:lapis_block")
    return t, {"HEART": [(1, 1, 5)], "PEDESTAL": [(3, 1, 1)], "GUARD": [(4, 2, 6)], "CHEST_1": [(0, 1, 0), (2, 1, 3)]}


FIXTURES = (
    ("arena_24x7x24", arena),
    ("urn_fixture", urns),
    ("seal_fixture", seal),
    ("pedestal_fixture", pedestal),
    ("terminal_fixture", terminal),
    ("anchor_fixture", anchor_test),
)


def check(name, t, anchors):
    sx, sy, sz = t.size
    if sy > MAX_HEIGHT:
        raise SystemExit(f"{name}: {sy} blocks high, at most {MAX_HEIGHT}")
    for anchor, cells in anchors.items():
        for cell in cells:
            if not t.inside(*cell):
                raise SystemExit(f"{name}: anchor {anchor} at {cell} is outside {t.size}")


def sidecar(path, anchors):
    text = "{\n" + ",\n".join(
        f'  "{anchor}": [' + ", ".join(f"[{x}, {y}, {z}]" for (x, y, z) in cells) + "]"
        for anchor, cells in anchors.items()) + "\n}\n"
    json.loads(text)  # well-formed
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def main():
    os.makedirs(os.path.join(ROOT, "structure"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "site_anchors"), exist_ok=True)
    for name, builder in FIXTURES:
        t, anchors = builder()
        check(name, t, anchors)
        n, p = t.save(os.path.join(ROOT, "structure", name + ".nbt"))
        sidecar(os.path.join(ROOT, "site_anchors", name + ".json"), anchors)
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries, anchors {', '.join(anchors)}")


if __name__ == "__main__":
    main()
