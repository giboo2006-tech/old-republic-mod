#!/usr/bin/env python3
"""Generates Chapter Two's small structure templates (gzipped structure NBT), PLACEHOLDER builds.

Deterministic: the same script always writes byte-identical files. Uses the NBT writer and
Template class of gen_structures.py. Writes src/main/resources/data/oldrepublic/structure/story/:

  jedi_camp     7 x 5 x 7   Master Saelen's camp: bedroll under a canvas lean-to, a low stone seat,
                            a lantern on a post, a training stump
  sith_camp     7 x 5 x 7   Overseer Sorn's camp: a dark field shelter, an Imperial supply crate,
                            a brazier (lit campfire), a weapon rack with a training staff
  raider_camp  13 x 6 x 13  Smoke on the Ridge: palisade stubs, two tents, crates, two lit campfires
                            (one a signal fire on hay, for the smoke column), three binding posts and a
                            smashed homestead cart

Rules shared with com.oldrepublic.quest.story.StoryStructures (keep both in step):
  * Layer y = 0 is the ground layer and sits one block below the site's stand height; the template
    is centred on the site (origin = stand - (size / 2, 1, size / 2)).
  * Cells that are not set are left as they are in the world (no air is written), so nothing but
    the camp itself is replaced. The game clears natural clutter above the footprint first.
  * Camps: the centre (3, 3) and the middle row's ends (0, 3) and (6, 3) stay free at y = 1..2:
    the chosen mentor stands in the middle, the Crossing mentors stood at the ends.
  * Raider camp, free at y = 1..2: crew (2,3) (10,3) (2,7) (11,6) (5,2) (8,2) (11,9), acolyte (7,5),
    captain (5,6), captives kneel at (3,9) (6,10) (9,9) in front of the posts (3,10) (6,11) (9,10).
  * No floor circles, sigils or rings.

Usage: python3 tools/assets/gen_structures_story.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_structures import Template, hash01, value_noise  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "data", "oldrepublic", "structure", "story")

FENCE_PROPS = {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"}
LANTERN = {"hanging": "false", "waterlogged": "false"}


def ground(t, seed, size, patches):
    """The ground layer: grass with patches (coarse dirt, path or gravel) chosen by noise."""
    for x in range(size):
        for z in range(size):
            n = value_noise(x, 0, z, 2.5, seed)
            name = "minecraft:grass_block"
            for limit, patch in patches:
                if n > limit:
                    name = patch
                    break
            t.set(x, 0, z, name, {"snowy": "false"} if name == "minecraft:grass_block" else None)


def build_jedi_camp():
    t = Template(7, 5, 7)
    ground(t, 7, 7, [(0.7, "minecraft:coarse_dirt"), (0.55, "minecraft:dirt_path")])

    # Canvas lean-to over a bedroll, against the north edge.
    for x in (0, 2):
        t.set(x, 1, 1, "minecraft:spruce_fence", FENCE_PROPS)
    for x in range(0, 3):
        for z in range(0, 2):
            t.set(x, 2, z, "minecraft:white_wool")
    t.set(0, 1, 0, "minecraft:spruce_planks")
    t.set(2, 1, 0, "minecraft:spruce_planks")
    t.set(1, 1, 0, "minecraft:light_gray_carpet")
    t.set(1, 1, 1, "minecraft:brown_carpet")

    # A low stone seat facing the middle.
    t.set(5, 1, 1, "minecraft:smooth_stone_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(4, 1, 1, "minecraft:stone_brick_slab", {"type": "bottom", "waterlogged": "false"})

    # Lantern on a post.
    t.set(5, 1, 5, "minecraft:spruce_fence", FENCE_PROPS)
    t.set(5, 2, 5, "minecraft:spruce_fence", FENCE_PROPS)
    t.set(5, 3, 5, "minecraft:lantern", LANTERN)

    # Training stump, scarred.
    t.set(1, 1, 5, "minecraft:stripped_oak_log", {"axis": "y"})
    t.set(1, 2, 5, "minecraft:oak_log", {"axis": "y"})
    return t


def build_sith_camp():
    t = Template(7, 5, 7)
    ground(t, 8, 7, [(0.62, "minecraft:gravel"), (0.5, "minecraft:coarse_dirt")])

    # Dark field shelter against the north edge.
    for x in (0, 2):
        t.set(x, 1, 1, "minecraft:dark_oak_fence", FENCE_PROPS)
        t.set(x, 1, 0, "minecraft:polished_deepslate")
    for x in range(0, 3):
        for z in range(0, 2):
            t.set(x, 2, z, "minecraft:black_wool")
    t.set(1, 1, 0, "minecraft:gray_carpet")

    # Imperial supply crate.
    t.set(5, 1, 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(4, 1, 1, "minecraft:polished_deepslate_slab", {"type": "bottom", "waterlogged": "false"})

    # Brazier: a lit campfire on dark stone.
    t.set(5, 0, 5, "minecraft:polished_blackstone")
    t.set(5, 1, 5, "minecraft:campfire", {"lit": "true", "facing": "north", "signal_fire": "false", "waterlogged": "false"})

    # Weapon rack with a training staff.
    t.set(0, 1, 5, "minecraft:dark_oak_fence", FENCE_PROPS)
    t.set(2, 1, 5, "minecraft:dark_oak_fence", FENCE_PROPS)
    for x in range(0, 3):
        t.set(x, 2, 5, "minecraft:stripped_dark_oak_log", {"axis": "x"})
    t.set(1, 1, 5, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    return t


def build_raider_camp():
    t = Template(13, 6, 13)
    seed = 13
    ground(t, seed, 13, [(0.66, "minecraft:coarse_dirt"), (0.5, "minecraft:dirt_path")])

    # Palisade stubs along the edges, sharpened with a fence tip here and there.
    stubs = [(0, 1, 2), (0, 4, 1), (0, 8, 2), (0, 11, 1), (12, 2, 2), (12, 5, 1), (12, 11, 2),
             (3, 0, 1), (8, 0, 2), (11, 0, 1), (1, 12, 2), (4, 12, 1), (11, 12, 1)]
    for (x, z, h) in stubs:
        for y in range(1, 1 + h):
            t.set(x, y, z, "minecraft:stripped_spruce_log", {"axis": "y"})
        if h == 2 and hash01(seed, x, z) > 0.4:
            t.set(x, 3, z, "minecraft:spruce_fence", FENCE_PROPS)

    # Two small tents (walls of canvas, a ridge above), open at both ends.
    def tent(x0, z0, z1, cloth, ridge):
        for z in range(z0, z1 + 1):
            t.set(x0, 1, z, cloth)
            t.set(x0 + 2, 1, z, cloth)
            t.set(x0 + 1, 2, z, ridge)
        t.set(x0 + 1, 1, z0, "minecraft:brown_carpet")

    tent(1, 4, 6, "minecraft:brown_wool", "minecraft:light_gray_wool")
    tent(9, 0, 2, "minecraft:light_gray_wool", "minecraft:brown_wool")

    # Crates of stolen goods.
    t.set(6, 1, 0, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(7, 1, 0, "minecraft:oak_planks")
    t.set(6, 2, 0, "minecraft:barrel", {"facing": "east", "open": "false"})
    t.set(4, 1, 0, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})

    # Two lit campfires: a signal fire on hay (the tall smoke column) and a cook fire.
    t.set(6, 0, 6, "minecraft:hay_block", {"axis": "y"})
    t.set(6, 1, 6, "minecraft:campfire", {"lit": "true", "facing": "south", "signal_fire": "true", "waterlogged": "false"})
    t.set(9, 1, 7, "minecraft:campfire", {"lit": "true", "facing": "west", "signal_fire": "false", "waterlogged": "false"})
    t.set(10, 1, 7, "minecraft:stripped_spruce_log", {"axis": "z"})

    # Three binding posts behind where the captives kneel.
    for (x, z) in ((3, 10), (6, 11), (9, 10)):
        t.set(x, 1, z, "minecraft:stripped_spruce_log", {"axis": "y"})
        t.set(x, 2, z, "minecraft:stripped_spruce_log", {"axis": "y"})
        t.set(x, 3, z, "minecraft:spruce_fence", FENCE_PROPS)

    # The landmark: a smashed homestead cart, its load spilled.
    for x in range(10, 13):
        t.set(x, 1, 12, "minecraft:oak_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(10, 1, 11, "minecraft:stripped_oak_log", {"axis": "x"})
    t.set(12, 1, 11, "minecraft:spruce_trapdoor", {"facing": "north", "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"})
    t.set(12, 2, 12, "minecraft:barrel", {"facing": "west", "open": "false"})
    t.set(8, 1, 12, "minecraft:hay_block", {"axis": "x"})
    return t


def main():
    os.makedirs(ROOT, exist_ok=True)
    for name, builder in (("jedi_camp", build_jedi_camp), ("sith_camp", build_sith_camp), ("raider_camp", build_raider_camp)):
        t = builder()
        n, p = t.save(os.path.join(ROOT, name + ".nbt"))
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries")


if __name__ == "__main__":
    main()
