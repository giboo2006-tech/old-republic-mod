# Building structures (the builder's guide)

This guide is for anyone who builds or rebuilds a structure template for the mod. It covers the toolkit
(`tools/assets/structlib`), the conventions the Java code relies on, the quality bar every structure
meets, the art rules and the checks to run. The Milestone 6 plan (which structures, where they go, who
builds what) is `docs/MILESTONE_6.md`.

Contents

1. What a structure is made of
2. The quality bar
3. Art rules
4. The toolkit
5. Conventions the game relies on
6. Worldgen structures
7. Runtime sites and site meta
8. Checking and previewing your work
9. Budgets and performance

---

## 1. What a structure is made of

- **The template** is a gzipped NBT file at `src/main/resources/data/oldrepublic/structure/<path>.nbt`
  (DataVersion 4903, Minecraft 26.2). Unset cells are left out of the file, so the world keeps whatever
  is there; an explicit `minecraft:air` cell clears the world (rooms, doorways, the space above a floor).
- **The anchor sidecar** at `data/oldrepublic/site_anchors/<path>.json` names cells for the Java side,
  as `{"ANCHOR": [[x, y, z], ...]}` in unrotated template-local coordinates. Names must be members of
  `com.oldrepublic.world.site.SiteAnchors.Anchor` (unknown names are skipped with a warning).
- **Site meta** at `data/oldrepublic/site_meta/<path>.json` (section 7) tells the runtime placer how to
  sit the template in the ground. Every template that Java places at runtime has one.
- **Worldgen JSON** (section 6) for structures that generate with the world: a structure, its pools,
  a structure set, a biome tag, optional processor lists and chest loot tables.
