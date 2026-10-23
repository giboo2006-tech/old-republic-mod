#!/usr/bin/env python3
"""Generates W1's Milestone 3 site templates (gzipped structure NBT) and their anchor sidecars.

Deterministic: the same script always writes byte-identical files (fixed seeds, the NBT writer and
Template class of gen_structures.py, fixed gzip mtime). Writes under
src/main/resources/data/oldrepublic/:

  structure/<template>.nbt and site_anchors/<template>.json for the 21 W1 templates of spec 15.1:

  jedi_ruin/courtyard        31 x 14 x 31  start (adapted, 4 layers buried): broken colonnade, a sun-shafted
                                           meditation garden, a robed statue with its face worn away, half a
                                           dome over a 3x3 veiled wall that hides the stair mouth, the diggers'
                                           camp. Anchors STAIR_MOUTH, SURFACE_GUARD, DIGGERS, CHEST_1
  jedi_ruin/stair             7 x 12 x 9   the switchback stair under the courtyard (no anchors)
  jedi_ruin/listening_hall   25 x 14 x 25  the Listening Hall: the Jedi pedestal (always ready) on a gallery
                                           7 up the north wall, three counterweights in floor alcoves (W, E,
                                           S), six step stones on the north wall wired by hidden redstone (two
                                           per counterweight), moss under the ledges, a veiled panel in the
                                           east wall to the niche, a passage in the west wall to the anchor
                                           cache. Anchors HEART, PEDESTAL_JEDI, COUNTERWEIGHT_W/E/S,
                                           SENTINEL_1, SENTINEL_2, CHEST_1
  jedi_ruin/pilgrim_niche     7 x 6 x 7    the Wayfarer pedestal (always ready). PEDESTAL_WAYFARER
  jedi_ruin/anchor_cache      7 x 6 x 7    a pull anchor above a puzzle seal guarding chests/force_cache. CACHE
  sith_tomb/gate             17 x 12 x 17  start (adapted, 3 layers buried): the half-sunk obelisk gateway
                                           and wind-cut steps. STAIR_MOUTH, SURFACE_GUARD
  sith_tomb/path              5 x 3 x 9    terrain-matching connector to the dig camp (no anchors)
  sith_tomb/dig_camp         19 x 8 x 19   the Imperial dig camp (adapted). SQUAD, CHEST_1
  sith_tomb/processional      9 x 14 x 27  the descending processional: 4 vents, funerary urns, a Tomb
                                           sentinel, diggers, veiled tomb stone to the niche. VENT_1-4,
                                           SENTINEL_1, DIGGERS, NICHE
  sith_tomb/burial_chamber   27 x 14 x 27  the urn puzzle: the keeper behind the sarcophagus dais (urns NBT),
                                           4 echo urns on plinths, 2 Tomb sentinels, the 7x6x7 sanctum behind
                                           a puzzle seal the keeper powers, the Sith pedestal powered by the
                                           keeper too. HEART, URN_KEEPER, URN_1-4, PEDESTAL_SITH, SENTINEL_1,
                                           SENTINEL_2, CHEST_1
  sith_tomb/pilgrim_niche     7 x 6 x 7    the Wayfarer pedestal (always ready). PEDESTAL_WAYFARER
  sith_tomb/rubble_room       9 x 7 x 9    collapsed rubble door, chests/force_cache, 2 funerary urns. CACHE
  wreck_field/hull           41 x 18 x 19  start (adapted, 4 layers buried): the tilted frigate section with
                                           the cargo hold behind veiled plating, the cargo counterweight
                                           powering the hatch seal and the Wayfarer pedestal, the torn deck 5
                                           up, the anchor locker. HEART, HATCH, COUNTERWEIGHT,
                                           PEDESTAL_WAYFARER, DECK_CACHE, ANCHOR_LOCKER, SCAVENGER_1,
                                           SCAVENGER_2, SURFACE_GUARD
  wreck_field/path            5 x 3 x 9    terrain-matching connector (no anchors)
  wreck_field/salvage_camp   15 x 8 x 15   the Republic salvage camp (adapted). SQUAD, CHEST_1
  wreck_field/scar           15 x 6 x 15   three blast craters with torn plating (adapted, no anchors)
  outpost/republic_post      23 x 12 x 23  Republic Forward Post (1 layer buried). TERMINAL, OFFICER,
                                           GARRISON_1-4
  outpost/imperial_post      23 x 14 x 23  Imperial Listening Post (1 layer buried). same anchors
  site/echo_shrine_jedi      15 x 14 x 15  Echo Shrines (9 layers buried): a broken arch over a 2x2 veiled
  site/echo_shrine_sith                    hatch, a stair to a 9x6x9 chamber 8 below the surface, a
  site/echo_shrine_wayfarer                counterweight powering the seal in front of the pedestal of the
                                           kind, a guard spawner. HEART, PEDESTAL, COUNTERWEIGHT, GUARD

Rules (spec 3, 15.1 and the W1 plan 4.3 and 4.8; keep in step with the Java code):
  * Template-local coordinates: x east, y up, z south. Runtime templates (outposts, shrines) have
    their entrance on the local north side (z = 0). Unset cells are structure void.
  * `buried` B: layers 0..B-1 are underground, layer B-1 is the ground, layer B the feet layer.
    Worldgen starts use start_height 1 - B (vanilla moves a projected start so its layer 1 sits on
    the first free block); runtime placement puts layer B at the stand (RuntimeSites).
  * Anchors are sidecars ({"ANCHOR": [[x, y, z], ...]}); every anchor cell holds its real block:
    site_spawner at SQUAD, SENTINEL_n, SCAVENGER_n, DIGGERS and GUARD (with its group NBT), chests at
    CHEST_n, CACHE, DECK_CACHE and ANCHOR_LOCKER, the pedestal at PEDESTAL*, the keeper, the urns,
    the vents, the counterweights, the terminal, veiled stone at STAIR_MOUTH-side cells, air feet
    cells at OFFICER, GARRISON_n, SURFACE_GUARD and HEART. No template carries entities.
  * Puzzle wiring is vanilla redstone, written without block updates, so every redstone wire gets
    the connection properties vanilla would compute and power 0. The redstone checker below
    simulates the vanilla rules (wire power and connections, repeaters, strongly and weakly powered
    blocks, `hasNeighborSignal`) and the generator refuses to write a template whose puzzle does not
    work: in the Listening Hall each counterweight extends exactly its two step stones.
  * Jigsaw connections are checked by assembling every structure on flat ground the way vanilla
    places rigid pieces: no two piece boxes may intersect, every connection opening lines up, and the
    site's resource counts are inside spec 3.2.

Usage: python3 tools/assets/gen_structures_m3.py
"""

import json
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import gen_structures as gs  # noqa: E402
from gen_structures import AIR, CAVE_AIR, Byte, Int, Template, hash01, value_noise  # noqa: E402

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "data", "oldrepublic")

# ---------------------------------------------------------------------------------------------
# NBT: gen_structures.py has no TAG_Int_Array, which the urn keeper's `urns` needs. Extend its writer
# here (it looks its helpers up through the module, so the extension applies to nested compounds).
# ---------------------------------------------------------------------------------------------
TAG_INT_ARRAY = 11


class IntArray(list):
    """An NBT TAG_Int_Array."""


_base_tag_type = gs._tag_type
_base_write_payload = gs._write_payload


def _tag_type(value):
    if isinstance(value, IntArray):
        return TAG_INT_ARRAY
    return _base_tag_type(value)


def _write_payload(out, tag, value):
    if tag == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(value)))
        for item in value:
            out.write(struct.pack(">i", int(item)))
    else:
        _base_write_payload(out, tag, value)


gs._tag_type = _tag_type
gs._write_payload = _write_payload

# ---------------------------------------------------------------------------------------------
# Block names
# ---------------------------------------------------------------------------------------------
MOD = "oldrepublic:"
RUIN = MOD + "ruin_stone"
RUIN_CARVED = MOD + "ruin_stone_carved"
RUIN_CRACKED = MOD + "ruin_stone_cracked"
RUIN_MOSSY = MOD + "ruin_stone_mossy"
TOMB = MOD + "tomb_stone"
TOMB_CARVED = MOD + "tomb_stone_carved"
TOMB_CRACKED = MOD + "tomb_stone_cracked"
PLATE = MOD + "wreck_plating"
SCORCHED = MOD + "wreck_plating_scorched"
CONDUIT = MOD + "wreck_conduit"
REP_PANEL = MOD + "republic_panel"
IMP_PANEL = MOD + "imperial_panel"
VEILED = MOD + "veiled_stone"
COUNTERWEIGHT = MOD + "counterweight_stone"
STEP = MOD + "step_stone"
ECHO_URN = MOD + "echo_urn"
KEEPER = MOD + "urn_keeper"
ANCHOR = MOD + "pull_anchor"
RUBBLE = MOD + "collapsed_rubble"
SEAL = MOD + "puzzle_seal"
PEDESTAL = MOD + "holocron_pedestal"
VENT = MOD + "tomb_vent"
MOSS = MOD + "lumen_moss"
FUNERARY = MOD + "funerary_urn"
SALVAGE = MOD + "wreck_salvage"
TERMINAL = MOD + "bounty_terminal"
SPAWNER = MOD + "site_spawner"
WIRE = "minecraft:redstone_wire"
REPEATER = "minecraft:repeater"
CHEST = "minecraft:chest"

GRASS = "minecraft:grass_block"
DIRT = "minecraft:dirt"
COARSE = "minecraft:coarse_dirt"
STONE = "minecraft:stone"
SAND = "minecraft:sand"

