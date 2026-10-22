#!/usr/bin/env python3
"""Generates the crystal_cave structure templates (gzipped structure NBT).

Deterministic: the same script always writes byte-identical files (fixed seeds, fixed gzip mtime).
Writes src/main/resources/data/oldrepublic/structure/crystal_cave/{camp,descent,heart}.nbt.

Pieces (template-local coordinates: x east, y up, z south), see docs/design/world.md:
  camp     25 x 16 x 25  crystal-poacher dig camp on the surface, shaft opening at its centre
  descent   9 x 30 x 32  ladder shaft then a stepped tunnel sloping down 23 blocks
  heart    45 x 22 x 48  main cavern, unstable passage, hidden chamber, sealed trial chamber

Jigsaw chain: camp (12,0,12) down -> descent (4,29,4) up; descent (4,1,31) south -> heart (22,1,0) north.

Usage: python3 tools/assets/gen_structures.py
"""

import gzip
import io
import math
import os
import struct

DATA_VERSION = 4903  # Minecraft 26.2 world_version
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "data", "oldrepublic", "structure", "crystal_cave")
DATA = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "data", "oldrepublic"))

# Files under DATA that Milestone 6 rebuilt with the generators under tools/assets/structures/ (docs/MILESTONE_6.md).
# The older generators built on this module skip them, so running one does not put an old template back.
M6_REBUILT = (
    "structure/mission/", "site_anchors/mission/",
    "structure/story/", "site_anchors/story/",
    "structure/site/echo_shrine_", "site_anchors/site/echo_shrine_",
    "structure/ship/cradle.nbt", "site_anchors/ship/cradle.json", "structure/ship/beacon_pad.nbt", "site_anchors/ship/beacon_pad.json",
    "structure/hub/", "site_anchors/hub/",
    "structure/outpost/", "site_anchors/outpost/", "worldgen/structure/republic_outpost.json", "worldgen/structure/imperial_outpost.json",
    "worldgen/template_pool/republic_outpost/", "worldgen/template_pool/imperial_outpost/", "worldgen/structure_set/outposts.json",
    "structure/site/detention_post.nbt", "site_anchors/site/detention_post.json",
    "structure/site/verse_stone.nbt", "site_anchors/site/verse_stone.json",
    "structure/planet/hymn_shrine.nbt", "site_anchors/planet/hymn_shrine.json", "worldgen/structure/hymn_shrine.json",
    "worldgen/template_pool/hymn_shrine/", "worldgen/structure_set/hymn_shrines.json",
    "structure/planet/mire_grave.nbt", "site_anchors/planet/mire_grave.json", "worldgen/structure/mire_grave.json",
    "worldgen/template_pool/mire_grave/", "worldgen/structure_set/mire_graves.json",
    "structure/planet/crashed_freighter.nbt", "site_anchors/planet/crashed_freighter.json", "worldgen/structure/crashed_freighter.json",
    "worldgen/template_pool/crashed_freighter/", "worldgen/structure_set/crashed_freighters.json",
    "structure/planet/kajidic_post.nbt", "site_anchors/planet/kajidic_post.json", "worldgen/structure/kajidic_post.json",
    "worldgen/template_pool/kajidic_post/", "worldgen/structure_set/kajidic_posts.json",
    "structure/site/debtor_camp.nbt", "site_anchors/site/debtor_camp.json",
    "structure/planet/waystone.nbt", "site_anchors/planet/waystone.json",
    "structure/planet/waystone_corrupted.nbt", "site_anchors/planet/waystone_corrupted.json",
    "structure/planet/heartstone.nbt", "site_anchors/planet/heartstone.json",
    "structure/planet/wayfarer_cache.nbt", "site_anchors/planet/wayfarer_cache.json",
    "worldgen/template_pool/waystone/", "worldgen/structure/waystone.json", "worldgen/structure_set/waystones.json",
    "tags/worldgen/biome/has_structure/waystone.json",
)


def rebuilt_in_m6(path):
    """True (and says so) when ``path`` is a file that a Milestone 6 generator writes now, so an old one must skip it."""
    rel = os.path.relpath(os.path.abspath(path), DATA).replace(os.sep, "/")
    if rel.startswith(M6_REBUILT):
        print(f"skipped {rel}: rebuilt in Milestone 6 (tools/assets/structures/)")
        return True
    return False

