#!/usr/bin/env python3
"""Generates the ship templates (Milestone 4, W3; spec MILESTONE_4.md 4.2, 4.8): the cradle, the yard
where a player's Chapter Four hulk is laid up (placed at runtime by Ships.placeHulk through
RuntimeSites), and the beacon pad, the tamped ground a landing beacon lays over its home pad
(LandingBeaconBlock.layPad).

Deterministic: the same script always writes byte-identical files (the NBT writer and Template class
of gen_structures.py, the Site helpers of gen_structures_m3.py, fixed seeds). Writes under
src/main/resources/data/oldrepublic/:

  structure/ship/cradle.nbt           17 x 7 x 21, 1 layer buried
  site_anchors/ship/cradle.json       HEART: where the hulk stands (its body's feet cell)
  structure/ship/beacon_pad.nbt       11 x 3 x 11, 1 layer buried
  site_anchors/ship/beacon_pad.json   HEART: where the ship lands (the pad's stand)

The cradle: a packed-earth yard, a work lamp on a post at each end, a lean-to of patched canvas over
a workbench and an anvil, crates and barrels of tools, timber cribbing stacked by the wings and a
coil of chain. The hulk itself brings its own tarp and timber jacks (the model, removed as it stands
up), so the yard keeps the ship's whole footprint clear: nothing is built within the box the model
and its 7 x 4 x 7 body occupy (CLEAR below), checked when the file is written.

The beacon pad: only its ground layer, a patchwork of stone and andesite tamped flat with gravel in
the joints and a coarse-dirt margin. Every block is natural ground (the #story_ground tag, none in
#built_blocks), so the area still passes the beacon's check when the beacon is set there again, and
nothing above the ground is written (the placement clears plants itself and never cuts a tree).

Template-local coordinates as in gen_structures_m3.py: x east, y up, z south; the entrance is the
local north side (z = 0), where the ship's tail and ramp face; the nose points south (+z), which is
yaw 0 at rotation NONE (Ships.yawOf). No floor circles, sigils or glowing runes.

Usage: python3 tools/assets/gen_structures_ship_m4.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_structures_m3 as m3  # noqa: E402
from gen_structures import AIR  # noqa: E402
from gen_structures_m3 import COARSE, DIRT, GRASS, LANTERN, STONE, Site, check_template, clear_above, noise, sidecar  # noqa: E402

DATA = m3.DATA
SIZE = (17, 7, 21)
BURIED = 1
HEART = (8, 1, 10)
# The model's footprint around HEART (about 9 x 11 blocks, 5 high) plus a block of margin.
CLEAR = ((3, 1, 3), (13, 5, 17))

PAD_SIZE = (11, 3, 11)
PAD_BURIED = 1
PAD_HEART = (5, 1, 5)
ANDESITE = "minecraft:andesite"
# Natural ground only (#story_ground): the beacon's area check must pass on the pad again.
PAD_BLOCKS = {STONE, "minecraft:andesite", "minecraft:gravel", COARSE}

GRAVEL = "minecraft:gravel"
PATH = "minecraft:dirt_path"
PACKED_MUD = "minecraft:packed_mud"
FENCE = "minecraft:spruce_fence"
FENCE_PROPS = {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"}
LOG = "minecraft:stripped_spruce_log"
BARREL = "minecraft:barrel"
WOOL_PALE = "minecraft:light_gray_wool"
WOOL_PATCH = "minecraft:brown_wool"
WOOL_DARK = "minecraft:gray_wool"


def ground(t):
    """Packed earth worn into the grass: gravel and mud under the ship, paths at the entrance."""
    sx, _, sz = t.size
    for x in range(sx):
        for z in range(sz):
            r = noise(301, x, 0, z)
            under = CLEAR[0][0] <= x <= CLEAR[1][0] and CLEAR[0][2] <= z <= CLEAR[1][2]
            if under:
                block = PACKED_MUD if r > 0.62 else (GRAVEL if r > 0.38 else COARSE)
            elif z <= 2 and 6 <= x <= 10:
                block = PATH
            else:
                block = GRASS if r > 0.45 else (COARSE if r > 0.2 else DIRT)
            t.set(x, 0, z, block)


def lamp_post(t, x, z):
    """A work lamp: a fence post three high with a lantern on top."""
    for y in range(1, 4):
        t.set(x, y, z, FENCE, FENCE_PROPS)
    t.set(x, 4, z, "minecraft:lantern", LANTERN)


def lean_to(t):
    """Patched canvas on four posts over a crafting table, an anvil and a grindstone (east side, aft)."""
    x0, x1, z0, z1 = 14, 16, 2, 6
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(1, 4):
            t.set(x, y, z, FENCE, FENCE_PROPS)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            r = noise(311, x, 4, z)
            t.set(x, 4, z, WOOL_PATCH if r > 0.7 else (WOOL_DARK if r < 0.2 else WOOL_PALE))
    t.set(15, 1, 3, "minecraft:crafting_table")
    t.set(15, 1, 5, "minecraft:chipped_anvil", {"facing": "south"})
    t.set(16, 1, 4, "minecraft:grindstone", {"face": "floor", "facing": "west"})


def crates(t):
    """Tool crates and barrels on the west side, by the nose."""
    for x, y, z in ((0, 1, 13), (0, 1, 14), (1, 1, 14), (0, 2, 14)):
        t.set(x, y, z, BARREL, {"facing": "up", "open": "false"})
    for x, y, z in ((1, 1, 16), (0, 1, 16), (0, 1, 17)):
        t.set(x, y, z, "minecraft:spruce_planks" if (x + z) % 2 else "minecraft:oak_planks")
    t.set(1, 1, 13, "minecraft:smithing_table")
    t.set(0, 1, 11, "minecraft:chain", {"axis": "x", "waterlogged": "false"})
    t.set(1, 1, 11, "minecraft:chain", {"axis": "x", "waterlogged": "false"})


def cribbing(t):
    """Timber cribbing stacked beside the wings: logs laid crosswise, two courses."""
    for x in (0, 1):
        for z in range(7, 10):
            t.set(x, 1, z, LOG, {"axis": "z"})
    for z in (7, 9):
        t.set(0, 2, z, LOG, {"axis": "x"})
        t.set(1, 2, z, LOG, {"axis": "x"})
    for x in (15, 16):
        for z in range(11, 14):
            t.set(x, 1, z, LOG, {"axis": "z"})
    t.set(15, 2, 12, LOG, {"axis": "x"})
    t.set(16, 2, 12, LOG, {"axis": "x"})


def build_cradle():
    t = Site("ship/cradle", SIZE, BURIED)
    ground(t)
    lamp_post(t, 2, 1)
    lamp_post(t, 14, 19)
    lean_to(t)
    crates(t)
    cribbing(t)
    t.spot("HEART", *HEART)
    clear_above(t, 1)
    return t


def build_beacon_pad():
    """The home pad: tamped stone and andesite flags with gravel joints, a coarse-dirt margin."""
    t = Site("ship/beacon_pad", PAD_SIZE, PAD_BURIED)
    sx, _, sz = t.size
    for x in range(sx):
        for z in range(sz):
            edge = x in (0, sx - 1) or z in (0, sz - 1)
            r = noise(331, x, 0, z)
            if edge:
                block = COARSE if r > 0.3 else GRAVEL
            elif (x + 1) % 3 == 0 and (z + 1) % 3 == 0 and r > 0.45:
                block = GRAVEL
            else:
                block = ANDESITE if noise(337, x // 3, 0, z // 3) > 0.5 else STONE
            t.set(x, 0, z, block)
    t.spot("HEART", *PAD_HEART)
    return t


def check_pad(t):
    for (x, y, z), (name, _, _) in t.cells.items():
        if y == 0 and name not in PAD_BLOCKS:
            raise SystemExit(f"{t.name}: {name} at {(x, y, z)} is not natural ground")
        if y > 0 and name != AIR:
            raise SystemExit(f"{t.name}: {name} at {(x, y, z)} is above the ground layer")


def check_clear(t):
    (x0, y0, z0), (x1, y1, z1) = CLEAR
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if t.get(x, y, z) != AIR:
                    raise SystemExit(f"{t.name}: {t.get(x, y, z)} at {(x, y, z)} is inside the ship's footprint")


def main():
    t = build_cradle()
    check_template(t, SIZE, ["HEART"])
    check_clear(t)
    n, p = t.save(os.path.join(DATA, "structure", "ship", "cradle.nbt"))
    sidecar(os.path.join(DATA, "site_anchors", "ship", "cradle.json"), {"HEART": t.anchors["HEART"]})
    print(f"{t.name}: size {t.size}, {n} blocks, {p} palette entries, anchors HEART")

    pad = build_beacon_pad()
    check_template(pad, PAD_SIZE, ["HEART"])
    check_pad(pad)
    n, p = pad.save(os.path.join(DATA, "structure", "ship", "beacon_pad.nbt"))
    sidecar(os.path.join(DATA, "site_anchors", "ship", "beacon_pad.json"), {"HEART": pad.anchors["HEART"]})
    print(f"{pad.name}: size {pad.size}, {n} blocks, {p} palette entries, anchors HEART")


if __name__ == "__main__":
    main()