BARS = {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"}
LANTERN = {"hanging": "false", "waterlogged": "false"}
LANTERN_HUNG = {"hanging": "true", "waterlogged": "false"}
LEAVES = {"distance": "7", "persistent": "true", "waterlogged": "false"}
BOTTOM = {"type": "bottom", "waterlogged": "false"}
TOP = {"type": "top", "waterlogged": "false"}


def stairs(facing, half="bottom"):
    return {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"}


def bars(**sides):
    props = dict(BARS)
    for side, on in sides.items():
        props[side] = "true" if on else "false"
    return props


def log(axis):
    return {"axis": axis}


def campfire(facing="north", lit=True):
    return {"facing": facing, "lit": "true" if lit else "false", "signal_fire": "false", "waterlogged": "false"}


DIRECTIONS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
HORIZONTAL = ("north", "east", "south", "west")


def step_of(pos, direction, n=1):
    d = DIRECTIONS[direction]
    return (pos[0] + d[0] * n, pos[1] + d[1] * n, pos[2] + d[2] * n)


# ---------------------------------------------------------------------------------------------
# Site templates
# ---------------------------------------------------------------------------------------------
class Site(Template):
    """A template with its anchors, its buried depth and the redstone it carries."""

    def __init__(self, name, size, buried):
        super().__init__(*size)
        self.name = name
        self.buried = buried
        self.anchors = {}
        self.jigsaws = []  # (pos, orientation, name, target, pool, joint)

    # anchors -------------------------------------------------------------------------------
    def mark(self, anchor, x, y, z):
        self.anchors.setdefault(anchor, []).append((x, y, z))

    def spot(self, anchor, x, y, z):
        """A feet anchor: air at the feet and head cells."""
        self.set(x, y, z, AIR)
        self.set(x, y + 1, z, AIR)
        self.mark(anchor, x, y, z)

    # filling ---------------------------------------------------------------------------------
    def box(self, x0, y0, z0, x1, y1, z1, block, props=None):
        """Fills a box; `block` is a name or a function (x, y, z) -> name or (name, props)."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, block, props)

    def put(self, x, y, z, block, props=None):
        if callable(block):
            block = block(x, y, z)
        if block is None:
            return
        if isinstance(block, tuple):
            self.set(x, y, z, block[0], block[1])
        else:
            self.set(x, y, z, block, props)

    def keep(self, x, y, z, block, props=None):
        """Sets a cell only when nothing is there yet."""
        if (x, y, z) not in self.cells:
            self.put(x, y, z, block, props)

    def air(self, x0, y0, z0, x1, y1, z1):
        self.box(x0, y0, z0, x1, y1, z1, AIR)

    def jig(self, x, y, z, orientation, name, target, pool, joint="aligned", final_state=AIR):
        self.jigsaw(x, y, z, orientation, MOD + name, target if target == "minecraft:empty" else MOD + target,
                    pool if pool == "minecraft:empty" else MOD + pool, joint, final_state)
        self.jigsaws.append(((x, y, z), orientation, MOD + name, target if target == "minecraft:empty" else MOD + target,
                             pool if pool == "minecraft:empty" else MOD + pool, joint))

    # site blocks -----------------------------------------------------------------------------
    def spawner(self, anchor, x, y, z, group, count=0, bound=False, replace_with=AIR):
        """A site_spawner with its group (plan 4.8); the cell above is cleared for the spawn."""
        self.set(x, y, z, SPAWNER, None, {
            "id": SPAWNER, "group": group, "count": Int(count), "bound": Byte(1 if bound else 0),
            "replace_with": {"Name": replace_with},
        })
        self.set(x, y + 1, z, AIR)
        self.mark(anchor, x, y, z)

    def chest(self, anchor, x, y, z, facing, loot):
        self.set(x, y, z, CHEST, {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": CHEST, "LootTable": MOD + "chests/" + loot})
        if anchor:
            self.mark(anchor, x, y, z)

    def pedestal(self, anchor, x, y, z, kind, always_ready):
        self.set(x, y, z, PEDESTAL, {"kind": kind, "ready": "false", "always_ready": "true" if always_ready else "false"},
                 {"id": PEDESTAL})
        if anchor:
            self.mark(anchor, x, y, z)

    def veiled(self, x, y, z, skin):
        self.set(x, y, z, VEILED, {"skin": skin, "veil": "veiled", "age": "0"})

    def counterweight(self, anchor, x, y, z, facing, skin):
        self.set(x, y, z, COUNTERWEIGHT, {"sunk": "false", "facing": facing, "skin": skin, "age": "0"})
        self.mark(anchor, x, y, z)

    def step_stone(self, x, y, z, facing):
        self.set(x, y, z, STEP, {"extended": "false", "facing": facing})

    def seal(self, x, y, z, skin):
        self.set(x, y, z, SEAL, {"open": "false", "skin": skin, "age": "0"})

    def vent(self, anchor, x, y, z):
        self.set(x, y, z, VENT, {"phase": "idle"})
        self.mark(anchor, x, y, z)

    def wire(self, x, y, z):
        """Redstone dust; its connections are computed once the template is complete (finish_wires)."""
        self.set(x, y, z, WIRE, {"north": "none", "east": "none", "south": "none", "west": "none", "power": "0"})

    def repeater(self, x, y, z, facing, delay=1):
        """A repeater whose input is the `facing` side (vanilla DiodeBlock: input at pos + facing)."""
        self.set(x, y, z, REPEATER, {"facing": facing, "delay": str(delay), "locked": "false", "powered": "false"})

    def finish_wires(self):
        """Writes the connection properties vanilla would compute for every wire."""
        sim = Redstone(self)
        for pos, cell in list(self.cells.items()):
            if cell[0] == WIRE:
                props = sim.connections(pos)
                props = {d: props[d] for d in ("north", "east", "south", "west")}
                props["power"] = "0"
                self.set(*pos, WIRE, props)

    def props(self, pos):
        cell = self.cells.get(pos)
        return dict(cell[1]) if cell else {}

    def count(self, name):
        return sum(1 for c in self.cells.values() if c[0] == name)


# ---------------------------------------------------------------------------------------------
# Redstone checker (vanilla rules as of RedstoneWireBlock / DiodeBlock / Level#getSignal)
# ---------------------------------------------------------------------------------------------
NOT_CONDUCTORS = {
    AIR, CAVE_AIR, WIRE, REPEATER, CHEST, PEDESTAL, STEP, MOSS, FUNERARY, ECHO_URN, SALVAGE, SPAWNER, "minecraft:jigsaw",
    "minecraft:lantern", "minecraft:soul_lantern", "minecraft:iron_bars", "minecraft:iron_chain", "minecraft:glass",
    "minecraft:water", "minecraft:barrel",
}


NON_CONDUCTOR_TOKENS = {
    "slab", "stairs", "carpet", "fence", "pane", "trapdoor", "door", "button", "lantern", "torch", "banner", "flower", "leaves",
    "glass", "wall", "candle", "bed", "rail", "sapling", "grass", "fern", "bush", "campfire", "anvil", "lectern", "cauldron",
    "scaffolding", "rod", "chain", "pot", "head", "skull", "vine", "pressure", "bars", "pad", "stand", "daisy", "bluet", "valley",
    "cobweb", "water", "lava", "air", "jigsaw",
}


def is_conductor_name(name):
    """Whether vanilla treats a full, opaque block of this name as a redstone conductor (good enough for templates)."""
    if name is None or name in NOT_CONDUCTORS:
        return False
    plain = name.split(":", 1)[1]
    if plain in ("grass_block", "moss_block", "glowstone"):
        return True
    return not any(token in NON_CONDUCTOR_TOKENS for token in plain.split("_"))


class Redstone:
    """Power in one template, given which sources are switched on."""

    def __init__(self, t, active=()):
        self.t = t
        self.active = set(active)
        self.power = {p: 0 for p, c in t.cells.items() if c[0] == WIRE}
        self.on = {p: False for p, c in t.cells.items() if c[0] == REPEATER}
        self._conn = {}

    def name(self, pos):
        return self.t.get(*pos)

    def conductor(self, pos):
        name = self.name(pos)
        if name == COUNTERWEIGHT and pos in self.active:
            return False  # a sunk counterweight is not a full block
        if name == SEAL:
            return True
        return is_conductor_name(name)

    def facing(self, pos):
        return self.t.props(pos).get("facing")

    def source(self, pos, d):
        """getSignal of a non-wire source at pos toward a receiver at pos - d (d: receiver -> pos)."""
        name = self.name(pos)
        if name == REPEATER:
            return 15 if self.on[pos] and self.facing(pos) == d else 0
        if name == COUNTERWEIGHT:
            return 15 if pos in self.active and self.facing(pos) == d else 0
        if name in (KEEPER, ANCHOR):
            return 15 if pos in self.active else 0
        return 0

    def is_source(self, pos):
        return self.name(pos) in (REPEATER, COUNTERWEIGHT, KEEPER, ANCHOR)

    def connects_to(self, pos, d):
        """shouldConnectTo(state, d) for the block at pos seen from a wire in direction d (None: no direction)."""
        name = self.name(pos)
        if name == WIRE:
            return True
        if name == REPEATER:
            f = self.facing(pos)
            return d is not None and (f == d or f == OPPOSITE[d])
        return d is not None and name in (COUNTERWEIGHT, KEEPER, ANCHOR)

    def connections(self, pos):
        if pos in self._conn:
            return self._conn[pos]
        above_open = not self.conductor(step_of(pos, "up"))
        sides = {}
        for d in HORIZONTAL:
            side = step_of(pos, d)
            result = "none"
            if above_open and self.conductor(side) and self.name(step_of(side, "up")) == WIRE:
                result = "up"
            elif self.connects_to(side, d):
                result = "side"
            elif not self.conductor(side) and self.name(step_of(side, "down")) == WIRE:
                result = "side"
            sides[d] = result
        n, s = sides["north"] != "none", sides["south"] != "none"
        e, w = sides["east"] != "none", sides["west"] != "none"
        if not n and not s:
            sides["east"] = sides["east"] if e else "side"
            sides["west"] = sides["west"] if w else "side"
        if not e and not w:
            sides["north"] = sides["north"] if n else "side"
            sides["south"] = sides["south"] if s else "side"
        self._conn[pos] = sides
        return sides

    def wire_out(self, pos, d):
        """getSignal of the wire at pos toward a receiver at pos - d."""
        p = self.power[pos]
        if p == 0 or d == "down":
            return 0
        if d == "up":
            return p
        return p if self.connections(pos)[OPPOSITE[d]] != "none" else 0

    def direct(self, pos, d, for_wire):
        """getDirectSignal of the block at pos toward a receiver at pos - d."""
        if self.name(pos) == WIRE:
            return 0 if for_wire else self.wire_out(pos, d)
        return self.source(pos, d)

    def direct_to(self, pos, for_wire):
        """Level#getDirectSignalTo: the strongest direct signal any neighbour sends into pos."""
        best = 0
        for e in DIRECTIONS:
            best = max(best, self.direct(step_of(pos, e), e, for_wire))
        return best

    def signal(self, pos, d, for_wire=False):
        """Level#getSignal(pos, d): what the block at pos sends to the receiver at pos - d."""
        name = self.name(pos)
        if name == WIRE:
            s = 0 if for_wire else self.wire_out(pos, d)
        else:
            s = self.source(pos, d)
        if self.conductor(pos):
            s = max(s, self.direct_to(pos, for_wire))
        return s

    def solve(self):
        for _ in range(400):
            changed = False
            for pos in self.power:
                best = 0
                for d in DIRECTIONS:
                    best = max(best, self.signal(step_of(pos, d), d, for_wire=True))
                j = 0
                if best < 15:
                    for d in HORIZONTAL:
                        side = step_of(pos, d)
                        if self.name(side) == WIRE:
                            j = max(j, self.power[side])
                        if self.conductor(side) and not self.conductor(step_of(pos, "up")):
                            above = step_of(side, "up")
                            if self.name(above) == WIRE:
                                j = max(j, self.power[above])
                        elif not self.conductor(side):
                            below = step_of(side, "down")
                            if self.name(below) == WIRE:
                                j = max(j, self.power[below])
                value = max(best, j - 1, 0)
                if value != self.power[pos]:
                    self.power[pos] = value
                    changed = True
            for pos in self.on:
                f = self.facing(pos)
                inp = step_of(pos, f)
                level = self.signal(inp, f)
                if self.name(inp) == WIRE:
                    level = max(level, self.power[inp])
                value = level > 0
                if value != self.on[pos]:
                    self.on[pos] = value
                    changed = True
            if not changed:
                return self
        raise SystemExit(f"{self.t.name}: redstone does not settle")

    def powered(self, pos):
        """Level#hasNeighborSignal."""
        return any(self.signal(step_of(pos, d), d) > 0 for d in DIRECTIONS)

    def receivers(self):
        return sorted(p for p, c in self.t.cells.items() if c[0] in (STEP, SEAL, PEDESTAL) and self.powered(p))


def check_redstone(t, cases):
    """`cases`: list of (active source positions, expected powered receivers)."""
    for active, expected in cases:
        got = set(Redstone(t, active).solve().receivers())
        if got != set(expected):
            raise SystemExit(f"{t.name}: sources {sorted(active)} power {sorted(got)}, expected {sorted(expected)}")


# ---------------------------------------------------------------------------------------------
# Rotation (StructureTemplate.transform with pivot ZERO and no mirror)
# ---------------------------------------------------------------------------------------------
ROTATIONS = ("none", "clockwise_90", "180", "counterclockwise_90")
TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def rotate_pos(pos, rotation):
    x, y, z = pos
    if rotation == "clockwise_90":
        return (-z, y, x)
    if rotation == "180":
        return (-x, y, -z)
    if rotation == "counterclockwise_90":
        return (z, y, -x)
    return (x, y, z)


def rotate_dir(direction, rotation):
    turns = {"none": 0, "clockwise_90": 1, "180": 2, "counterclockwise_90": 3}[rotation]
    for _ in range(turns):
        direction = TURN[direction]
    return direction


def inverse(rotation):
    return {"none": "none", "clockwise_90": "counterclockwise_90", "180": "180", "counterclockwise_90": "clockwise_90"}[rotation]


def rotate_orientation(orientation, rotation):
    front, top = orientation.split("_")
    return rotate_dir(front, rotation) + "_" + rotate_dir(top, rotation)


def world_box(origin, size, rotation):
    """The block box (min, max inclusive) of a template placed at origin with rotation (pivot ZERO)."""
    corners = [rotate_pos((x, 0, z), rotation) for x in (0, size[0] - 1) for z in (0, size[2] - 1)]
    xs = [c[0] for c in corners]
    zs = [c[2] for c in corners]
    return ((origin[0] + min(xs), origin[1], origin[2] + min(zs)),
            (origin[0] + max(xs), origin[1] + size[1] - 1, origin[2] + max(zs)))


def boxes_intersect(a, b):
    """True when two block boxes overlap (vanilla deflates the new box by 0.25, so touching faces are fine)."""
    return all(a[0][i] <= b[1][i] and b[0][i] <= a[1][i] for i in range(3))


def to_world(origin, rotation, pos):
    r = rotate_pos(pos, rotation)
    return (origin[0] + r[0], origin[1] + r[1], origin[2] + r[2])


# ---------------------------------------------------------------------------------------------
# Structures: pools and the assembly check (vanilla JigsawPlacement on flat ground)
# ---------------------------------------------------------------------------------------------
RIGID, TERRAIN = "rigid", "terrain_matching"


class Element:
    """A pool element: a template, its projection, processors and (for adapted elements) the terrain numbers."""

    def __init__(self, template, projection=RIGID, processors="minecraft:empty", adapted=None, weight=1):
        self.template = template
        self.projection = projection
        self.processors = processors
        self.adapted = adapted  # dict of AdaptedPoolElement fields, or None for a plain single element
        self.weight = weight


class Structure:
    def __init__(self, name, start_pool, size, max_distance, expected, resources, step="surface_structures"):
        self.name = name
        self.start_pool = start_pool
        self.size = size
        self.max_distance = max_distance
        self.expected = expected  # template -> how many pieces of it every assembly has
        self.resources = resources  # block -> (min, max) over the whole site
        self.step = step


def assemble(structure, pools, templates):
    """Places the structure the way vanilla's JigsawPlacement does on flat ground with every rotation of the start,
    and checks it: every expected piece is placed, no two piece boxes intersect, every connection opening is open,
    nothing leaves the max-distance box, and the resource counts are inside the spec range. Returns the pieces of the
    unrotated assembly as (template, origin, rotation, depth)."""
    first = None
    for start_rotation in ROTATIONS:
        start = templates[pools[structure.start_pool][0].template]
        feet = 64
        # The start's layer `buried` sits on the first free block (start_height 1 - buried, projected).
        origin = (0, feet - start.buried, 0)
        pieces = [(start.name, origin, start_rotation, 0, pools[structure.start_pool][0])]
        boxes = [world_box(origin, start.size, start_rotation)]
        centre = ((boxes[0][0][0] + boxes[0][1][0]) // 2, boxes[0][0][1] + 1, (boxes[0][0][2] + boxes[0][1][2]) // 2)
        d = structure.max_distance
        limit = ((centre[0] - d, centre[1] - d, centre[2] - d), (centre[0] + d, centre[1] + d, centre[2] + d))
        queue = [0]
        while queue:
            index = queue.pop(0)
            name, porigin, prot, depth, pelement = pieces[index]
            parent = templates[name]
            for (jpos, jorient, jname, jtarget, jpool, jjoint) in parent.jigsaws:
                if jpool == "minecraft:empty":
                    continue
                if depth >= structure.size:
                    raise SystemExit(f"{structure.name}: {name} at depth {depth} reaches the size limit {structure.size}")
                wpos = to_world(porigin, prot, jpos)
                worient = rotate_orientation(jorient, prot)
                front, top = worient.split("_")
                target_cell = step_of(wpos, front)
                placed = False
                for element in pools[jpool.split(":", 1)[1]]:
                    child = templates[element.template]
                    for (cpos, corient, cname, ctarget, cpool, cjoint) in child.jigsaws:
                        if cname != jtarget:
                            continue
                        for rot in ROTATIONS:
                            cworient = rotate_orientation(corient, rot)
                            cfront, ctop = cworient.split("_")
                            if cfront != OPPOSITE[front]:
                                continue
                            if front in ("up", "down") and jjoint != "rollable" and ctop != top:
                                continue
                            r = rotate_pos(cpos, rot)
                            if element.projection == TERRAIN or pelement.projection == TERRAIN:
                                # Non-rigid: the child's jigsaw sits on the first free height at the parent jigsaw.
                                cy = feet
                            else:
                                cy = target_cell[1]
                            corigin = (target_cell[0] - r[0], cy - r[1], target_cell[2] - r[2])
                            cbox = world_box(corigin, child.size, rot)
                            clash = [templates[pieces[i][0]].name for i, b in enumerate(boxes) if boxes_intersect(cbox, b)]
                            if clash:
                                raise SystemExit(f"{structure.name}: {child.name} from {name} intersects {clash}")
                            if not all(limit[0][i] <= cbox[0][i] and cbox[1][i] <= limit[1][i] for i in range(3)):
                                raise SystemExit(f"{structure.name}: {child.name} leaves the max distance box")
                            if front not in ("up", "down"):
                                # The opening: the cell above both jigsaws is passable.
                                above_parent = parent.get(*step_of(jpos, "up"))
                                above_child = child.get(*step_of(cpos, "up"))
                                if above_parent not in (AIR, CAVE_AIR, None) or above_child not in (AIR, CAVE_AIR, None):
                                    raise SystemExit(f"{structure.name}: the opening {name} -> {child.name} is blocked above the jigsaw")
                            pieces.append((child.name, corigin, rot, depth + 1, element))
                            boxes.append(cbox)
                            queue.append(len(pieces) - 1)
                            placed = True
                            break
                        if placed:
                            break
                    if placed:
                        break
                if not placed:
                    raise SystemExit(f"{structure.name}: nothing in {jpool} attaches to {jname} of {name}")
        counts = {}
        for p in pieces:
            counts[p[0]] = counts.get(p[0], 0) + 1
        if counts != structure.expected:
            raise SystemExit(f"{structure.name}: pieces {counts}, expected {structure.expected}")
        for block, (lo, hi) in structure.resources.items():
            total = sum(templates[p[0]].count(block) for p in pieces)
            if not lo <= total <= hi:
                raise SystemExit(f"{structure.name}: {total} x {block}, expected {lo}-{hi}")
        if first is None:
            first = [(p[0], p[1], p[2], p[3]) for p in pieces]
    return first


# ---------------------------------------------------------------------------------------------
# Sidecars and template checks
# ---------------------------------------------------------------------------------------------
def sidecar(path, anchors):
    if gs.rebuilt_in_m6(path):
        return
    text = "{\n" + ",\n".join(
        f'  "{anchor}": [' + ", ".join(f"[{x}, {y}, {z}]" for (x, y, z) in cells) + "]"
        for anchor, cells in anchors.items()) + "\n}\n"
    json.loads(text)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


ANCHOR_BLOCKS = {
    "PEDESTAL_JEDI": PEDESTAL, "PEDESTAL_SITH": PEDESTAL, "PEDESTAL_WAYFARER": PEDESTAL, "PEDESTAL": PEDESTAL,
    "SQUAD": SPAWNER, "SENTINEL_1": SPAWNER, "SENTINEL_2": SPAWNER, "SCAVENGER_1": SPAWNER, "SCAVENGER_2": SPAWNER,
    "DIGGERS": SPAWNER, "GUARD": SPAWNER,
    "CHEST_1": CHEST, "CACHE": CHEST, "DECK_CACHE": CHEST, "ANCHOR_LOCKER": CHEST,
    "URN_KEEPER": KEEPER, "URN_1": ECHO_URN, "URN_2": ECHO_URN, "URN_3": ECHO_URN, "URN_4": ECHO_URN,
    "VENT_1": VENT, "VENT_2": VENT, "VENT_3": VENT, "VENT_4": VENT,
    "COUNTERWEIGHT": COUNTERWEIGHT, "COUNTERWEIGHT_W": COUNTERWEIGHT, "COUNTERWEIGHT_E": COUNTERWEIGHT,
    "COUNTERWEIGHT_S": COUNTERWEIGHT, "TERMINAL": TERMINAL, "HATCH": VEILED, "NICHE": VEILED, "STAIR_MOUTH": None,
}
FEET_ANCHORS = ("HEART", "SURFACE_GUARD", "OFFICER", "GARRISON_1", "GARRISON_2", "GARRISON_3", "GARRISON_4")


def check_template(t, size, expected):
    if t.size != size:
        raise SystemExit(f"{t.name}: size {t.size}, expected {size}")
    if sorted(t.anchors) != sorted(expected):
        raise SystemExit(f"{t.name}: anchors {sorted(t.anchors)}, expected {sorted(expected)}")
    for anchor, cells in t.anchors.items():
        for cell in cells:
            if not t.inside(*cell):
                raise SystemExit(f"{t.name}: {anchor} {cell} is outside the template")
            block = ANCHOR_BLOCKS.get(anchor)
            if block is not None and t.get(*cell) != block:
                raise SystemExit(f"{t.name}: {anchor} {cell} holds {t.get(*cell)}, expected {block}")
            if anchor in FEET_ANCHORS:
                if t.get(*cell) not in (AIR, CAVE_AIR) or t.get(*step_of(cell, "up")) not in (AIR, CAVE_AIR):
                    raise SystemExit(f"{t.name}: {anchor} {cell} is not two cells of air")
                below = t.get(*step_of(cell, "down"))
                if cell[1] > 0 and (below is None or not is_conductor_name(below) and below not in (SPAWNER,)):
                    raise SystemExit(f"{t.name}: {anchor} {cell} does not stand on a block ({below})")
    for pos, cell in t.cells.items():
        if cell[0] == SPAWNER and t.get(*step_of(pos, "up")) not in (AIR, CAVE_AIR):
            raise SystemExit(f"{t.name}: the spawner at {pos} has no room above it")
        if cell[0] == "minecraft:jigsaw" and not any(j[0] == pos for j in t.jigsaws):
            raise SystemExit(f"{t.name}: stray jigsaw at {pos}")
    for (jpos, orientation, *_rest) in t.jigsaws:
        front = orientation.split("_")[0]
        if t.inside(*step_of(jpos, front)):
            raise SystemExit(f"{t.name}: the jigsaw at {jpos} faces into its own template")


# ---------------------------------------------------------------------------------------------
# Shared building helpers
# ---------------------------------------------------------------------------------------------
def noise(seed, x, y, z):
    return hash01(seed, x, y, z)


def ground_layers(t, top, seed, top_block=GRASS, under=DIRT, deep=STONE, skip=None):
    """Fills layers 0..top with ground: `top_block` on the top layer, dirt under it, stone deeper."""
    sx, _, sz = t.size
    for x in range(sx):
        for z in range(sz):
            if skip and skip(x, z):
                continue
            for y in range(top + 1):
                if y == top:
                    block = top_block(x, z) if callable(top_block) else top_block
                elif y >= top - 2:
                    block = under
                else:
                    block = deep if noise(seed, x, y, z) > 0.12 else "minecraft:andesite"
                t.keep(x, y, z, block)


def clear_above(t, y0):
    """Air in every unset cell from layer y0 up (the site clears the terrain inside its box)."""
    sx, sy, sz = t.size
    for x in range(sx):
        for z in range(sz):
            for y in range(y0, sy):
                t.keep(x, y, z, AIR)


def column(t, x, z, y0, y1, block, props=None):
    for y in range(y0, y1 + 1):
        t.put(x, y, z, block, props)


def lantern(t, x, y, z, hanging=False, soul=False):
    t.set(x, y, z, "minecraft:soul_lantern" if soul else "minecraft:lantern", LANTERN_HUNG if hanging else LANTERN)


# =============================================================================================
# Watchers' Rest (jedi_ruin)
# =============================================================================================
TRIM_STAIRS = "minecraft:smooth_sandstone_stairs"
TRIM_SLAB = "minecraft:smooth_sandstone_slab"
FLOWERS = ("minecraft:lily_of_the_valley", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:fern", "minecraft:short_grass")


def ruin_paving(seed):
    def fn(x, z):
        r = noise(seed, x, 0, z)
        if r < 0.08:
            return RUIN_CRACKED
        if r < 0.16:
            return RUIN_MOSSY
        if r < 0.2:
            return COARSE
        return RUIN
    return fn


def build_courtyard():
    t = Site("jedi_ruin/courtyard", (31, 14, 31), 4)
    # --- the stairwell under the half dome: treads (stand per row) descending south into jedi_ruin/stair.
    treads = {19: 3, 20: 2, 21: 1, 22: 0}
    for z in range(19, 25):
        for y in range(0, 4):
            t.set(13, y, z, RUIN)
            t.set(17, y, z, RUIN)
    for x in range(13, 18):
        for y in range(0, 4):
            t.set(x, y, 24, RUIN)
    for z, s in treads.items():
        for x in (14, 15, 16):
            for y in range(s, 4):
                t.set(x, y, z, AIR)
            if s - 1 >= 0:
                t.set(x, s - 1, z, TRIM_STAIRS, stairs("north"))
                for y in range(0, s - 1):
                    t.set(x, y, z, RUIN)
    for x in (14, 15, 16):
        for y in range(0, 4):
            t.set(x, y, 23, AIR)
        # headroom for the step from the stair piece's top tread (feet -1, z = 23) down to its next (feet -2, z = 24)
        t.set(x, 0, 24, AIR)
    t.jig(14, 0, 23, "down_south", "courtyard_stair", "stair_top", "jedi_ruin/underground")
    # --- the half dome: a semicircular apse opening north at z = 18, closed by a facade with the veiled doorway.
    for x in range(8, 23):
        for z in range(18, 26):
            for y in range(4, 14):
                dh = math.hypot(x - 15, z - 18)
                d = dh if y <= 7 else math.sqrt(dh * dh + (y - 7) ** 2)
                if z == 18:
                    if math.hypot(x - 15, max(0, y - 7)) <= 6.6:
                        t.set(x, y, z, RUIN)
                elif d <= 6.6:
                    t.set(x, y, z, RUIN if d >= 5.5 else AIR)
            if z > 18 and math.hypot(x - 15, z - 18) < 6.6:
                t.keep(x, 3, z, RUIN)
    for y in range(4, 8):
        t.set(13, y, 18, RUIN_CARVED)
        t.set(17, y, 18, RUIN_CARVED)
    for x in range(13, 18):
        t.set(x, 7, 18, RUIN_CARVED)
    for x in (14, 15, 16):
        for y in (4, 5, 6):
            t.veiled(x, y, 18, "ruin")
    t.mark("STAIR_MOUTH", 15, 4, 18)
    t.set(15, 10, 18, RUIN_CARVED)
    t.set(15, 12, 22, RUIN_CARVED)
    # --- the broken colonnade: heights per column (6 = whole, with capital), an architrave on the whole runs.
    columns = {(7, 4): 6, (7, 8): 6, (7, 12): 3, (7, 16): 6, (23, 4): 6, (23, 8): 2, (23, 12): 6, (23, 16): 6,
               (11, 4): 6, (15, 4): 4, (19, 4): 6, (11, 16): 6, (19, 16): 5}
    for (x, z), h in columns.items():
        t.set(x, 4, z, RUIN_CARVED)
        for y in range(5, 4 + h):
            t.set(x, y, z, RUIN if y < 9 else RUIN_CARVED)
        if h < 6:
            t.set(x, 4 + h, z, TRIM_SLAB, BOTTOM)
    for x0, z0, x1, z1 in ((7, 4, 11, 4), (19, 4, 23, 4), (7, 4, 7, 8), (23, 12, 23, 16), (7, 16, 11, 16)):
        t.box(x0, 10, z0, x1, 10, z1, RUIN)
    for x, z in ((8, 12), (9, 12), (22, 8), (22, 9), (16, 5), (20, 17)):
        t.set(x, 4, z, RUIN_CRACKED)
    t.set(9, 5, 12, TRIM_SLAB, BOTTOM)
    # --- the meditation garden: grass, flowers, a reflecting pool and two trees.
    for x in range(13, 18):
        for z in range(8, 14):
            rim = x in (13, 17) or z in (8, 13)
            t.set(x, 3, z, RUIN_CARVED if rim else "minecraft:water", None if rim else {"level": "0"})
            t.set(x, 2, z, RUIN)
    t.set(14, 4, 10, "minecraft:lily_pad")
    t.set(16, 4, 12, "minecraft:lily_pad")
    for x in range(10, 21):
        for z in range(7, 15):
            if (x, 4, z) in t.cells or 13 <= x <= 17 and 8 <= z <= 13:
                continue
            r = noise(23, x, 4, z)
            if r < 0.3:
                t.set(x, 4, z, FLOWERS[int(r * 100) % len(FLOWERS)])
    for tx, tz in ((11, 8), (19, 13)):
        for y in range(4, 8):
            t.set(tx, y, tz, "minecraft:oak_log", log("y"))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for y in (7, 8, 9):
                    r = abs(dx) + abs(dz) + (y - 7)
                    if (dx, dz) != (0, 0) or y > 7:
                        if r <= 3 and noise(31, tx + dx, y, tz + dz) > 0.25:
                            t.keep(tx + dx, y, tz + dz, "minecraft:flowering_azalea_leaves" if noise(32, dx, y, dz) < 0.3
                                   else "minecraft:azalea_leaves", LEAVES)
    # --- the worn statue at the garden's north end, facing the gate.
    t.set(15, 4, 6, RUIN_CARVED)
    for y in (5, 6, 7):
        t.set(15, y, 6, RUIN)
    t.set(14, 5, 6, TRIM_STAIRS, stairs("east"))
    t.set(16, 5, 6, TRIM_STAIRS, stairs("west"))
    t.set(14, 7, 6, TRIM_STAIRS, stairs("east", "top"))
    t.set(16, 7, 6, TRIM_STAIRS, stairs("west", "top"))
    t.set(15, 8, 6, RUIN_CRACKED)  # the face, worn away
    # --- Vasch's diggers: a lean-to, crates, a fire, a dig pit with lifted stones.
    for x, z in ((1, 5), (3, 5), (1, 8), (3, 8)):
        column(t, x, z, 4, 5, "minecraft:spruce_fence", {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
    t.box(1, 6, 5, 3, 6, 8, "minecraft:brown_wool")
    t.set(2, 6, 5, "minecraft:brown_wool")
    lantern(t, 2, 5, 6, hanging=True)
    t.chest("CHEST_1", 2, 4, 2, "south", "jedi_ruin")
    t.set(1, 4, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(1, 4, 3, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(3, 4, 2, "minecraft:barrel", {"facing": "south", "open": "false"})
    t.set(5, 4, 4, "minecraft:campfire", campfire())
    for x in range(2, 6):
        for z in range(10, 13):
            t.set(x, 3, z, "minecraft:gravel" if noise(41, x, 3, z) < 0.5 else COARSE)
            t.set(x, 2, z, DIRT)
    t.set(3, 3, 11, AIR)
    t.set(4, 3, 11, AIR)
    t.set(2, 4, 13, RUIN_CARVED)
    t.set(5, 4, 13, RUIN)
    t.set(6, 4, 12, RUIN_CRACKED)
    t.spawner("DIGGERS", 5, 4, 7, "diggers")
    # --- lumen moss in the shade of the dome and the south walk.
    for x, z in ((8, 17), (12, 21), (18, 21)):
        t.set(x, 4, z, MOSS)
    t.spot("SURFACE_GUARD", 15, 4, 1)
    # --- ground: paving in the court and the apse, forest floor around it.
    paving = ruin_paving(17)

    def top(x, z):
        if 10 <= x <= 20 and 7 <= z <= 14:
            return GRASS
        if 7 <= x <= 23 and 3 <= z <= 17:
            return paving(x, z)
        r = noise(19, x, 3, z)
        return COARSE if r < 0.12 else ("minecraft:podzol" if r < 0.2 else GRASS)
    ground_layers(t, 3, 5, top_block=top)
    clear_above(t, 4)
    return t


def build_stair():
    t = Site("jedi_ruin/stair", (7, 12, 9), 0)
    t.box(0, 0, 0, 6, 11, 8, RUIN)

    def tread(x, s, z, facing):
        for y in range(s, min(s + 3, 12)):
            t.set(x, y, z, AIR)
        if facing:
            t.set(x, s - 1, z, TRIM_STAIRS, stairs(facing))
    for i, z in enumerate(range(1, 7)):
        for x in (1, 2, 3):
            tread(x, 11 - i, z, "north")
    for x in range(1, 6):
        tread(x, 5, 7, None)
    for i, z in enumerate((6, 5, 4, 3)):
        for x in (4, 5):
            tread(x, 4 - i, z, "south")
    for z in (2, 1):
        for x in (4, 5):
            tread(x, 1, z, None)
    for x in (4, 5):
        for y in (1, 2):
            t.set(x, y, 0, AIR)
    t.set(3, 7, 7, "minecraft:lantern", LANTERN_HUNG)
    t.set(5, 3, 2, "minecraft:lantern", LANTERN_HUNG)
    t.set(0, 6, 7, RUIN_CARVED)
    t.set(0, 3, 4, RUIN_CARVED)
    t.jig(1, 11, 1, "up_south", "stair_top", "courtyard_stair", "minecraft:empty")
    t.jig(4, 1, 0, "north_up", "stair_exit", "hall_entry", "jedi_ruin/underground")
    return t


# The Listening Hall's step stones (step j at x = 5 + j, y = 2 + j on the north wall) and their wiring planes:
# z = 1 feeds (south and east lines), z = 2 the dust behind each repeater (west line too), z = 3 the repeaters,
# z = 4 the wall face (the conductor behind each step), z = 5 the step stones.
HALL_STEPS = [(5 + j, 2 + j, 5) for j in range(1, 7)]
HALL_CW = {"W": (2, 3, 13), "E": (22, 3, 13), "S": (14, 3, 22)}


def build_listening_hall():
    t = Site("jedi_ruin/listening_hall", (25, 14, 25), 0)
    t.box(0, 0, 0, 24, 13, 24, RUIN)
    t.air(3, 3, 5, 21, 11, 21)
    # Carved bands and corner pilasters.
    for x in range(3, 22):
        if not 6 <= x <= 11:
            t.set(x, 7, 4, RUIN_CARVED)
        t.set(x, 7, 22, RUIN_CARVED)
    for z in range(5, 22):
        t.set(2, 7, z, RUIN_CARVED)
        t.set(22, 7, z, RUIN_CARVED)
    # The gallery 7 up the north wall (floor y = 9, stand 10), solid below.
    t.box(12, 3, 5, 21, 9, 7, RUIN)
    t.box(12, 9, 5, 21, 9, 7, RUIN_CARVED)
    t.air(12, 10, 5, 21, 11, 7)
    t.pedestal("PEDESTAL_JEDI", 17, 10, 5, "jedi", True)
    t.chest("CHEST_1", 21, 10, 6, "west", "jedi_ruin")
    for x in (14, 20):
        t.air(x, 10, 4, x, 11, 4)
        t.spawner("SENTINEL_1" if x == 14 else "SENTINEL_2", x, 10, 4, "watcher")
        t.set(x, 12, 4, RUIN_CARVED)
    # Step stones, the repeaters and the dust behind them.
    for j, (x, y, z) in enumerate(HALL_STEPS, start=1):
        t.step_stone(x, y, z, "south")
        t.set(x, y, 4, RUIN)
        t.repeater(x, y, 3, "north")
        t.wire(x, y, 2)
    for pocket in ((6, 4, 2), (8, 6, 2), (10, 8, 2)):
        t.set(*pocket, AIR)
    # West line: alcove counterweight (front east), north inside the west wall, east along z = 2 to D1.
    cx, cy, cz = HALL_CW["W"]
    t.counterweight("COUNTERWEIGHT_W", cx, cy, cz, "east", "ruin")
    for z in range(2, 14):
        t.wire(1, 3, z)
    t.repeater(1, 3, 7, "south")
    for x in range(2, 6):
        t.wire(x, 3, 2)
    # East line: north inside the east wall, west along z = 1, then up a hidden staircase to D6.
    cx, cy, cz = HALL_CW["E"]
    t.counterweight("COUNTERWEIGHT_E", cx, cy, cz, "west", "ruin")
    for z in range(1, 14):
        t.wire(23, 3, z)
    t.repeater(23, 3, 7, "south")
    for x in range(16, 23):
        t.wire(x, 3, 1)
    t.repeater(20, 3, 1, "east")
    for i, (x, y) in enumerate(((15, 4), (14, 5), (13, 6), (12, 7), (11, 8))):
        t.wire(x, y, 1)
        t.set(x + 1, y, 1, AIR)
    # South line: down the south wall, north under the floor, then up a hidden staircase to D3.
    cx, cy, cz = HALL_CW["S"]
    t.counterweight("COUNTERWEIGHT_S", cx, cy, cz, "north", "ruin")
    t.wire(14, 3, 23)
    t.wire(13, 2, 23)
    t.set(13, 3, 23, AIR)
    t.wire(12, 1, 23)
    t.set(12, 2, 23, AIR)
    for z in range(1, 23):
        t.wire(12, 1, z)
    t.repeater(12, 1, 15, "south")
    t.repeater(12, 1, 6, "south")
    for x, y in ((11, 2), (10, 3), (9, 4), (8, 5)):
        t.wire(x, y, 1)
        t.set(x + 1, y, 1, AIR)
    # Alcoves over the three counterweights.
    for (x, y, z) in HALL_CW.values():
        t.set(x, y + 1, z, AIR)
        t.set(x, y + 2, z, AIR)
        t.set(x, y + 3, z, RUIN_CARVED)
    t.finish_wires()
    # Moss under the ledges (the fall from the gallery lands on it) and in two corners.
    for x, y, z in HALL_STEPS[1:]:
        t.set(x, 3, z, MOSS)
    t.set(3, 3, 6, MOSS)
    t.set(21, 3, 9, MOSS)
    # The veiled panel to the pilgrim's niche (east) and the open passage to the anchor cache (west).
    for y in (3, 4):
        t.veiled(22, y, 18, "ruin")
        t.set(23, y, 18, AIR)
        t.set(24, y, 18, AIR)
        for x in (0, 1, 2):
            t.set(x, y, 18, AIR)
    t.set(2, 5, 18, RUIN_CARVED)
    t.jig(24, 3, 18, "east_up", "hall_niche", "niche_entry", "jedi_ruin/niche")
    t.jig(0, 3, 18, "west_up", "hall_cache", "cache_entry", "jedi_ruin/side")
    # The entry from the stair (south wall).
    t.air(6, 3, 22, 7, 4, 24)
    t.jig(6, 3, 24, "south_up", "hall_entry", "stair_exit", "minecraft:empty")
    # Light.
    for x, z in ((8, 14), (16, 14), (12, 19)):
        t.set(x, 11, z, "minecraft:lantern", LANTERN_HUNG)
    t.set(3, 3, 21, "minecraft:lantern", LANTERN)
    t.set(21, 3, 21, "minecraft:lantern", LANTERN)
    t.spot("HEART", 12, 3, 14)
    check_redstone(t, [
        ((), ()),
        ((HALL_CW["W"],), HALL_STEPS[0:2]),
        ((HALL_CW["S"],), HALL_STEPS[2:4]),
        ((HALL_CW["E"],), HALL_STEPS[4:6]),
        (tuple(HALL_CW.values()), HALL_STEPS),
    ])
    return t


def build_jedi_niche():
    t = Site("jedi_ruin/pilgrim_niche", (7, 6, 7), 0)
    t.box(0, 0, 0, 6, 5, 6, RUIN)
    t.air(1, 1, 1, 5, 4, 5)
    for x in range(1, 6):
        t.set(x, 4, 1, RUIN_CARVED)
        t.set(x, 4, 5, RUIN_CARVED)
    t.pedestal("PEDESTAL_WAYFARER", 4, 1, 3, "wayfarer", True)
    t.set(5, 1, 3, RUIN_CARVED)
    t.set(2, 1, 1, MOSS)
    t.set(2, 1, 5, TRIM_STAIRS, stairs("north"))
    t.set(5, 3, 3, "minecraft:lantern", LANTERN_HUNG)
    t.set(0, 2, 3, AIR)
    t.jig(0, 1, 3, "west_up", "niche_entry", "hall_niche", "minecraft:empty")
    return t


def build_anchor_cache():
    t = Site("jedi_ruin/anchor_cache", (7, 6, 7), 0)
    t.box(0, 0, 0, 6, 5, 6, RUIN)
    t.air(2, 1, 1, 5, 4, 5)
    # The chest sits in the west wall behind the seal; the anchor hangs over the seal.
    t.chest("CACHE", 0, 1, 3, "east", "force_cache")
    t.set(0, 2, 3, AIR)
    t.seal(1, 1, 3, "ruin")
    t.set(1, 2, 3, ANCHOR, {"strain": "0", "age": "0"})
    t.set(1, 3, 3, RUIN_CARVED)
    t.set(5, 1, 1, MOSS)
    t.set(5, 1, 5, MOSS)
    t.set(4, 4, 3, "minecraft:lantern", LANTERN_HUNG)
    t.set(6, 2, 3, AIR)
    t.jig(6, 1, 3, "east_up", "cache_entry", "hall_cache", "minecraft:empty")
    check_redstone(t, [((), ()), (((1, 2, 3),), ((1, 1, 3),))])
    return t


# =============================================================================================
# Sith Tomb (sith_tomb)
# =============================================================================================
SOUL_LANTERN = "minecraft:soul_lantern"
TOMB_STAIRS = "minecraft:polished_blackstone_brick_stairs"
TOMB_SLAB = "minecraft:polished_blackstone_brick_slab"


def tomb_stone(seed):
    def fn(x, y, z):
        r = noise(seed, x, y, z)
        return TOMB_CRACKED if r < 0.1 else TOMB
    return fn


def build_gate():
    t = Site("sith_tomb/gate", (17, 12, 17), 3)
    stone = tomb_stone(51)
    # Wind-cut steps: the stair mouth at z = 9, steps down at z = 10 and 11, a cut floor to the exit at z = 16.
    stands = {10: 2, 11: 1, 12: 1, 13: 1, 14: 1, 15: 1, 16: 1}
    for z, s in stands.items():
        for x in (7, 8, 9):
            for y in range(s, 3):
                t.set(x, y, z, AIR)
            for y in range(0, s - 1):
                t.set(x, y, z, stone(x, y, z))
            if z in (10, 11):
                t.set(x, s - 1, z, TOMB_STAIRS, stairs("north"))
            else:
                t.set(x, s - 1, z, stone(x, s - 1, z))
        for x in (6, 10):
            for y in range(0, 3):
                t.set(x, y, z, TOMB_CARVED if y == 2 and z % 2 == 0 else stone(x, y, z))
            t.set(x, 3, z, TOMB_SLAB, BOTTOM)
    for x in (7, 8, 9):
        t.set(x, 2, 9, TOMB)
    t.mark("STAIR_MOUTH", 8, 3, 9)
    t.set(8, 3, 9, AIR)
    t.set(8, 4, 9, AIR)
    t.jig(8, 1, 16, "south_up", "gate_down", "processional_entry", "sith_tomb/underground")
    # The obelisk gateway, half sunk: two 2x2 pillars from deep in the sand, a lintel, tapering tips; the east tip lies fallen.
    for x0 in (5, 10):
        for x in (x0, x0 + 1):
            for z in (7, 8):
                for y in range(0, 8):
                    t.set(x, y, z, TOMB_CARVED if y in (3, 6) else stone(x, y, z))
    for x in range(5, 12):
        for z in (7, 8):
            t.set(x, 7, z, TOMB_CARVED)
    for x in (7, 8, 9):
        for z in (7, 8):
            for y in range(3, 7):
                t.set(x, y, z, AIR)
            t.set(x, 2, z, TOMB)
    for y in (8, 9, 10):
        t.set(5, y, 7, TOMB if y < 10 else TOMB_CARVED)
    t.set(5, 11, 7, TOMB_SLAB, BOTTOM)
    t.set(11, 8, 8, TOMB)
    for x, y, z in ((13, 3, 10), (14, 3, 10), (15, 3, 11)):
        t.set(x, y, z, TOMB_CARVED if x == 13 else TOMB)
    t.set(14, 4, 10, TOMB_SLAB, BOTTOM)
    # Paving under drifted sand around the gate.
    for x in range(3, 14):
        for z in range(3, 10):
            if (x, 2, z) not in t.cells:
                t.set(x, 2, z, stone(x, 2, z) if noise(53, x, 2, z) > 0.35 else SAND)
    for x, z in ((4, 6), (12, 5), (3, 9), (13, 9), (6, 4), (11, 3)):
        t.set(x, 3, z, SAND)
    for x, z in ((2, 12), (14, 13), (3, 14)):
        t.set(x, 3, z, "minecraft:dead_bush")
    t.spot("SURFACE_GUARD", 8, 3, 3)
    t.jig(0, 3, 4, "west_up", "gate_path", "path_in", "sith_tomb/path")

    def top(x, z):
        return SAND if noise(57, x, 2, z) > 0.15 else "minecraft:smooth_sandstone"
    ground_layers(t, 2, 59, top_block=top, under="minecraft:sandstone", deep="minecraft:sandstone")
    clear_above(t, 3)
    return t


def build_path(name, track, edge, marker, seed):
    """A terrain-matching connector (the village street pattern): layer 0 replaces the ground, layers 1-2 are clear."""
    t = Site(name, (5, 3, 9), 0)
    for x in range(5):
        for z in range(9):
            r = noise(seed, x, 0, z)
            t.set(x, 0, z, track[int(r * len(track)) % len(track)] if 1 <= x <= 3 else edge)
            t.set(x, 1, z, AIR)
            t.set(x, 2, z, AIR)
    t.set(0, 1, 2, marker)
    t.set(4, 1, 6, marker)
    t.jig(2, 1, 0, "north_up", "path_in", "gate_path" if name.startswith("sith") else "hull_path", "minecraft:empty")
    t.jig(2, 1, 8, "south_up", "path_out", "camp_entry", name.split("/")[0] + "/side")
    return t


def build_sith_path():
    return build_path("sith_tomb/path", ("minecraft:coarse_dirt", "minecraft:gravel", SAND, "minecraft:smooth_sandstone"), SAND,
                      TOMB_CARVED, 61)


def tent(t, x0, z0, x1, z1, cloth, trim):
    """A ridge tent over x0..x1 (ridge along x) and z0..z1 (5 wide), its door in the west end, a plank floor."""
    zc = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.set(x, 0, z, "minecraft:spruce_planks")
            h = 3 - abs(z - zc)
            for y in range(1, h + 1):
                if y == h:
                    t.set(x, y, z, cloth if x not in (x0, x1) else trim)
                elif x in (x0, x1):
                    t.set(x, y, z, trim)
                else:
                    t.set(x, y, z, AIR)
    t.set(x0, 1, zc, AIR)
    t.set(x0, 2, zc, AIR)


def build_dig_camp():
    t = Site("sith_tomb/dig_camp", (19, 8, 19), 1)
    # Ground: trampled sand, a dig trench with lifted tomb stone, Imperial tents and crates.
    for x in range(19):
        for z in range(19):
            r = noise(71, x, 0, z)
            t.set(x, 0, z, SAND if r > 0.3 else ("minecraft:coarse_dirt" if r > 0.12 else "minecraft:gravel"))
    tent(t, 2, 2, 8, 6, "minecraft:gray_wool", "minecraft:red_wool")
    tent(t, 2, 11, 8, 15, "minecraft:gray_wool", "minecraft:red_wool")
    t.chest("CHEST_1", 7, 1, 4, "west", "sith_tomb")
    t.set(7, 1, 3, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(7, 1, 12, "minecraft:white_bed", {"facing": "south", "occupied": "false", "part": "foot"})
    t.set(7, 1, 13, "minecraft:white_bed", {"facing": "south", "occupied": "false", "part": "head"})
    for x, z in ((11, 3), (12, 3), (11, 4), (16, 15), (16, 16), (15, 16)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(12, 2, 3, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(11, 1, 9, "minecraft:campfire", campfire())
    t.set(10, 1, 8, "minecraft:spruce_slab", BOTTOM)
    t.set(12, 1, 10, "minecraft:spruce_slab", BOTTOM)
    for x in range(12, 17):
        for z in range(11, 14):
            t.set(x, 0, z, "minecraft:gravel")
    t.set(13, 0, 12, AIR)
    t.set(14, 0, 12, AIR)
    t.set(15, 0, 12, TOMB_CARVED)
    t.set(13, 1, 14, TOMB_CRACKED)
    t.set(16, 1, 11, TOMB)
    for y in range(1, 5):
        t.set(16, y, 6, "minecraft:spruce_fence", {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
    t.set(16, 4, 7, "minecraft:lantern", LANTERN_HUNG)
    t.set(16, 5, 7, "minecraft:spruce_fence", {"east": "false", "west": "false", "north": "true", "south": "false", "waterlogged": "false"})
    t.set(16, 5, 6, "minecraft:spruce_fence", {"east": "false", "west": "false", "north": "false", "south": "true", "waterlogged": "false"})
    t.spawner("SQUAD", 11, 1, 7, "dig_team")
    t.set(18, 2, 9, AIR)
    t.jig(18, 1, 9, "east_up", "camp_entry", "path_out", "minecraft:empty")
    clear_above(t, 1)
    return t


PROC_STAND = {}
for _z in range(27):
    PROC_STAND[_z] = (10 if _z <= 3 else 9 if _z <= 5 else 8 if _z <= 7 else 7 if _z <= 9 else 6 if _z <= 11
                      else 5 if _z <= 14 else 4 if _z <= 16 else 3 if _z <= 19 else 2)
PROC_VENTS = {"VENT_1": (3, 6, 9), "VENT_2": (5, 4, 13), "VENT_3": (3, 2, 18), "VENT_4": (5, 1, 22)}


def proc_ceiling(z):
    s = PROC_STAND[z]
    return 11 if z <= 5 else min(10, s + 5)


def build_processional():
    t = Site("sith_tomb/processional", (9, 14, 27), 0)
    stone = tomb_stone(81)
    for z in range(27):
        s = PROC_STAND[z]
        if z <= 3:
            # The open trench that continues the gate's cut.
            for x in range(9):
                for y in range(0, 12):
                    if 2 <= x <= 6 and y >= s:
                        t.set(x, y, z, AIR)
                    else:
                        t.set(x, y, z, TOMB_CARVED if y == 11 and x in (1, 7) else stone(x, y, z))
                for y in (12, 13):
                    if 2 <= x <= 6:
                        t.set(x, y, z, AIR)
            continue
        top = proc_ceiling(z)
        for x in range(9):
            for y in range(0, top + 1):
                if 2 <= x <= 6 and s <= y < top:
                    t.set(x, y, z, AIR)
                else:
                    t.set(x, y, z, stone(x, y, z))
        if z % 4 == 0:
            for y in range(s, top):
                t.set(1, y, z, TOMB_CARVED)
                t.set(7, y, z, TOMB_CARVED)
    # Lintel where the trench dives under the ground.
    for x in range(1, 8):
        t.set(x, 11, 4, TOMB_CARVED)
    # Vents in the floor, urns along the walls, the sentinel at the far end and Vasch's diggers on the first landing.
    for anchor, pos in PROC_VENTS.items():
        t.vent(anchor, *pos)
    for x, z in ((2, 5), (6, 7), (6, 14), (2, 16), (2, 19)):
        t.set(x, PROC_STAND[z], z, FUNERARY)
    t.spawner("DIGGERS", 4, 6, 10, "diggers")
    t.spawner("SENTINEL_1", 4, 2, 24, "tomb")
    for x, z in ((4, 13), (4, 21), (4, 8)):
        t.set(x, proc_ceiling(z) - 1, z, SOUL_LANTERN, LANTERN_HUNG)
    # The rubble room door (west, landing at stand 5) and the veiled tomb stone to the pilgrim's niche (east, stand 3).
    for y in (5, 6):
        t.set(0, y, 13, AIR)
        t.set(1, y, 13, AIR)
    t.jig(0, 5, 13, "west_up", "proc_rubble", "rubble_entry", "sith_tomb/side")
    for y in (3, 4):
        t.veiled(7, y, 18, "tomb")
        t.set(8, y, 18, AIR)
    t.mark("NICHE", 7, 3, 18)
    t.jig(8, 3, 18, "east_up", "proc_niche", "niche_entry", "sith_tomb/niche")
    # Entry (from the gate) and exit (to the burial chamber).
    t.jig(4, 10, 0, "north_up", "processional_entry", "gate_down", "minecraft:empty")
    for x in (3, 4, 5):
        for y in (2, 3, 4):
            t.set(x, y, 26, AIR)
    t.jig(4, 2, 26, "south_up", "proc_exit", "chamber_entry", "sith_tomb/underground")
    return t


CHAMBER_KEEPER = (12, 2, 18)
CHAMBER_URNS = [(5, 3, 8), (21, 3, 8), (5, 3, 16), (21, 3, 16)]


def build_burial_chamber():
    t = Site("sith_tomb/burial_chamber", (27, 14, 27), 0)
    stone = tomb_stone(91)
    t.box(0, 0, 0, 26, 13, 26, stone)
    t.air(2, 2, 2, 24, 11, 24)
    # A stepped vault: the ceiling rises toward the middle.
    for x in range(2, 25):
        for z in range(2, 25):
            edge = min(x - 2, 24 - x, z - 2, 24 - z)
            if edge == 0:
                t.set(x, 11, z, stone(x, 11, z))
                t.set(x, 10, z, TOMB_CARVED)
            elif edge == 1:
                t.set(x, 11, z, stone(x, 11, z))
    for x in range(2, 25):
        for z in range(2, 25):
            if x % 4 == 0 and z % 4 == 0:
                t.set(x, 1, z, TOMB_CARVED)
    # Entry balcony at stand 6 (from the processional) and the stair down to the floor (stand 2).
    for x in (12, 13, 14):
        for y in (6, 7, 8):
            t.set(x, y, 0, AIR)
            t.set(x, y, 1, AIR)
    t.jig(13, 6, 0, "north_up", "chamber_entry", "proc_exit", "minecraft:empty")
    t.box(10, 2, 2, 16, 5, 3, stone)
    for x in (10, 16):
        t.set(x, 6, 3, TOMB_SLAB, BOTTOM)
    for i, z in enumerate((4, 5, 6)):
        for x in (12, 13, 14):
            top = 4 - i
            for y in range(2, top):
                t.set(x, y, z, stone(x, y, z))
            t.set(x, top, z, TOMB_STAIRS, stairs("north"))
    # Pillars.
    for x, z in ((6, 6), (20, 6), (6, 20), (20, 20)):
        for y in range(2, 11):
            t.set(x, y, z, TOMB_CARVED if y in (2, 10) else TOMB)
    # The inner sanctum (7x6x7) behind the keeper's wall; the seal and the pedestal sit beside the keeper.
    t.box(9, 1, 18, 15, 6, 24, TOMB)
    t.air(10, 2, 19, 14, 5, 23)
    kx, ky, kz = CHAMBER_KEEPER
    offsets = []
    for (ux, uy, uz) in CHAMBER_URNS:
        offsets += [ux - kx, uy - ky, uz - kz]
    t.set(kx, ky, kz, KEEPER, {"facing": "north", "solved": "false"}, {"id": KEEPER, "urns": IntArray(offsets)})
    t.mark("URN_KEEPER", kx, ky, kz)
    t.set(kx, ky + 1, kz, TOMB_CARVED)
    t.seal(13, 2, 18, "tomb")
    t.seal(13, 3, 18, "tomb")
    t.pedestal("PEDESTAL_SITH", 12, 2, 19, "sith", False)
    t.chest("CHEST_1", 14, 2, 22, "west", "sith_tomb")
    t.set(10, 2, 23, TOMB_CARVED)
    t.set(12, 4, 23, SOUL_LANTERN, LANTERN_HUNG)
    # The sarcophagus dais in front of the keeper.
    for x in range(9, 16):
        for z in (14, 15, 16):
            t.set(x, 2, z, TOMB_CARVED if z == 14 or x in (9, 15) else TOMB)
    for x in (11, 12, 13):
        t.set(x, 3, 15, TOMB_CARVED)
        t.set(x, 4, 15, TOMB_SLAB, BOTTOM)
    # Four echo urns on plinths around the room.
    for i, (x, y, z) in enumerate(CHAMBER_URNS):
        t.set(x, y - 1, z, TOMB_CARVED)
        t.set(x, y, z, ECHO_URN, {"facing": "north", "index": str(i)})
        t.mark(f"URN_{i + 1}", x, y, z)
    t.spawner("SENTINEL_1", 8, 2, 12, "tomb")
    t.spawner("SENTINEL_2", 17, 2, 12, "tomb")
    for x, z in ((3, 3), (23, 3), (23, 23)):
        t.set(x, 2, z, FUNERARY)
    for x, z in ((6, 13), (20, 13), (13, 8)):
        t.set(x, 10, z, SOUL_LANTERN, LANTERN_HUNG)
    t.spot("HEART", 13, 2, 10)
    check_redstone(t, [((), ()), ((CHAMBER_KEEPER,), ((13, 2, 18), (13, 3, 18), (12, 2, 19)))])
    return t


def build_sith_niche():
    t = Site("sith_tomb/pilgrim_niche", (7, 6, 7), 0)
    stone = tomb_stone(95)
    t.box(0, 0, 0, 6, 5, 6, stone)
    t.air(1, 1, 1, 5, 4, 5)
    for z in range(1, 6):
        t.set(5, 4, z, TOMB_CARVED)
    t.pedestal("PEDESTAL_WAYFARER", 4, 1, 3, "wayfarer", True)
    t.set(5, 1, 3, TOMB_CARVED)
    t.set(1, 1, 1, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    t.set(4, 3, 3, SOUL_LANTERN, LANTERN_HUNG)
    t.set(0, 2, 3, AIR)
    t.jig(0, 1, 3, "west_up", "niche_entry", "proc_niche", "minecraft:empty")
    return t


def build_rubble_room():
    t = Site("sith_tomb/rubble_room", (9, 7, 9), 0)
    stone = tomb_stone(97)
    t.box(0, 0, 0, 8, 6, 8, stone)
    t.air(1, 1, 1, 6, 5, 7)
    for y in (1, 2):
        t.set(7, y, 4, RUBBLE, {"cleared": "false", "age": "0"})
        t.set(8, y, 4, AIR)
    t.chest("CACHE", 1, 1, 4, "east", "force_cache")
    t.set(1, 1, 1, FUNERARY)
    t.set(1, 1, 7, FUNERARY)
    for x, y, z in ((5, 1, 2), (6, 1, 2), (6, 2, 2), (5, 1, 6), (4, 5, 4), (5, 5, 5)):
        t.set(x, y, z, TOMB_CRACKED)
    t.set(6, 1, 6, "minecraft:cobweb")
    t.set(3, 4, 4, SOUL_LANTERN, LANTERN_HUNG)
    t.jig(8, 1, 4, "east_up", "rubble_entry", "proc_rubble", "minecraft:empty")
    return t


# =============================================================================================
# Wreck Field (wreck_field)
# =============================================================================================
HULL_ZC, HULL_YC, HULL_A, HULL_B, HULL_ROLL = 9.0, 7.6, 8.7, 7.7, 0.28
HULL_INNER = 0.6
HULL_CW = (31, 4, 7)
HULL_SEAL = [(32, 4, 8), (32, 5, 8)]
HULL_PEDESTAL = (33, 4, 7)
HULL_LOCKER_ANCHOR = (31, 5, 11)
HULL_LOCKER_SEAL = (31, 4, 11)


def hull_e(z, y):
    """The tilted frigate's cross-section: < HULL_INNER inside, HULL_INNER..1 the plating, > 1 outside."""
    dz, dy = z - HULL_ZC, y - HULL_YC
    u = dz * math.cos(HULL_ROLL) + dy * math.sin(HULL_ROLL)
    v = -dz * math.sin(HULL_ROLL) + dy * math.cos(HULL_ROLL)
    return (u / HULL_A) ** 2 + (v / HULL_B) ** 2


def plating(seed):
    def fn(x, y, z):
        r = noise(seed, x, y, z)
        return SCORCHED if r < 0.12 else PLATE
    return fn


def build_hull():
    t = Site("wreck_field/hull", (41, 18, 19), 4)
    plate = plating(101)
    bulkheads = (4, 13, 14, 31, 32, 36)
    for x in range(41):
        for y in range(18):
            for z in range(19):
                e = hull_e(z, y)
                if e > 1:
                    continue
                if x < 4 or x > 36:
                    # The torn ends: ragged plating that thins out toward the break.
                    keep = 1.0 - (4 - x if x < 4 else x - 36) * 0.22
                    if e >= HULL_INNER and noise(103, x, y, z) < keep and y >= 2:
                        t.set(x, y, z, plate(x, y, z))
                    elif e >= HULL_INNER or y <= 3:
                        t.set(x, y, z, plate(x, y, z) if y <= 3 else AIR)
                    else:
                        t.set(x, y, z, AIR)
                elif x in bulkheads or e >= HULL_INNER or y <= 3:
                    t.set(x, y, z, plate(x, y, z))
                else:
                    t.set(x, y, z, AIR)
    # Zone A (x 5-12): the breach in the north side, a torn roof, two of Vasch's scavengers.
    for x in (8, 9, 10):
        for y in (4, 5, 6):
            for z in range(0, 6):
                if t.get(x, y, z) is not None:
                    t.set(x, y, z, AIR)
    for x in range(6, 12):
        for y in range(12, 18):
            for z in range(19):
                if t.get(x, y, z) is not None and hull_e(z, y) >= HULL_INNER and noise(107, x, 0, z) < 0.45:
                    t.set(x, y, z, AIR)
    t.spawner("SCAVENGER_1", 7, 4, 11, "scavengers")
    t.spawner("SCAVENGER_2", 11, 4, 6, "scavengers")
    t.set(6, 4, 8, SALVAGE)
    t.set(12, 4, 12, SCORCHED)
    t.set(12, 5, 12, PLATE)
    # The veiled plating over the hatch (bulkhead x 13-14).
    for y in (4, 5):
        for z in (9, 10):
            t.veiled(13, y, z, "wreck")
            t.set(14, y, z, AIR)
    t.mark("HATCH", 13, 4, 9)
    # Zone B (x 15-30): the cargo hold, the torn deck 5 up, the counterweight and the locker in the far bulkhead.
    for x in range(22, 31):
        for z in range(19):
            if t.get(x, 8, z) == AIR and hull_e(z, 9) < HULL_INNER:
                hole = noise(109, x, 8, z) < 0.18 or x == 22 and z % 3 == 0
                if not hole or (x, z) in ((29, 13), (28, 13), (24, 6), (27, 10)):
                    t.set(x, 8, z, plate(x, 8, z))
    t.chest("DECK_CACHE", 29, 9, 13, "west", "wreck_field")
    t.set(24, 9, 6, SALVAGE)
    t.set(27, 9, 10, SALVAGE)
    t.set(17, 4, 12, SALVAGE)
    for x, z in ((16, 5), (16, 6), (17, 5), (20, 13), (20, 14)):
        t.set(x, 4, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(16, 5, 5, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.counterweight("COUNTERWEIGHT", *HULL_CW, "west", "wreck")
    for (x, y, z) in HULL_SEAL:
        t.seal(x, y, z, "wreck")
        t.set(x - 1, y, z, AIR)
    t.set(*HULL_LOCKER_SEAL, SEAL, {"open": "false", "skin": "wreck", "age": "0"})
    t.set(*HULL_LOCKER_ANCHOR, ANCHOR, {"strain": "0", "age": "0"})
    t.chest("ANCHOR_LOCKER", 32, 4, 11, "west", "force_cache")
    t.set(32, 5, 11, AIR)
    t.set(33, 4, 11, PLATE)
    t.set(33, 5, 11, PLATE)
    t.spot("HEART", 22, 4, 9)
    # Zone C (x 33-35): the Wayfarer pedestal compartment behind the hatch seal.
    t.pedestal("PEDESTAL_WAYFARER", *HULL_PEDESTAL, "wayfarer", False)
    t.set(35, 4, 8, SCORCHED)
    # Sparking conduits along the inner plating, a few lamps of the old ship still lit.
    for x in range(5, 36):
        if x in bulkheads:
            continue
        for y in range(5, 15):
            for z in range(19):
                if t.get(x, y, z) in (PLATE, SCORCHED) and hull_e(z, y) >= HULL_INNER and noise(113, x, y, z) < 0.035:
                    if any(t.get(*step_of((x, y, z), d)) == AIR and hull_e(*step_of((x, y, z), d)[2:0:-1]) < HULL_INNER
                           for d in ("north", "south", "up", "down")):
                        t.set(x, y, z, CONDUIT)
    for x, z in ((18, 9), (26, 9), (34, 9)):
        y = max(y for y in range(18) if t.get(x, y, z) == AIR and hull_e(z, y) < HULL_INNER)
        t.set(x, y, z, "minecraft:lantern", LANTERN_HUNG)
    # Gun emplacement stubs on the spine.
    for x in (17, 26):
        t.set(x, 16, 9, SCORCHED)
        t.set(x, 16, 10, PLATE)
        t.set(x + 1, 16, 9, "minecraft:lightning_rod", {"facing": "east", "powered": "false", "waterlogged": "false"})
    # Connections: scars at both ends (terrain-matching), the path to the salvage camp from the north side.
    for x, orientation in ((0, "west_up"), (40, "east_up")):
        for y in (4, 5):
            t.set(x, y, 9, AIR)
        t.jig(x, 4, 9, orientation, "hull_scar", "scar_entry", "wreck_field/side")
    t.set(20, 5, 0, AIR)
    t.jig(20, 4, 0, "north_up", "hull_path", "path_in", "wreck_field/path")
    t.spot("SURFACE_GUARD", 24, 4, 0)
    # Ground: scorched furrows along the crash line.
    def top(x, z):
        r = noise(117, x, 3, z)
        furrow = abs(z - 9 - (x - 20) * 0.1) < 1.2 and r < 0.6
        return COARSE if furrow or r < 0.15 else ("minecraft:gravel" if r < 0.22 else GRASS)
    ground_layers(t, 3, 119, top_block=top)
    clear_above(t, 4)
    t.finish_wires()
    check_redstone(t, [
        ((), ()),
        ((HULL_CW,), (HULL_SEAL[0], HULL_PEDESTAL)),
        ((HULL_LOCKER_ANCHOR,), (HULL_LOCKER_SEAL,)),
    ])
    return t


def build_wreck_path():
    return build_path("wreck_field/path", ("minecraft:coarse_dirt", "minecraft:gravel", "minecraft:dirt_path", "minecraft:dirt_path"),
                      GRASS, SCORCHED, 121)


def build_salvage_camp():
    t = Site("wreck_field/salvage_camp", (15, 8, 15), 1)
    for x in range(15):
        for z in range(15):
            r = noise(131, x, 0, z)
            t.set(x, 0, z, GRASS if r > 0.35 else ("minecraft:dirt_path" if r > 0.15 else COARSE))
    tent(t, 1, 1, 7, 5, "minecraft:white_wool", "minecraft:blue_wool")
    for z in (2, 4):
        t.set(3, 1, z, "minecraft:white_bed", {"facing": "east", "occupied": "false", "part": "foot"})
        t.set(4, 1, z, "minecraft:white_bed", {"facing": "east", "occupied": "false", "part": "head"})
    t.chest("CHEST_1", 6, 1, 3, "west", "wreck_field")
    for x, z in ((10, 2), (11, 2), (10, 3), (12, 9), (12, 10)):
        t.set(x, 1, z, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(10, 2, 2, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(9, 1, 7, "minecraft:campfire", campfire())
    for x, y, z in ((3, 1, 9), (4, 1, 9), (3, 2, 9), (3, 1, 10), (5, 1, 11)):
        t.set(x, y, z, SCORCHED if (x + z) % 2 else PLATE)
    t.set(4, 1, 10, SALVAGE)
    t.set(2, 1, 12, CONDUIT)
    for y in range(1, 5):
        t.set(13, y, 6, "minecraft:spruce_fence", {"east": "false", "west": "false", "north": "false", "south": "false", "waterlogged": "false"})
    t.set(13, 5, 6, "minecraft:blue_wool")
    t.set(13, 5, 7, "minecraft:blue_wool")
    t.set(13, 4, 7, "minecraft:lantern", LANTERN_HUNG)
    t.spawner("SQUAD", 7, 1, 8, "salvage_crew")
    t.set(7, 2, 14, AIR)
    t.jig(7, 1, 14, "south_up", "camp_entry", "path_out", "minecraft:empty")
    clear_above(t, 1)
    return t


def build_scar():
    """Three blast craters with torn plating; terrain-matching, so layer 0 replaces the ground surface."""
    t = Site("wreck_field/scar", (15, 6, 15), 0)
    craters = ((4, 4, 3.2), (10, 9, 4.0), (4, 11, 2.4))
    for x in range(15):
        for z in range(15):
            d = min(math.hypot(x - cx, z - cz) / r for cx, cz, r in craters)
            r = noise(141, x, 0, z)
            if d < 0.55:
                t.set(x, 0, z, AIR)
            elif d < 1.0:
                t.set(x, 0, z, COARSE if r < 0.6 else "minecraft:gravel")
            elif d < 1.35:
                t.set(x, 0, z, COARSE if r < 0.5 else GRASS)
                if r < 0.25:
                    t.set(x, 1, z, COARSE)
            if d < 1.35:
                for y in range(1, 4):
                    t.keep(x, y, z, AIR)
    for x, y, z in ((9, 1, 8), (9, 2, 8), (10, 1, 7), (11, 1, 11), (11, 2, 11), (11, 3, 11), (12, 1, 10)):
        t.set(x, y, z, SCORCHED if y > 1 else PLATE)
    t.set(5, 1, 5, SCORCHED)
    t.set(3, 1, 3, SALVAGE)
    t.set(13, 1, 3, CONDUIT)
    t.jig(7, 1, 0, "north_up", "scar_entry", "hull_scar", "minecraft:empty")
    return t


# =============================================================================================
# Outposts (runtime and worldgen): outpost/republic_post, outpost/imperial_post
# =============================================================================================
OUTPOST_TERMINAL = (11, 1, 9)
OUTPOST_MAST_LAMP = (11, 11, 13)  # terminal + 10 up + 4 toward the terminal's back (SiteAmbience's mast blink)
OUTPOST_GARRISON = [(4, 1, 4), (18, 1, 4), (4, 1, 18), (18, 1, 18)]


def banner(t, x, y, z, facing, base, pattern_color, pattern="oldrepublic:republic_chevron"):
    """A wall banner with one pattern layer (BannerBlockEntity `patterns`, API-CHECK in the report)."""
    t.set(x, y, z, f"minecraft:{base}_wall_banner", {"facing": facing},
          {"id": "minecraft:banner", "patterns": gs.TagList(gs.TAG_COMPOUND, [{"pattern": pattern, "color": pattern_color}])})


def outpost_common(t, army, floor, wall, trim, post):
    """The compound both armies share: a floor, a perimeter wall with a north gate, the terminal on the plaza,
    the officer's spot before it, four garrison posts, the command block to the south."""
    sx, sy, sz = t.size
    for x in range(sx):
        for z in range(sz):
            inside = 2 <= x <= 20 and 2 <= z <= 20
            t.set(x, 0, z, floor(x, z) if inside else ("minecraft:gravel" if noise(151, x, 0, z) < 0.4 else COARSE))
    for x in range(1, 22):
        for z in range(1, 22):
            if x in (1, 21) or z in (1, 21):
                if z == 1 and 9 <= x <= 13:
                    continue
                t.set(x, 1, z, wall)
                t.set(x, 2, z, trim if (x + z) % 4 == 0 else wall)
    for x in (8, 14):
        for y in range(1, 5):
            t.set(x, y, 1, post)
        t.set(x, 5, 1, "minecraft:lantern", LANTERN)
    x, y, z = OUTPOST_TERMINAL
    t.set(x, y, z, TERMINAL, {"faction": army, "facing": "north"}, {"id": TERMINAL})
    t.mark("TERMINAL", x, y, z)
    t.spot("OFFICER", 11, 1, 7)
    for i, (gx, gy, gz) in enumerate(OUTPOST_GARRISON, start=1):
        t.spot(f"GARRISON_{i}", gx, gy, gz)


def build_republic_post():
    t = Site("outpost/republic_post", (23, 12, 23), 1)
    panel_floor = lambda x, z: REP_PANEL if (x + z) % 3 else "minecraft:smooth_stone"
    outpost_common(t, "republic", panel_floor, REP_PANEL, "minecraft:white_concrete", "minecraft:iron_block")
    # Command block (x 6-16, z 12-20): medbay to the west, requisition rack to the east, the mast on the roof.
    t.box(6, 1, 12, 16, 4, 20, REP_PANEL)
    t.air(7, 1, 13, 15, 3, 19)
    t.box(6, 5, 12, 16, 5, 20, "minecraft:smooth_stone_slab", BOTTOM)
    for x in (6, 16):
        for z in (12, 20):
            column(t, x, z, 1, 5, "minecraft:white_concrete")
    for x in (10, 11, 12):
        for y in (1, 2):
            t.set(x, y, 12, AIR)
    for x in (8, 14):
        t.set(x, 2, 12, "minecraft:glass_pane", {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
    for z in (14, 16, 18):
        t.set(7, 1, z, "minecraft:white_bed", {"facing": "east", "occupied": "false", "part": "foot"})
        t.set(8, 1, z, "minecraft:white_bed", {"facing": "east", "occupied": "false", "part": "head"})
    t.set(7, 1, 19, "minecraft:brewing_stand", {"has_bottle_0": "false", "has_bottle_1": "false", "has_bottle_2": "false"})
    for z in range(14, 20):
        t.set(15, 1, z, "minecraft:barrel", {"facing": "west", "open": "false"})
        if z % 2 == 0:
            t.set(15, 2, z, "minecraft:barrel", {"facing": "west", "open": "false"})
    t.set(13, 1, 19, "minecraft:smithing_table")
    t.set(11, 3, 16, "minecraft:lantern", LANTERN_HUNG)
    # Navy chevron banners either side of the door.
    banner(t, 9, 3, 11, "north", "blue", "white")
    banner(t, 13, 3, 11, "north", "blue", "white")
    # The terminal's canopy and the sandbagged corners.
    for x in (10, 11, 12):
        t.set(x, 4, 9, "minecraft:smooth_stone_slab", BOTTOM)
    column(t, 10, 9, 1, 3, "minecraft:iron_bars", bars())
    column(t, 12, 9, 1, 3, "minecraft:iron_bars", bars())
    for cx, cz in ((3, 3), (19, 3)):
        for dx in (-1, 0, 1):
            t.set(cx + dx, 1, cz + 2, "minecraft:white_concrete_powder" if dx else "minecraft:light_gray_concrete_powder")
    # The mast and its lamp (SiteAmbience blinks it).
    lx, ly, lz = OUTPOST_MAST_LAMP
    for y in range(6, ly):
        t.set(lx, y, lz, "minecraft:iron_bars", bars(north=y == 10, south=y == 10))
    t.set(lx, ly, lz, "minecraft:sea_lantern")
    t.set(lx, 10, lz + 1, "minecraft:iron_bars", bars(north=True))
    t.set(lx, 10, lz - 1, "minecraft:iron_bars", bars(south=True))
    clear_above(t, 1)
    return t


def build_imperial_post():
    t = Site("outpost/imperial_post", (23, 14, 23), 1)
    tiles = lambda x, z: "minecraft:deepslate_tiles" if (x + z) % 2 else IMP_PANEL
    outpost_common(t, "imperial", tiles, "minecraft:polished_blackstone_bricks", "minecraft:red_nether_bricks", "minecraft:polished_basalt")
    # Command block in blackstone with crimson trim; the holding cell to the west, the rack to the east.
    t.box(6, 1, 12, 16, 5, 20, "minecraft:polished_blackstone_bricks")
    t.air(7, 1, 13, 15, 4, 19)
    t.box(6, 6, 12, 16, 6, 20, "minecraft:polished_blackstone_slab", BOTTOM)
    for x in range(6, 17):
        t.set(x, 5, 12, "minecraft:crimson_planks" if x % 2 else IMP_PANEL)
    for x in (6, 16):
        for z in (12, 20):
            column(t, x, z, 1, 6, "minecraft:polished_basalt", {"axis": "y"})
    for x in (10, 11, 12):
        for y in (1, 2, 3):
            t.set(x, y, 12, AIR)
    for z in range(13, 18):
        t.set(10, 1, z, "minecraft:iron_bars", bars(north=z > 13, south=z < 17))
        t.set(10, 2, z, "minecraft:iron_bars", bars(north=z > 13, south=z < 17))
    for x in range(7, 10):
        t.set(x, 1, 17, "minecraft:iron_bars", bars(east=x < 9, west=x > 7))
        t.set(x, 2, 17, "minecraft:iron_bars", bars(east=x < 9, west=x > 7))
    for z in range(14, 20):
        t.set(15, 1, z, "minecraft:barrel", {"facing": "west", "open": "false"})
        if z % 2:
            t.set(15, 2, z, "minecraft:barrel", {"facing": "west", "open": "false"})
    t.set(13, 1, 19, "minecraft:smithing_table")
    t.set(12, 4, 16, SOUL_LANTERN, LANTERN_HUNG)
    banner(t, 9, 4, 11, "north", "black", "red", "minecraft:rhombus")
    banner(t, 13, 4, 11, "north", "black", "red", "minecraft:rhombus")
    for x in (10, 11, 12):
        t.set(x, 4, 9, "minecraft:polished_blackstone_slab", BOTTOM)
    column(t, 10, 9, 1, 3, "minecraft:polished_basalt", {"axis": "y"})
    column(t, 12, 9, 1, 3, "minecraft:polished_basalt", {"axis": "y"})
    # The gate's crimson-lit lintel.
    for x in range(8, 15):
        t.set(x, 5, 1, "minecraft:shroomlight" if x == 11 else "minecraft:red_nether_bricks")
    for x in (8, 14):
        column(t, x, 1, 1, 4, "minecraft:polished_basalt", {"axis": "y"})
    # Crimson trim along the wall top and the sensor spire on the roof.
    for x in range(1, 22):
        for z in (1, 21):
            if t.get(x, 2, z) is not None:
                t.set(x, 3, z, "minecraft:polished_blackstone_slab", BOTTOM)
    for y in range(7, 12):
        t.set(11, y, 16, "minecraft:polished_basalt" if y < 10 else "minecraft:basalt", {"axis": "y"})
    t.set(11, 12, 16, "minecraft:end_rod", {"facing": "up"})
    t.set(11, 13, 16, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    for dx, dz, f in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
        t.set(11 + dx, 10, 16 + dz, "minecraft:end_rod", {"facing": f})
    t.set(11, 9, 16, "minecraft:crimson_planks")
    clear_above(t, 1)
    return t


# =============================================================================================
# Echo Shrines: site/echo_shrine_{jedi,sith,wayfarer}
# =============================================================================================
SHRINE_KINDS = {
    "jedi": {"skin": "ruin", "stone": RUIN, "carved": RUIN_CARVED, "cracked": RUIN_CRACKED, "group": "watcher", "count": 0,
             "stairs": TRIM_STAIRS, "light": "minecraft:lantern", "ground": GRASS},
    "sith": {"skin": "tomb", "stone": TOMB, "carved": TOMB_CARVED, "cracked": TOMB_CRACKED, "group": "tomb", "count": 0,
             "stairs": TOMB_STAIRS, "light": SOUL_LANTERN, "ground": COARSE},
    "wayfarer": {"skin": "ruin", "stone": "minecraft:tuff_bricks", "carved": "minecraft:chiseled_tuff_bricks",
                 "cracked": "minecraft:mossy_stone_bricks", "group": "scavengers", "count": 2,
                 "stairs": "minecraft:tuff_brick_stairs", "light": "minecraft:lantern", "ground": GRASS},
}
SHRINE_CW = (6, 3, 11)
SHRINE_PEDESTAL = (7, 1, 12)
SHRINE_SEAL = [(7, 1, 11), (7, 2, 11)]


def build_shrine(kind):
    k = SHRINE_KINDS[kind]
    t = Site("site/echo_shrine_" + kind, (15, 14, 15), 9)
    stone, carved = k["stone"], k["carved"]
    t.box(0, 0, 0, 14, 7, 14, lambda x, y, z: k["cracked"] if noise(161, x, y, z) < 0.08 else stone)
    for x in range(15):
        for z in range(15):
            t.set(x, 8, z, k["ground"] if noise(163, x, 8, z) > 0.2 else COARSE)
    # The chamber (9x6x9, floor 8 below the surface).
    t.air(4, 1, 4, 10, 4, 10)
    for x, z in ((4, 4), (10, 4), (4, 10), (10, 10)):
        column(t, x, z, 1, 4, carved)
    for x in range(4, 11):
        for z in range(4, 11):
            if (x + z) % 2 == 0:
                t.set(x, 0, z, carved)
    # The stair: from under the hatch east along z = 1-2, then south to a landing and the chamber's east door.
    for i, x in enumerate(range(6, 13)):
        s = 8 - i
        for z in (1, 2):
            for y in range(s, min(s + 3, 8)):
                t.set(x, y, z, AIR)
            t.set(x, s - 1, z, k["stairs"], stairs("west"))
    for x in (12, 13):
        for z in (3, 4, 5):
            for y in (1, 2, 3):
                t.set(x, y, z, AIR)
    for z in (4, 5):
        for y in (1, 2):
            t.set(11, y, z, AIR)
    t.set(13, 3, 5, k["light"], LANTERN_HUNG)
    # The veiled hatch under the broken arch.
    for x in (6, 7):
        for z in (1, 2):
            t.veiled(x, 8, z, k["skin"])
    for x in range(3, 12):
        for z in range(0, 5):
            if t.get(x, 8, z) != VEILED:
                t.set(x, 8, z, stone if noise(167, x, 8, z) > 0.3 else carved)
    for y in range(9, 13):
        t.set(5, y, 3, stone)
        if y < 11:
            t.set(9, y, 3, stone)
    for x in range(5, 8):
        t.set(x, 13, 3, carved)
    t.set(8, 12, 3, k["stairs"], stairs("west", "top"))
    t.set(10, 9, 4, k["cracked"])
    t.set(11, 9, 3, stone)
    t.set(10, 9, 2, k["stairs"], stairs("north"))
    # The pedestal behind its seal; the counterweight beside it powers both through the block over the pedestal.
    pk = {"jedi": "jedi", "sith": "sith", "wayfarer": "wayfarer"}[kind]
    t.pedestal("PEDESTAL", *SHRINE_PEDESTAL, pk, False)
    for (x, y, z) in SHRINE_SEAL:
        t.seal(x, y, z, k["skin"])
    t.counterweight("COUNTERWEIGHT", *SHRINE_CW, "north", k["skin"])
    t.set(7, 2, 12, carved)  # the junction block over the pedestal
    t.wire(6, 3, 12)
    t.wire(7, 3, 12)
    t.finish_wires()
    t.spawner("GUARD", 5, 1, 8, k["group"], count=k["count"])
    t.spot("HEART", 8, 1, 7)
    t.set(9, 4, 9, k["light"], LANTERN_HUNG)
    t.set(5, 4, 5, k["light"], LANTERN_HUNG)
    clear_above(t, 9)
    check_redstone(t, [((), ()), ((SHRINE_CW,), (SHRINE_PEDESTAL, SHRINE_SEAL[1]))])
    return t


# =============================================================================================
# Tables: templates, pools, structures, processors
# =============================================================================================
TEMPLATES = [
    (build_courtyard, (31, 14, 31), ["STAIR_MOUTH", "SURFACE_GUARD", "DIGGERS", "CHEST_1"]),
    (build_stair, (7, 12, 9), []),
    (build_listening_hall, (25, 14, 25), ["HEART", "PEDESTAL_JEDI", "COUNTERWEIGHT_W", "COUNTERWEIGHT_E", "COUNTERWEIGHT_S",
                                          "SENTINEL_1", "SENTINEL_2", "CHEST_1"]),
    (build_jedi_niche, (7, 6, 7), ["PEDESTAL_WAYFARER"]),
    (build_anchor_cache, (7, 6, 7), ["CACHE"]),
    (build_gate, (17, 12, 17), ["STAIR_MOUTH", "SURFACE_GUARD"]),
    (build_sith_path, (5, 3, 9), []),
    (build_dig_camp, (19, 8, 19), ["SQUAD", "CHEST_1"]),
    (build_processional, (9, 14, 27), ["VENT_1", "VENT_2", "VENT_3", "VENT_4", "SENTINEL_1", "DIGGERS", "NICHE"]),
    (build_burial_chamber, (27, 14, 27), ["HEART", "URN_KEEPER", "URN_1", "URN_2", "URN_3", "URN_4", "PEDESTAL_SITH",
                                          "SENTINEL_1", "SENTINEL_2", "CHEST_1"]),
    (build_sith_niche, (7, 6, 7), ["PEDESTAL_WAYFARER"]),
    (build_rubble_room, (9, 7, 9), ["CACHE"]),
    (build_hull, (41, 18, 19), ["HEART", "HATCH", "COUNTERWEIGHT", "PEDESTAL_WAYFARER", "DECK_CACHE", "ANCHOR_LOCKER",
                                "SCAVENGER_1", "SCAVENGER_2", "SURFACE_GUARD"]),
    (build_wreck_path, (5, 3, 9), []),
    (build_salvage_camp, (15, 8, 15), ["SQUAD", "CHEST_1"]),
    (build_scar, (15, 6, 15), []),
    (build_republic_post, (23, 12, 23), ["TERMINAL", "OFFICER", "GARRISON_1", "GARRISON_2", "GARRISON_3", "GARRISON_4"]),
    (build_imperial_post, (23, 14, 23), ["TERMINAL", "OFFICER", "GARRISON_1", "GARRISON_2", "GARRISON_3", "GARRISON_4"]),
    (lambda: build_shrine("jedi"), (15, 14, 15), ["HEART", "PEDESTAL", "COUNTERWEIGHT", "GUARD"]),
    (lambda: build_shrine("sith"), (15, 14, 15), ["HEART", "PEDESTAL", "COUNTERWEIGHT", "GUARD"]),
    (lambda: build_shrine("wayfarer"), (15, 14, 15), ["HEART", "PEDESTAL", "COUNTERWEIGHT", "GUARD"]),
]

RUIN_W = MOD + "jedi_ruin_weathering"
TOMB_W = MOD + "sith_tomb_weathering"
WRECK_W = MOD + "wreck_weathering"


def adapted(yard_stand, clear_from, support_depth, ramp_rings, clear_inset=0):
    return {"yard_stand": yard_stand, "clear_from": clear_from, "clear_inset": clear_inset, "support_depth": support_depth,
            "ramp_rings": ramp_rings}


POOLS = {
    "jedi_ruin/start": [Element("jedi_ruin/courtyard", RIGID, RUIN_W, adapted(4, 14, 8, 2))],
    "jedi_ruin/underground": [Element("jedi_ruin/stair", RIGID, RUIN_W), Element("jedi_ruin/listening_hall", RIGID, RUIN_W)],
    "jedi_ruin/side": [Element("jedi_ruin/anchor_cache", RIGID, RUIN_W)],
    "jedi_ruin/niche": [Element("jedi_ruin/pilgrim_niche", RIGID, RUIN_W)],
    "sith_tomb/start": [Element("sith_tomb/gate", RIGID, TOMB_W, adapted(3, 12, 8, 2))],
    "sith_tomb/path": [Element("sith_tomb/path", TERRAIN)],
    "sith_tomb/side": [Element("sith_tomb/dig_camp", RIGID, TOMB_W, adapted(1, 8, 3, 1)), Element("sith_tomb/rubble_room", RIGID, TOMB_W)],
    "sith_tomb/underground": [Element("sith_tomb/processional", RIGID, TOMB_W), Element("sith_tomb/burial_chamber", RIGID, TOMB_W)],
    "sith_tomb/niche": [Element("sith_tomb/pilgrim_niche", RIGID, TOMB_W)],
    "wreck_field/start": [Element("wreck_field/hull", RIGID, WRECK_W, adapted(4, 18, 8, 2))],
    "wreck_field/path": [Element("wreck_field/path", TERRAIN)],
    "wreck_field/side": [Element("wreck_field/salvage_camp", RIGID, WRECK_W, adapted(1, 8, 3, 1)), Element("wreck_field/scar", TERRAIN, WRECK_W)],
    "republic_outpost/start": [Element("outpost/republic_post", RIGID, "minecraft:empty", adapted(1, 12, 3, 2))],
    "imperial_outpost/start": [Element("outpost/imperial_post", RIGID, "minecraft:empty", adapted(1, 14, 3, 2))],
}

STRUCTURES = [
    Structure("jedi_ruin", "jedi_ruin/start", 5, 64,
              {"jedi_ruin/courtyard": 1, "jedi_ruin/stair": 1, "jedi_ruin/listening_hall": 1, "jedi_ruin/pilgrim_niche": 1,
               "jedi_ruin/anchor_cache": 1}, {MOSS: (10, 14)}),
    Structure("sith_tomb", "sith_tomb/start", 4, 80,
              {"sith_tomb/gate": 1, "sith_tomb/path": 1, "sith_tomb/dig_camp": 1, "sith_tomb/processional": 1,
               "sith_tomb/burial_chamber": 1, "sith_tomb/pilgrim_niche": 1, "sith_tomb/rubble_room": 1}, {FUNERARY: (8, 10)}),
    Structure("wreck_field", "wreck_field/start", 3, 64,
              {"wreck_field/hull": 1, "wreck_field/path": 1, "wreck_field/salvage_camp": 1, "wreck_field/scar": 2}, {SALVAGE: (6, 8)}),
    Structure("republic_outpost", "republic_outpost/start", 1, 32, {"outpost/republic_post": 1}, {}),
    Structure("imperial_outpost", "imperial_outpost/start", 1, 32, {"outpost/imperial_post": 1}, {}),
]

PROCESSORS = {
    # Rules apply in order with one position-seeded random, so the second probability is of what the first left:
    # 0.85 x 0.235 = 20% mossy overall beside 15% cracked.
    "jedi_ruin_weathering": [(RUIN, RUIN_CRACKED, 0.15), (RUIN, RUIN_MOSSY, 0.235)],
    "sith_tomb_weathering": [(TOMB, TOMB_CRACKED, 0.2)],
    "wreck_weathering": [(PLATE, SCORCHED, 0.3)],
}


# =============================================================================================
# JSON writers
# =============================================================================================
def write_json(path, value):
    if gs.rebuilt_in_m6(path):
        return
    text = json.dumps(value, indent=2, sort_keys=True) + "\n"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        out.write(text)


def pool_json(elements):
    out = []
    for e in elements:
        element = {
            "element_type": "oldrepublic:adapted_pool_element" if e.adapted else "minecraft:single_pool_element",
            "location": MOD + e.template,
            "processors": e.processors,
            "projection": e.projection,
        }
        if e.adapted:
            element.update(e.adapted)
        out.append({"element": element, "weight": e.weight})
    return {"elements": out, "fallback": "minecraft:empty"}


def structure_json(structure, templates):
    start = templates[POOLS[structure.start_pool][0].template]
    return {
        "type": "minecraft:jigsaw",
        "biomes": "#oldrepublic:has_structure/" + structure.name,
        "liquid_settings": "ignore_waterlogging",
        "max_distance_from_center": structure.max_distance,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": structure.size,
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": []}},
        # Vanilla moves a projected start so that its layer 1 (the ground level delta) sits on the first free
        # block plus this height: 1 - buried puts layer `buried` (the feet layer) there. API-CHECK in the report.
        "start_height": {"absolute": 1 - start.buried},
        "start_pool": MOD + structure.start_pool,
        "step": structure.step,
        "terrain_adaptation": "none",
        "use_expansion_hack": False,
    }


def processor_json(rules):
    return {"processors": [{"processor_type": "minecraft:rule", "rules": [{
        "input_predicate": {"block": block, "predicate_type": "minecraft:random_block_match", "probability": p},
        "location_predicate": {"predicate_type": "minecraft:always_true"},
        "output_state": {"Name": out},
    } for block, out, p in rules]}]}


# =============================================================================================
# Main
# =============================================================================================
def main():
    templates = {}
    for builder, size, expected in TEMPLATES:
        t = builder()
        check_template(t, size, expected)
        t.expected = expected
        templates[t.name] = t
    for name, elements in POOLS.items():
        for e in elements:
            if e.template not in templates:
                raise SystemExit(f"pool {name} names an unknown template {e.template}")
    for structure in STRUCTURES:
        pieces = assemble(structure, POOLS, templates)
        if structure.name == "sith_tomb":
            main_route = sum(templates[p[0]].count(FUNERARY) for p in pieces
                             if p[0] in ("sith_tomb/gate", "sith_tomb/processional", "sith_tomb/burial_chamber"))
            if not 8 <= main_route <= 10:
                raise SystemExit(f"sith_tomb: {main_route} funerary urns on the main route, expected 8-10")
        print(f"{structure.name}: " + ", ".join(f"{p[0]} at {p[1]} {p[2]} (depth {p[3]})" for p in pieces))
    for name, t in templates.items():
        n, p = t.save(os.path.join(DATA, "structure", name + ".nbt"))
        expected = t.expected
        sidecar(os.path.join(DATA, "site_anchors", name + ".json"), {a: t.anchors[a] for a in expected})
        print(f"{name}: size {t.size}, {n} blocks, {p} palette entries, anchors {', '.join(expected) or '-'}")
    for name, elements in POOLS.items():
        write_json(os.path.join(DATA, "worldgen", "template_pool", name + ".json"), pool_json(elements))
    for structure in STRUCTURES:
        write_json(os.path.join(DATA, "worldgen", "structure", structure.name + ".json"), structure_json(structure, templates))
    for name, rules in PROCESSORS.items():
        write_json(os.path.join(DATA, "worldgen", "processor_list", name + ".json"), processor_json(rules))


if __name__ == "__main__":
    main()