# ---------------------------------------------------------------------------------------------
# Minimal NBT writer
# ---------------------------------------------------------------------------------------------
TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_STRING, TAG_LIST, TAG_COMPOUND = 8, 9, 10


class Byte(int):
    pass


class Int(int):
    pass


class Double(float):
    pass


class TagList(list):
    """A list with an explicit element tag type."""

    def __init__(self, tag_type, items=()):
        super().__init__(items)
        self.tag_type = tag_type


def _tag_type(value):
    if isinstance(value, Byte):
        return TAG_BYTE
    if isinstance(value, bool):
        return TAG_BYTE
    if isinstance(value, Int) or isinstance(value, int):
        return TAG_INT
    if isinstance(value, (Double, float)):
        return TAG_DOUBLE
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, TagList):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError(f"Unsupported NBT value {value!r}")


def _write_string(out, s):
    data = s.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _write_payload(out, tag, value):
    if tag == TAG_BYTE:
        out.write(struct.pack(">b", int(value)))
    elif tag == TAG_INT:
        out.write(struct.pack(">i", int(value)))
    elif tag == TAG_DOUBLE:
        out.write(struct.pack(">d", float(value)))
    elif tag == TAG_STRING:
        _write_string(out, value)
    elif tag == TAG_LIST:
        elem = value.tag_type if value else TAG_END
        out.write(struct.pack(">bi", elem, len(value)))
        for item in value:
            _write_payload(out, elem, item)
    elif tag == TAG_COMPOUND:
        for key in sorted(value.keys()):
            item = value[key]
            t = _tag_type(item)
            out.write(struct.pack(">b", t))
            _write_string(out, key)
            _write_payload(out, t, item)
        out.write(struct.pack(">b", TAG_END))
    else:
        raise TypeError(tag)


def write_nbt(path, root):
    raw = io.BytesIO()
    raw.write(struct.pack(">b", TAG_COMPOUND))
    _write_string(raw, "")
    _write_payload(raw, TAG_COMPOUND, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0, filename="") as gz:
            gz.write(raw.getvalue())


# ---------------------------------------------------------------------------------------------
# Deterministic noise
# ---------------------------------------------------------------------------------------------
def hash01(*args):
    h = 2166136261
    for a in args:
        h ^= (int(a) & 0xFFFFFFFF)
        h = (h * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 0x5bd1e995) & 0xFFFFFFFF
        h ^= h >> 15
    return h / 0xFFFFFFFF


def value_noise(x, y, z, scale, seed):
    fx, fy, fz = x / scale, y / scale, z / scale
    x0, y0, z0 = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - x0, fy - y0, fz - z0

    def s(t):
        return t * t * (3 - 2 * t)

    tx, ty, tz = s(tx), s(ty), s(tz)
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                acc += w * hash01(seed, x0 + dx, y0 + dy, z0 + dz)
    return acc  # 0..1