- **The generator script** that wrote all of the above. Templates are never edited by hand: a script
  under `tools/assets/structures/<area>/` writes them, so a rebuild is one command and the output is
  byte-identical every run (all randomness goes through the template's seeded `t.rng` or `Noise(seed)`).

Template-local axes: x east, y up, z south. A runtime site's entrance is on its **north side (z = 0)**;
the placer turns the template so the entrance faces the player's approach.

## 2. The quality bar

The goal: a player who walks up to any structure should feel they are in a different game. Every
template meets all of these before it is done.

1. **Silhouette.** It reads from 100 blocks away: varied heights, a landmark element (a tower, a hull,
   a dome, a mast, an arch, a crane), no single-height boxes. Ruins keep the outline of what they were.
2. **Layered, weathered materials.** Walls have depth: a plinth or footing, a body, trim and a cap;
   pillars and buttresses stand proud, windows and doors are recessed. At least three materials per
   surface family, mixed by noise patches (not per-cell salt and pepper). Metal is streaked and
   scorched, stone is cracked and mossy where water would run, wood is weathered where exposed.
3. **Depth and detail at player scale.** Look at every wall from 5 blocks away: is there anything to
   see? Use stairs, slabs, walls, trapdoors, bars, chains, buttons and panes to break flat faces.
4. **Ruin logic.** Damage has a cause and a direction: a collapsed section falls one way and its rubble
   lies below the gap; roots come through cracks; sand drifts against the windward side; scorch marks
   radiate from a blast. Intact parts look intact.
5. **Story props.** Every structure tells you who was here and what happened: a half-eaten meal, a
   toppled chair, a jammed blast door, a dead campfire, an unfinished repair, crates in the middle of
   being loaded. No empty rooms.
6. **Furnished interiors.** Every room you can enter has furniture and a purpose you can read:
   bunks, tables, consoles, shelves, crates, tool racks, a stove, a med bay, a cell.
7. **Mood lighting.** Interiors are lit well enough that monsters do not spawn (light level 8 or more on
   every floor cell a player can reach), with pools of light and shadow rather than even floodlight.
   Tech lights for tech, flame for the primitive, the cold blue of kyber for the Force.
8. **Terrain blending.** Nothing floats and nothing is a box dropped on a hill: footings and support
   columns reach the ground, the edges step down to the terrain, a path or a debris trail leads out
   10 to 20 blocks, the ground around is worn (coarse dirt, gravel, path blocks, ash, sand) and
   scattered with small things. Buried pieces show only what would show.
9. **Scale.** Doors 2 wide and 3 high (blast doors bigger), corridors at least 3 wide, halls at least
   5 high, ceilings at least 3 above every floor, stairs at most 1 rise per step. A player must be able
   to walk every route a story beat or puzzle needs without jumping or breaking blocks.
10. **Variety.** Worldgen structures come in at least two variants (different templates in the start
    pool, or several side pieces), and repeated elements (tents, huts, crates) are never identical
    copies: vary their size, material, damage and contents.
11. **Readable for play.** Entrances are obvious, the route to an objective is lit or signposted by
    the architecture, anchor cells are on solid ground with two air cells above, and chests and
    spawners are where the story says they are.

The review loop: build, render the preview sheet (section 8), **look at it**, fix what reads as flat,
empty, floating or repetitive, render again. Expect three passes for a landmark.

## 3. Art rules

These are standing rules for the whole mod.

- **No ground circles, sigils, halos, glyphs or glowing runes**, in blocks or in particles. No circular
  floor inlays, no rune rings, no glowing symbols carved in walls. A ring of standing stones or round
  room is fine; a pattern drawn on the floor is not.
- Atmosphere comes from light shafts, dust, overgrowth, scorch marks, weathering and sound, never from
  magic decoration.
- The Force is shown with kyber crystals, worn stone, meditation spaces and quiet; the dark side with
  black stone, red light from below, cold and decay. Nothing is labelled.
- Text: no signs with writing in templates (the mod has no translatable sign text). Banners use the
  mod's patterns or plain colours.

## 4. The toolkit

Import from a script in `tools/assets/structures/<area>/`:

```python
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
from structlib import Template, S, AIR, shapes, nature, palettes, mod, meta, worldgen as W
```

- **`Template(sx, sy, sz, seed)`** (`structlib/template.py`): `set`, `get`, `fill` (a state, a
  callable `f(x, y, z)` or a palette picker), `replace_all`, `air` (clear the world), `clear` (leave the
  world), `paste` (another template, rotated or mirrored, with its anchors), `anchor(name, x, y, z)`,
  `chest(x, y, z, facing, loot)`, `jigsaw(...)`, `entity(...)`, `finish()` (joins fences, walls, panes
  and bars and shapes stair corners; `save` runs it) and `save(template_path, preview=png)`.
- **`S(name, **props)`** builds a state string: `S('oak_stairs', facing='north', half='top')`. A bare
  name is `minecraft:`; `or:` is `oldrepublic:`.
- **`shapes`**: `box`, `walls`, `room`, `frame`, `line`, `path`, `disk`, `cylinder`, `ellipsoid`,
  `dome`, `cone`, `arch`, `pillar`, `stair_run`, `spiral_stair`, `gable_roof`, `hip_roof`, `flat_roof`,
  `foundation`. Every `state` argument also takes a callable, so one call can mix materials.
- **`nature`**: `weather` (swap blocks for aged variants, in noise patches), `decay` (knock blocks out
  of a ruin, more near the top, with rubble below), `overgrow` (vines, moss, leaves, lichen, roots),
  `scatter` (small things on the ground), `drift` (sand, ash or snow piled against walls), `Noise`.
  Anchored cells, jigsaws, chests and spawners are never touched.
- **`palettes.Palette`**: named roles with weighted choices; `picker(role, rng)` mixes per cell and
  `patches(role, seed)` mixes in coherent patches. `palettes` also holds the shared palettes for the
  build kit (`TECH`, `REPUBLIC`, `IMPERIAL`, `SCRAP`, `JEDI_STONE`, `SITH_STONE`, `WATCH_STONE`,
  `ASH_STONE`, `ADOBE`, `STORMSTONE`); copy one with `with_roles` to tune it.
- **`mod`** (block entities of the mod): `spawner(t, x, y, z, group, count=0, bound=False, anchor=None)`,
  `hub_spawner(t, x, y, z, role, anchor=None)`, `pedestal(t, x, y, z, kind, always_ready=False,
  anchor=None, fixed_id=None)`, `urn_keeper(t, x, y, z, urns, facing)`, `banner(t, x, y, z, facing,
  base, patterns)`, `lectern(t, x, y, z, facing, book=False)`, `spot(t, anchor, x, y, z)` (an anchor
  on two cells of air), `light_fill(t, region=None, level=8, strength=13)` (adds invisible
  `minecraft:light` blocks until every floor cell in the region has light `level`; it returns the cells
  it lit) and the lists `SPAWNER_GROUPS` and `HUB_ROLES`.
- **`meta.write(template_path, **fields)`** writes the site meta file (section 7).
- **`worldgen`**: `element`, `pool`, `structure`, `structure_set` (with `placement='clear_of_origin'`
  and `min_distance` for planets), `processor_list`, `biome_tag`.
- **`jigsaw.assemble(start_pool, templates, max_distance)`** simulates jigsaw assembly on flat ground
  for every start rotation and every candidate (the vanilla rules: facing, joint, target name, no box
  overlap, inside the max distance) and returns the piece list, so you can check a multi-piece
  structure before it ever runs in game.
- **`preview`**: `python3 tools/assets/structlib/preview.py <file.nbt> -o out.png` renders an
  isometric sheet from two sides plus floor plans (and cutaways of buried pieces). `--views se,nw,ne`,
  `--cut 5,9`, `--plans 1,6`.

## 5. Conventions the game relies on

- **Anchors** mark where people stand, what Java finds and where spawns happen. A feet anchor (people,
  guards, the HEART of most sites) is a cell of air with air above it, standing on a solid block; use
  `mod.spot`. A block anchor (STONE, PEDESTAL, a chest, a spawner) is the block's own cell.
- **Keep every anchor a template already has** (Java and the tests read them), unless the plan says
  otherwise. You may add anchors from the enum; ask for a new name if none fits.
- **Spawners** (`oldrepublic:site_spawner`) spawn their group once when a player comes within 32
  blocks (48 for an owned site). Groups: `dig_team`, `salvage_crew`, `diggers`, `scavengers`, `watcher`,
  `tomb`, `security_droids`, `hollowhorns`, `ash_hounds`, `cartel_enforcers`, `stormbacks`,
  `scour_brood`, `hask_warriors`, `hymnsworn`, `storm_callers`. The cell above a spawner is air.
- **Hub people** stand on `oldrepublic:hub_spawner` blocks with a role (`host`, `apprentice`, `acolyte`,
  `guard`, `droid`, `bartender`, `doorman`, `enforcer`, `warrior`, the named NPCs and
  `vendor:<vendor id>`), and spawn within 64 blocks.
- **Loot chests** name a table `oldrepublic:chests/<...>`; barrels never carry loot.
- **Puzzle and mechanism blocks** (`veiled_stone`, `counterweight_stone`, `step_stone`, `echo_urn`,
  `urn_keeper`, `pull_anchor`, `collapsed_rubble`, `puzzle_seal`, `holocron_pedestal`, `tomb_vent`,
  `sealed_plating`, `waystone*`, `heartstone`, `verse_stone`, `resonance_pylon`, `reliquary_seal`,
  `hoard_plinth`, `holdfast_gate`, `camp_gate`) are unbreakable and work by their position: never move
  one in a template whose plan says "keep the mechanism".
- **Ids are never renamed.** A rebuilt template keeps its path; Java and the tests find it by name.
- **Jigsaw blocks** face out of their template, are paired by name and target, and keep the cell above a
  horizontal pair clear. `final_state` is `minecraft:air`.
- **Entities** in templates are avoided (the game spawns people and creatures itself).

## 6. Worldgen structures

- A structure is `minecraft:jigsaw` with `project_start_to_heightmap: WORLD_SURFACE_WG`,
  `terrain_adaptation: none` and `start_height {"absolute": 1 - buried}`. Terrain is fitted by the
  mod's pool element `oldrepublic:adapted_pool_element` (`worldgen.element(..., adapted=dict(...))`):
  - `support_depth`: footing columns filled down to solid ground, at most this deep (8 by default).
  - `clear_from`, `clear_inset`: terrain above template-local height `clear_from` (normally the
    template's height) is removed, `clear_inset` from the edges.
  - `yard_stand`, `ramp_rings`: the outer `ramp_rings` rings step down from the yard at local y
    `yard_stand` toward lower ground outside.
- `max_distance_from_center` is at most 128, and every piece must fit inside it (vanilla silently
  drops pieces that do not). Check with `jigsaw.assemble`.
- Spacing and separation are in chunks; every structure set has its own salt from the plan's range.
- **Planet structure sets use `"type": "oldrepublic:clear_of_origin"`**, a random spread that never
  starts within `min_distance` blocks (default 256) of (0, 0), where the planet's hub stands.
- Biome tags live at `tags/worldgen/biome/has_structure/<id>.json`. Overworld ones use vanilla biome
  tags (`#minecraft:is_forest`) or biome ids; planet ones use the mod's biomes.
- Naming: a new structure `oldrepublic:<world>/<name>` (`wilds`, `selvaren`, `varkhast`, `ghessar`,
  `tyrvann`) keeps every file in the same sub-path: templates `structure/<world>/<name>/*.nbt`, pools
  `worldgen/template_pool/<world>/<name>/*.json`, the structure `worldgen/structure/<world>/<name>.json`,
  the set `worldgen/structure_set/<world>/<name>.json`, the biome tag
  `tags/worldgen/biome/has_structure/<world>/<name>.json`, processors
  `worldgen/processor_list/<world>/<name>*.json` and chests `loot_table/chests/<world>/<name>*.json`.

## 7. Runtime sites and site meta

Story, mission and planet sites are placed by Java (`RuntimeSites`, `FarSites`). The placer finds
ground, turns the template so its north side faces the player's approach, sets it `buried` layers into
the ground, fits the terrain and records it. Site meta tells it how:

```json
{
  "buried": 1,
  "max_slope": 4,
  "clearance": 8,
  "support_depth": 8,
  "ramp_rings": 2,
  "clear_above": 6,
  "terrain": "flat",
  "biomes": "#oldrepublic:has_structure/selvaren/watch_cloister",
  "spawn_radius": 64,
  "links": {"oldrepublic:site/hymnal": [-11, -28, 9]}
}
```

Every field is optional (the values above, except `biomes` and `links`, are the defaults).

| Field | Meaning |
|---|---|
| `buried` | the feet layer: how many layers sit below the ground the player walks on |
| `max_slope` | the most the ground may vary under the footprint before the placer looks elsewhere |
| `clearance` | blocks kept clear of other sites and built blocks around the footprint |
| `support_depth` | footings filled down to solid ground, at most this deep |
| `ramp_rings` | outer rings stepped down toward lower ground |
| `clear_above` | trees and terrain removed above the template's top, this many blocks |
| `terrain` | where far placement prefers to put it: `any`, `flat`, `high` (ridges, hilltops) or `low` (valleys, shores) |
| `biomes` | a biome tag far placement keeps to |
| `spawn_radius` | how close the owner comes before the site's people spawn |
| `links` | companion templates placed with this one and their origin offsets (unrotated) |

## 8. Checking and previewing your work

- `python3 tools/check_m6_structures.py` checks every template, sidecar, meta file and worldgen file:
  block ids exist (vanilla 26.2 or the mod), properties are sane, anchors are enum names inside the
  template, required anchors exist, feet anchors have air above, spawner groups and hub roles are
  known, chests name existing loot tables, pools name existing templates, sets have unique salts,
  biome tags exist, sizes are at most 128, and runtime templates have meta. It also holds every
  template to `tools/m6_baseline.json` (anchors and sizes that code relies on, and the mechanism
  cells of the puzzle rooms, which must stay exactly where they are) and checks block ids against
  `tools/vanilla_blocks_26_2.txt`. `--only <path prefix>` checks part of the tree; `--report` lists
  problems without failing (CI uses it until the rebuilt structures are merged).
- Render previews into your scratch folder, not into the repository, and look at them.
- In game: `/oldrepublic site place <template> ~ ~ ~` stamps a runtime template where you stand and
  `/place structure oldrepublic:<id>` generates a worldgen structure.

## 9. Budgets and performance

- At most 128 blocks on every axis, and at most about 60,000 non-air cells per template. Bigger
  structures are several jigsaw pieces.
- Light sources: enough to keep floors at light 8; `mod.light_fill` adds invisible light blocks where
  the architecture cannot.
- Block entities (chests, spawners, banners, lecterns, campfires): use what the scene needs.
- Avoid huge volumes of explicit air above open ground; clear only what must be cleared (rooms,
  doorways, the space under roofs) and let `clear_above` and the pool element's `clear_from` handle
  terrain above the footprint.