# ---------------------------------------------------------------------------------------------
# Template builder
# ---------------------------------------------------------------------------------------------
AIR = "minecraft:air"
CAVE_AIR = "minecraft:cave_air"
STONE = "minecraft:stone"


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.cells = {}  # (x,y,z) -> (name, props tuple, nbt or None)
        self.entities = []

    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, name, props=None, nbt=None):
        if not self.inside(x, y, z):
            return
        self.cells[(x, y, z)] = (name, tuple(sorted((props or {}).items())), nbt)

    def get(self, x, y, z):
        c = self.cells.get((x, y, z))
        return c[0] if c else None

    def is_air(self, x, y, z):
        return self.get(x, y, z) in (AIR, CAVE_AIR)

    def is_open(self, x, y, z):
        """Air or a non-solid decoration (anything that is not rock)."""
        n = self.get(x, y, z)
        return n is not None and n not in ROCK_LIKE

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def shell(self, thickness, rock_fn):
        """Puts rock around every air cell within `thickness` blocks, where no cell is set yet."""
        open_cells = [p for p, c in self.cells.items() if c[0] in (AIR, CAVE_AIR)]
        for (x, y, z) in open_cells:
            for dx in range(-thickness, thickness + 1):
                for dy in range(-thickness, thickness + 1):
                    for dz in range(-thickness, thickness + 1):
                        p = (x + dx, y + dy, z + dz)
                        if p not in self.cells and self.inside(*p):
                            self.set(*p, rock_fn(*p))

    def entity(self, x, y, z, nbt):
        self.entities.append({
            "pos": TagList(TAG_DOUBLE, [Double(x + 0.5), Double(float(y)), Double(z + 0.5)]),
            "blockPos": TagList(TAG_INT, [Int(x), Int(y), Int(z)]),
            "nbt": nbt,
        })

    def jigsaw(self, x, y, z, orientation, name, target, pool, joint, final_state=AIR):
        self.set(x, y, z, "minecraft:jigsaw", {"orientation": orientation}, {
            "id": "minecraft:jigsaw",
            "name": name,
            "target": target,
            "pool": pool,
            "joint": joint,
            "final_state": final_state,
            "placement_priority": Int(0),
            "selection_priority": Int(0),
        })

    def save(self, path):
        if rebuilt_in_m6(path):
            return 0, 0
        palette = []
        index = {}
        blocks = TagList(TAG_COMPOUND)
        for pos in sorted(self.cells.keys(), key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.cells[pos]
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = {k: str(v) for k, v in props}
                palette.append(entry)
            block = {"pos": TagList(TAG_INT, [Int(pos[0]), Int(pos[1]), Int(pos[2])]), "state": Int(index[key])}
            if nbt is not None:
                block["nbt"] = nbt
            blocks.append(block)
        root = {
            "DataVersion": Int(DATA_VERSION),
            "size": TagList(TAG_INT, [Int(v) for v in self.size]),
            "palette": TagList(TAG_COMPOUND, palette),
            "blocks": blocks,
            "entities": TagList(TAG_COMPOUND, self.entities),
        }
        write_nbt(path, root)
        return len(blocks), len(palette)


ROCK_LIKE = {
    STONE, "minecraft:tuff", "minecraft:andesite", "minecraft:calcite", "minecraft:smooth_basalt",
    "minecraft:deepslate", "minecraft:polished_deepslate", "minecraft:deepslate_tiles", "minecraft:chiseled_deepslate",
    "minecraft:chiseled_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:stone_bricks", "minecraft:chiseled_tuff",
    "minecraft:chiseled_tuff_bricks", "minecraft:tuff_bricks", "minecraft:polished_tuff", "minecraft:dirt", "minecraft:coarse_dirt",
    "minecraft:grass_block", "minecraft:gravel", "minecraft:cobblestone", "minecraft:dirt_path", "oldrepublic:crystal_veined_stone",
    "minecraft:mossy_cobblestone",
}

KYBER = "oldrepublic:kyber_cluster"
UNSTABLE = "oldrepublic:unstable_crystal_cluster"
VEINED = "oldrepublic:crystal_veined_stone"
SEAL = "oldrepublic:resonant_seal"
MEDITATION = "oldrepublic:meditation_stone"
POACHER = {"id": "oldrepublic:crystal_poacher", "PersistenceRequired": Byte(1)}


def cave_rock(seed):
    def fn(x, y, z):
        n = value_noise(x, y, z, 4.0, seed)
        r = hash01(seed + 7, x, y, z)
        if r < 0.03:
            return VEINED
        if n > 0.72:
            return "minecraft:tuff"
        if n < 0.2:
            return "minecraft:andesite"
        return STONE
    return fn


def ladder_shaft(t, cx, cz, y0, y1, sides=("north", "south", "east", "west")):
    """3x3 open shaft centred on (cx, cz) with ladders on the given walls, y0..y1 inclusive.

    The pieces above and below may be rotated independently (rollable jigsaw), so ladders are put on
    several walls to make sure at least one ladder column always lines up.
    """
    ladders = {"north": ((0, -1), "south"), "south": ((0, 1), "north"), "west": ((-1, 0), "east"), "east": ((1, 0), "west")}
    for y in range(y0, y1 + 1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                t.set(cx + dx, y, cz + dz, AIR)
        for side in sides:
            (dx, dz), facing = ladders[side]
            t.set(cx + dx, y, cz + dz, "minecraft:ladder", {"facing": facing, "waterlogged": "false"})


# ---------------------------------------------------------------------------------------------
# Camp
# ---------------------------------------------------------------------------------------------
def build_camp():
    t = Template(25, 16, 25)
    seed = 101
    # Pit and clear air first so later fills do not overwrite them.
    ladder_shaft(t, 12, 12, 0, 4)
    t.jigsaw(12, 0, 12, "down_south", "oldrepublic:camp_bottom", "oldrepublic:descent_top",
             "oldrepublic:crystal_cave/descent", "rollable")

    # Foundation: stone core, dirt, a yard of grass with coarse dirt and path patches on top.
    for x in range(25):
        for z in range(25):
            for y in range(5):
                if (x, y, z) in t.cells:
                    continue
                if y <= 2:
                    t.set(x, y, z, STONE if hash01(seed, x, y, z) > 0.1 else "minecraft:andesite")
                elif y == 3:
                    t.set(x, y, z, "minecraft:dirt")
                else:
                    edge = min(x, z, 24 - x, 24 - z)
                    n = value_noise(x, 0, z, 3.0, seed)
                    if edge >= 3 and n > 0.55:
                        t.set(x, y, z, "minecraft:coarse_dirt")
                    elif edge >= 3 and n < 0.22:
                        t.set(x, y, z, "minecraft:dirt_path")
                    else:
                        t.set(x, y, z, "minecraft:grass_block", {"snowy": "false"})

    # Rubble collar around the pit.
    for (x, z) in [(10, 10), (14, 10), (10, 14), (14, 14), (11, 10), (14, 12), (10, 13)]:
        t.set(x, 4, z, "minecraft:gravel")

    # Winch frame over the pit.
    for (x, z) in [(10, 10), (14, 10), (10, 14), (14, 14)]:
        for y in range(5, 8):
            t.set(x, y, z, "minecraft:spruce_fence", {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
    for x in range(10, 15):
        t.set(x, 8, 10, "minecraft:stripped_spruce_log", {"axis": "x"})
        t.set(x, 8, 14, "minecraft:stripped_spruce_log", {"axis": "x"})
    for z in range(11, 14):
        t.set(12, 8, z, "minecraft:stripped_spruce_log", {"axis": "z"})
    t.set(12, 7, 12, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.set(12, 6, 12, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.set(10, 8, 10, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})

    # Tent 1 (worn brown canvas) x2..6, z3..8, open at both ends.
    def tent(x0, z0, z1, cloth, ridge):
        for z in range(z0, z1 + 1):
            t.set(x0, 5, z, cloth)
            t.set(x0 + 4, 5, z, cloth)
            t.set(x0 + 1, 6, z, cloth)
            t.set(x0 + 3, 6, z, cloth)
            t.set(x0 + 2, 7, z, ridge)
        t.set(x0 + 2, 5, z0 + 1, "minecraft:brown_carpet")
        t.set(x0 + 2, 5, z0 + 2, "minecraft:brown_carpet")
        t.set(x0 + 1, 5, z1 - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        t.set(x0 + 3, 5, z1 - 1, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})

    tent(2, 3, 8, "minecraft:brown_wool", "minecraft:light_gray_wool")
    tent(18, 15, 20, "minecraft:light_gray_wool", "minecraft:brown_wool")

    # Supply crates.
    for (x, y, z) in [(16, 5, 3), (17, 5, 3), (16, 6, 3), (18, 5, 4), (16, 5, 4)]:
        t.set(x, y, z, "minecraft:oak_planks" if (x + y + z) % 2 else "minecraft:barrel", {} if (x + y + z) % 2 else {"facing": "up", "open": "false"})
    t.set(19, 5, 3, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})

    # Cold campfire with log seats.
    t.set(6, 5, 17, "minecraft:campfire", {"lit": "false", "facing": "north", "signal_fire": "false", "waterlogged": "false"})
    t.set(4, 5, 17, "minecraft:stripped_spruce_log", {"axis": "z"})
    t.set(8, 5, 17, "minecraft:stripped_spruce_log", {"axis": "z"})
    t.set(6, 5, 19, "minecraft:stripped_spruce_log", {"axis": "x"})

    # Dig site: rubble mounds, a broken cutting rig, offcuts left behind.
    for (x, z, h) in [(18, 9, 2), (19, 9, 1), (18, 10, 1), (20, 11, 1), (19, 10, 1)]:
        for y in range(5, 5 + h):
            t.set(x, y, z, "minecraft:cobblestone" if (x + z + y) % 3 else "minecraft:gravel")
    t.set(18, 7, 9, KYBER, {"facing": "up"})
    t.set(20, 6, 11, KYBER, {"facing": "up"})
    t.set(16, 5, 9, "minecraft:chipped_anvil", {"facing": "north"})
    t.set(16, 5, 11, "minecraft:grindstone", {"face": "floor", "facing": "east"})
    for y in range(5, 8):
        t.set(21, y, 8, "minecraft:iron_bars", {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
    t.set(21, 8, 8, "minecraft:iron_bars", {"east": "false", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
    t.set(20, 8, 8, "minecraft:iron_bars", {"east": "true", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
    t.set(20, 7, 8, "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
    t.set(15, 5, 6, "minecraft:cauldron")
    t.set(15, 5, 7, "minecraft:hopper", {"facing": "down", "enabled": "true"})
    t.set(7, 5, 12, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})
    t.set(7, 6, 12, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})

    # Clear the camp volume above the yard (keeps hills and trees out of the camp).
    for x in range(2, 23):
        for z in range(2, 23):
            for y in range(5, 13):
                if (x, y, z) not in t.cells:
                    t.set(x, y, z, AIR)

    # Poachers (persistent template entities; the enemy feature arms them on their first tick).
    t.entity(5, 5, 11, POACHER)
    t.entity(19, 5, 13, POACHER)
    t.entity(14, 5, 6, POACHER)
    return t


# ---------------------------------------------------------------------------------------------
# Descent
# ---------------------------------------------------------------------------------------------
def build_descent():
    t = Template(9, 30, 32)
    seed = 202
    ladder_shaft(t, 4, 4, 24, 29, ("north", "east", "west"))
    t.jigsaw(4, 29, 4, "up_north", "oldrepublic:descent_top", "minecraft:empty", "minecraft:empty", "rollable")
    # Shaft floor at y23; the tunnel leaves south.
    for z in range(3, 6):
        for x in range(3, 6):
            for y in range(24, 28):
                if (x, y, z) not in t.cells:
                    t.set(x, y, z, AIR)

    def floor_at(z):
        if z <= 5:
            return 23
        return max(0, 23 - (z - 5))

    for z in range(6, 32):
        f = floor_at(z)
        for x in range(3, 6):
            for y in range(f + 1, f + 5):
                t.set(x, y, z, AIR)
            if f > 0:
                t.set(x, f, z, "minecraft:cobblestone_stairs", {"facing": "north", "half": "bottom", "shape": "straight", "waterlogged": "false"})
            else:
                t.set(x, f, z, "minecraft:gravel" if hash01(seed, x, z) < 0.3 else STONE)
        # Timber shoring every 4 blocks.
        if z % 4 == 0 and z < 30:
            for y in range(f + 1, f + 5):
                t.set(2, y, z, "minecraft:stripped_spruce_log", {"axis": "y"})
                t.set(6, y, z, "minecraft:stripped_spruce_log", {"axis": "y"})
            for x in range(2, 7):
                t.set(x, f + 5, z, "minecraft:spruce_planks")
        if z % 8 == 2 and 6 < z < 30:
            t.set(5, f + 4, z, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})

    # Abandoned gear in the lower tunnel.
    t.set(3, 1, 27, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(5, 1, 29, "minecraft:rail", {"shape": "north_south", "waterlogged": "false"})
    t.set(5, 1, 30, "minecraft:rail", {"shape": "north_south", "waterlogged": "false"})
    t.jigsaw(4, 1, 31, "south_up", "oldrepublic:descent_bottom", "oldrepublic:heart_entrance",
             "oldrepublic:crystal_cave/heart", "aligned")
    t.shell(2, cave_rock(seed))
    return t


# ---------------------------------------------------------------------------------------------
# Heart
# ---------------------------------------------------------------------------------------------
HEART_STONE = (22, 1, 38)  # meditation stone, template-local


def build_heart():
    t = Template(45, 22, 48)
    seed = 303
    cx, cy, cz = 22, 4, 18
    rx, ry, rz = 13, 9, 9

    def carve(x, y, z):
        if t.get(x, y, z) is None or t.get(x, y, z) in ROCK_LIKE:
            t.set(x, y, z, CAVE_AIR)

    # Main cavern: noisy ellipsoid dome with a mostly flat floor.
    for x in range(cx - rx - 2, cx + rx + 3):
        for y in range(1, cy + ry + 2):
            for z in range(cz - rz - 2, cz + rz + 3):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                d += (value_noise(x, y, z, 5.0, seed) - 0.5) * 0.35
                floor = 1 + (1 if value_noise(x, 0, z, 3.0, seed + 1) > 0.78 else 0)
                if d < 1.0 and y >= floor:
                    carve(x, y, z)

    # Entrance tunnel from the descent (north side).
    for z in range(0, 11):
        for x in range(21, 24):
            for y in range(1, 5):
                carve(x, y, z)
    t.jigsaw(22, 1, 0, "north_up", "oldrepublic:heart_entrance", "minecraft:empty", "minecraft:empty", "aligned")

    # Unstable passage: east from the cavern, then north to the hidden wall.
    for x in range(31, 40):
        for z in range(17, 20):
            for y in range(1, 4):
                carve(x, y, z)
    for z in range(8, 20):
        for x in range(37, 40):
            for y in range(1, 4):
                carve(x, y, z)
    # Wall niche with the first unstable cluster (attached to the east wall, pointing west).
    carve(40, 1, 13)
    carve(40, 2, 13)
    t.set(40, 1, 13, UNSTABLE, {"facing": "west", "charging": "false"})
    # Second one hangs from the ceiling near the dead end.
    t.set(38, 3, 10, UNSTABLE, {"facing": "down", "charging": "false"})
    # A few small kyber clusters lure the eye into the passage.
    t.set(37, 1, 16, KYBER, {"facing": "up"})
    t.set(39, 3, 15, KYBER, {"facing": "down"})

    # Hidden chamber behind a breakable veined wall at z=7.
    for x in range(37, 40):
        for y in range(1, 4):
            t.set(x, y, 7, VEINED)
    for x in range(34, 43):
        for z in range(1, 7):
            for y in range(1, 5):
                rim = (x in (34, 42)) and (z in (1, 6))
                if not rim and not (y == 4 and (x in (34, 42) or z in (1, 6))):
                    carve(x, y, z)
    t.set(38, 1, 2, "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": "oldrepublic:chests/crystal_cave_hidden"})
    t.set(36, 1, 3, "minecraft:brown_carpet")
    t.set(36, 1, 4, "minecraft:brown_carpet")
    t.set(40, 1, 2, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(41, 1, 5, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(41, 2, 5, KYBER, {"facing": "up"})  # a crystal grown through an old crate
    t.set(35, 1, 5, "minecraft:oak_planks")
    t.set(35, 2, 5, "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})

    # Trial chamber: round hall, dome to y8.
    scx, scz = HEART_STONE[0], HEART_STONE[2]
    for x in range(scx - 8, scx + 9):
        for z in range(scz - 8, scz + 9):
            r = math.hypot(x - scx, z - scz)
            if r <= 7.2:
                top = 8 - int(max(0.0, r - 3.5) * 0.9)
                for y in range(1, top + 1):
                    t.set(x, y, z, CAVE_AIR)
                # Floor rings of polished tuff and deepslate tiles.
                ring = int(r)
                t.set(x, 0, z, "minecraft:polished_tuff" if ring % 3 else "minecraft:deepslate_tiles")
    for x in range(scx - 9, scx + 10):
        for z in range(scz - 9, scz + 10):
            r = math.hypot(x - scx, z - scz)
            if 7.2 < r <= 9.2:
                for y in range(0, 10):
                    pillar = (abs(x - scx) == abs(z - scz)) or x == scx or z == scz
                    if pillar and y < 8:
                        t.set(x, y, z, "minecraft:chiseled_deepslate" if y % 4 == 1 else "minecraft:polished_deepslate")
                    elif y in (3, 6):
                        t.set(x, y, z, "minecraft:chiseled_stone_bricks")
                    else:
                        t.set(x, y, z, "minecraft:deepslate_tiles" if hash01(seed, x, y, z) > 0.25 else "minecraft:polished_deepslate")
    # Doorway from the cavern through the chamber wall, closed by the resonant seal at z=29.
    for z in range(26, 32):
        for x in range(21, 24):
            for y in range(1, 4):
                if z == 29:
                    t.set(x, y, z, SEAL, {"open": "false"})
                else:
                    t.set(x, y, z, CAVE_AIR)
    t.set(*HEART_STONE, MEDITATION)
    # Rim crystals.
    for i in range(12):
        a = i * math.tau / 12
        x = scx + int(round(math.cos(a) * 6.4))
        z = scz + int(round(math.sin(a) * 6.4))
        if t.is_air(x, 1, z) and not (21 <= x <= 23 and z <= 32):
            t.set(x, 1, z, KYBER, {"facing": "up"})

    # Ancient carving on the cavern's west wall.
    for z in range(14, 23):
        for y in range(2, 9):
            for x in range(6, 16):
                if t.is_air(x, y, z) and not t.is_air(x - 1, y, z):
                    wx = x - 1
                    pattern = (y + z) % 4
                    name = ["minecraft:chiseled_stone_bricks", "minecraft:chiseled_tuff", "minecraft:cracked_stone_bricks", "minecraft:chiseled_tuff_bricks"][pattern]
                    t.set(wx, y, z, name)
                    break

    # Broken cutting rig on the east side of the cavern.
    rig = (31, 1, 22)
    if t.is_air(*rig):
        for y in range(1, 5):
            t.set(rig[0], y, rig[2], "minecraft:iron_bars", {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
        t.set(rig[0] - 1, 4, rig[2], "minecraft:iron_bars", {"east": "true", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
        t.set(rig[0] - 2, 4, rig[2], "minecraft:iron_bars", {"east": "true", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
        t.set(rig[0] - 2, 3, rig[2], "minecraft:iron_chain", {"axis": "y", "waterlogged": "false"})
        t.set(rig[0] - 2, 1, rig[2], "minecraft:chipped_anvil", {"facing": "east"})
        t.set(rig[0] - 1, 1, rig[2] + 1, "minecraft:grindstone", {"face": "floor", "facing": "north"})
        t.set(rig[0] + 1, 1, rig[2] - 1, "minecraft:barrel", {"facing": "up", "open": "false"})
        t.set(rig[0] - 3, 1, rig[2] - 1, "minecraft:scaffolding", {"bottom": "false", "distance": "0", "waterlogged": "false"})
        t.set(rig[0] - 1, 1, rig[2] - 2, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})

    # Rock shell (structure void beyond it keeps the natural terrain).
    rock = cave_rock(seed)
    t.shell(2, rock)

    # Kyber formations: three dense groups plus sparse clusters on every surface of the cavern.
    formations = [(12, 1, 14), (31, 1, 25), (17, 10, 22)]
    for (x, y, z), cell in list(t.cells.items()):
        if cell[0] != CAVE_AIR or z >= 28 or z < 9:
            continue
        if 21 <= x <= 23 and y <= 2:
            continue  # keep the walkway from the entrance to the seal clear
        near = min(math.dist((x, y, z), f) for f in formations)
        chance = 0.55 if near < 3.2 else 0.035
        if hash01(seed + 11, x, y, z) > chance:
            continue
        for facing, (dx, dy, dz) in (("up", (0, -1, 0)), ("down", (0, 1, 0)), ("east", (-1, 0, 0)),
                                     ("west", (1, 0, 0)), ("south", (0, 0, -1)), ("north", (0, 0, 1))):
            support = t.get(x + dx, y + dy, z + dz)
            if support in ROCK_LIKE:
                t.set(x, y, z, KYBER, {"facing": facing})
                if near < 3.2 and support in (STONE, "minecraft:andesite"):
                    t.set(x + dx, y + dy, z + dz, "minecraft:calcite" if hash01(seed, x, z) > 0.5 else VEINED)
                break
    return t


def main():
    for name, builder in (("camp", build_camp), ("descent", build_descent), ("heart", build_heart)):
        t = builder()
        n, p = t.save(os.path.join(ROOT, name + ".nbt"))
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries, {len(t.entities)} entities")


if __name__ == "__main__":
    main()
