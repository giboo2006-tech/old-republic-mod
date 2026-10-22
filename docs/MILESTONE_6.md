# Milestone 6: the structures overhaul

Gabriel's request (22 October): "Redo the structures they need to be better bigger and not like spawning
in 50 blocks away from where you get the quest from make it realistic, more random generated
structures, make them detailed really put the effort I want to feel like I'm playing a different game".

So this milestone does three things:

1. **Every structure is rebuilt** bigger, more detailed and to one quality bar (`docs/STRUCTURES.md`,
   section 2), with a new build kit of blocks so the worlds look like the Old Republic, not like
   Minecraft with a coat of paint.
2. **Quest, story and mission sites go out into the world** at realistic distances (hundreds of blocks,
   chosen in terrain that suits them) instead of the 12 to 144 blocks the loaded-chunk searches allow
   today, through a new far-placement service.
3. **Many new random structures** generate with every world: about ten in the home world's wilds and
   four or five more on each planet.

Contents

1. Scope and cut order
2. How it should feel
3. Far placement
4. The build kit
5. The structure catalogue
6. Worldgen rules
7. Frozen Phase 0 contracts
8. Ownership and workers
9. Tests
10. Docs
11. Revision notes

---

## 1. Scope and cut order

In scope: every template under `data/oldrepublic/structure/` except `station/spindle` and the gametest
fixtures; every runtime placement of a quest, story, mission or planet site; new worldgen structures in
the overworld and on the four planets; a build kit of 64 blocks; the checks and tests that go with them.

Out of scope: the Silent Spindle (space), new quests, new enemies, new spawner groups, new NPC roles,
vanilla villages, the travel camps a summoned mentor brings (they stay beside the player by design),
the hunters' ambushes and Vasch's counter-hunt (they are meant to happen where the player is).

If time runs short, cut from the bottom of this list first:

1. The far-placement service and every quest, story and mission site moved out (Gabriel's first point).
2. The rebuilt story and mission sites, the outposts and the overworld landmarks (what every player sees).
3. The build kit with final textures.
4. The new overworld structures (at least six of the eight).
5. The rebuilt planet sites, hubs and dungeons.
6. The new planet structures (at least three per planet).
7. The stretch structures (marked "stretch" in section 5).

## 2. How it should feel

- Walking out of a hub or away from a quest giver, the player crosses real country to reach the site:
  the journal, the Calling or the tracker says roughly how far and which way, and the site is a
  landmark they see from a distance before they arrive.
- Wandering anywhere, the player keeps finding things: a homestead with its vaporator, a crashed
  starfighter in a ravine, a Watch hermit's tower on a ridge, a battlefield of rusted walkers, a stilt
  village in the swamp. Each world has its own architecture, materials and story.
- Every structure meets the quality bar and the art rules of `docs/STRUCTURES.md` (sections 2 and 3).
  Builders render previews and look at them; the lead's acceptance test is the in-game screenshot tour
  (section 9).

## 3. Far placement

### 3.1 What changes

Today both search engines (`MeetingSites.find`, `RuntimeSites.placeSurface`) need the chunks under a
candidate to be loaded, so no site can be further than the view distance (about 144 blocks), and their
near fallbacks (24 to 80 blocks, then 6 to 24 blocks straight ahead) decide whenever the terrain is
rough. The new service `com.oldrepublic.world.site.far.FarSites` separates *where* from *when*:

1. **Choose** a target in a distance band without loading anything: candidates on rings in the band,
   each scored from the world generator's own noise heights
   (`ChunkGenerator.getBaseHeight(x, z, WORLD_SURFACE_WG | OCEAN_FLOOR_WG, level, randomState)`), its
   noise biome (`BiomeSource.getNoiseBiome(..., randomState.sampler())`), its flatness under the
   footprint, the terrain preference, the bearing preference, the distance from other sites and from
   spawn, and whether a worldgen structure can start nearby (`ChunkGeneratorStructureState
   .hasStructureChunkInRange`, filtered by the structure's biomes). Time-sliced: at most
   `far.samplesPerTick` height samples per server tick, shared by all requests. Deterministic: seeded
   by the request key and the world seed.
2. **Load** the area: a ticket of the mod's own type `oldrepublic:far_site` (FLAG_LOADING only, no
   simulation) around the target, sized from the template plus its clearance and the search slack.
3. **Place** precisely once loaded: the normal surface search around the target (rings 0, 8, 16 and 24,
   the template's own rules from its site meta), then a clearing search that cuts trees, then a forced
   placement at the target with full terrain adaptation. The template's entrance faces `from`. The
   placement is recorded in `WorldSites` as today. Trees and terrain above are cleared
   (`clear_above`), footings reach the ground (`support_depth`) and the edges step down
   (`ramp_rings`), using the same adaptation as the worldgen pool element.
4. **Linger, then release**: the ticket stays `far.lingerTicks` after placing, then goes.

The chosen target is saved (global attachment `oldrepublic:far_sites`), so a restart in the middle
resumes with the same target, and a repeated request with the same key never places twice.

While a request is CHOOSING or LOADING, guidance points at the target; once PLACED it points at the
site's anchor, as now. On FAILED (no dry ground anywhere in the band and twice beyond it, or the
template is missing) the caller falls back to its current near search, so the game never blocks.

### 3.2 Distances

New balance keys and values. A range `[a, b]` is the distance band from the named origin. Every key
lives in the balance class of the feature that owns it.

| Site | Key | Value | From | Terrain | Notes |
|---|---|---|---|---|---|
| The Crossing (Chapter Two meeting) | `story.crossingRange` | [350, 600] | the player | flat | new template `story/crossing` |
| Raider stronghold, "Smoke on the Ridge" | `story.strongholdRange` | [450, 750] | the mentor's camp (Independent: the player) | high | rebuilt `story/raider_camp` |
| Mentor's encampment stamp | `story.encampmentStampDistance` | 64 | the Crossing | | overlay stamped once the player is this far away |
| Courier's Fall | `chapter_three.fallRange` | [500, 800] | the player | flat | |
| Echo Shrine | `chapter_three.shrineRange` | [400, 700] | the player | any | `chapter_three.firstEchoRadius` 768 becomes 1024 |
| The Reliquary | `chapter_three.reliquaryRange` | [600, 900] | the player | any | the hall is sunk below the surface piece as now |
| The hulk's cradle | `ship.cradleRange` | [250, 450] | the giver (camp or Courier's Fall) | flat | replaces {32, 64} |
| Runtime outposts | `sites.outpostRange` | [400, 700] | the player | high | `outpostWorldgenRadius` 640 → 1024, `outpostReuseRadius` 512 → 768 |
| Stamped missions | `missions.siteRange` | [300, 600] | the giver | from the template | never under `missions.siteMinRange` 200; `siteSpawnDistance` 48 → 64 |
| Planet beast cull | `planets.cullRange` | [200, 400] | the player | any | was 48 to 96 with no checks |
| Expeditions | `planets.expeditionRings` | {400, 525, 650} | the hub | | was {300, 375, 450} |
| Tessaly's Wayfarer cache | `planets.cacheRings` | {350, 450, 550} | the hub | | was {150, 200, 250} |
| The Heartstone | `planets.heartstoneRing` | {250, 325, 400} | the hub | | was {80, 100, 120} |
| Debtor camp | `planets.debtorCampRings` | {400, 550, 700} | the hub | | was {200, 300, 400} |
| Detention post | `planets.detentionRings` | {400, 525, 650} | the hub | | was {120, 160, 200} |
| Verse stones | `planets.verseRings` | {350, 500, 650} | the hub | | was {250, 350, 450} |
| The matriarch's hunting ground | `chapter_five.matriarchRange` | [300, 500] | Saltgate | | was [150, 250] |

Unchanged: the Hymnal ({600, 750, 900}), Kaast's hunting camp ({400, 500, 600}), the hunters' ambushes,
echo cairns and caches (they ring the camp or the Fall), Kaast's beacon, landing pads, the mentors'
travel camps.

Planet sites (`PlanetSites.ensure`) stop waiting for the owner to come within 160 blocks: they go
through `FarSites` (with the ring point as the band's centre and the hub as `from`) as soon as they
are ensured, so the site exists before the player sees that part of the world.

Far-placement tuning (`SiteBalance`, prefix `far.`): `samplesPerTick` 64, `anglesPerRing` 24,
`ringsPerBand` 4, `searchSlack` 24, `maxTicketRadius` 7 (chunks), `lingerTicks` 1200,
`loadTimeoutTicks` 2400, `separation` 96 (from any other recorded site), `structureRange` 3 (chunks).

### 3.3 Telling the player

`FarSites.describe(from, to)` returns "about 550 blocks to the north-east" (rounded to 50; "close by"
under 100). Every message that sends a player to a far site gives it: the mentor's brief, the
mission's "site found" message, the journal entry, the Courier's Fall and Reliquary summons. The mission
tracker shows the rough distance next to its arrow. Lang keys: `message.oldrepublic.far.about`
("about %s blocks %s"), `message.oldrepublic.far.close`, `message.oldrepublic.far.dir.<n|ne|e|se|s|sw|w|nw>`.
Text that says "nearby" for a far site is corrected (the placement survey lists them).

### 3.4 Site meta

`data/oldrepublic/site_meta/<template path>.json` (format in `docs/STRUCTURES.md` 7), loaded on server
start and on `/reload` like the anchors. `SiteMeta.of(template)` returns the file's values or the
defaults. Every runtime template has one, written by the template's builder. Java reads `buried`,
the footprint (the template's larger side), `clearance`, `max_slope`, the adaptation fields, `terrain`
and `biomes` from it instead of the constants it keeps today (`EchoShrines.BURIED`,
`ChapterThreeOpenings.FALL_*`, `MissionSites.SITE_BURIED`, the `PlanetSites` buried table,
`Ships.CRADLE_*`, `OutpostPlacement.FOOTPRINT` and the rest), keeping each constant only as the
fallback when no meta exists. `links` gives the offsets of companion templates (the Proving's
Sepulchre, the Hymnal's halls).

### 3.5 Story flow changes

- **The Crossing.** `Crossing.chooseMeeting` requests `story/crossing` through FarSites. The meeting
  point becomes the placed site's `HEART`; the mentors stand at `MENTOR_JEDI` and `MENTOR_SITH`.
- **The mentor's encampment.** After the player joins, `story/jedi_encampment` or
  `story/sith_encampment` (the Crossing with the chosen side's camp built into it: same size, same
  origin and rotation) is stamped over the Crossing once the player is `story.encampmentStampDistance`
  away (FarSites keeps the area loaded for the stamp: `Request.at`). The camp (`StoryData.camp`) is
  the Crossing's `HEART`.
- **The travel camps** `story/jedi_camp` and `story/sith_camp` (summons and brings) stay 7×5×7 beside
  the player.
- **The raider stronghold.** `Reckoning.begin` requests `story/raider_camp` through FarSites. The
  crew, the acolyte, the captain and the captives stand at the anchors `CREW_1..7`, `ACOLYTE`,
  `CAPTAIN` and `CAPTIVE_1..3`; the `StoryStructures` constants stay as the fallback. The
  `SIGNAL_FIRE` anchor is the smoke the player sees on the ridge.
- **Courier's Fall, the Echo Shrines, the Reliquary and the hulk's cradle** request their templates
  through FarSites with the ranges above; the Reliquary's hall check is passed as the request's spot
  check and its load radius covers the hall.
- **Missions** request their site through FarSites from the giver. A mission whose template has
  variants (`mission/<name>_2`, `_3`) picks one by the request key.
- **Outposts**: the locator looks 1024 blocks for a worldgen outpost first; a runtime outpost goes
  400 to 700 blocks out on high ground.

### 3.6 Old saves

Sites already placed stay where they are. A quest waiting to place a site places it far away under the
new rules. Old saves have no `oldrepublic:far_sites` attachment; it starts empty.

---

## 4. The build kit

64 new blocks, registered by Phase 0 in `com.oldrepublic.world.build.BuildBlocks` with block items,
placeholder textures, models, loot tables (slabs drop two when double), tags (`mineable/pickaxe`
except cloth and decals, `stairs`, `slabs`, `walls` for blocks and items, `oldrepublic:built_blocks`)
and English names. They are in the creative tab. Textures live in
`assets/oldrepublic/textures/block/build/`. Strengths: metal 3.0 (metal sound), masonry 1.8 (stone
sound), cloth 0.8 (wool sound), decals 0.1.

| Family | Ids | Shape | Look |
|---|---|---|---|
| Durasteel | `durasteel_plating`, `_stairs`, `_slab` | cube, stairs, slab | mid-grey hull panels with seams and rivets |
| | `durasteel_deck`, `durasteel_deck_slab` | cube, slab | darker deck plates with a tread pattern |
| | `durasteel_grate` | cube, see-through | dark grating with holes (cutout) |
| | `durasteel_beam`, `durasteel_pipes` | pillar (axis) | an I-beam; a bundle of conduits |
| | `durasteel_vent` | cube | plating with vent slats |
| | `rusted_plating` | cube | rust-streaked scavenged plating |
| Faction plating | `republic_plating`, `_stairs`, `_slab` | cube, stairs, slab | Old Republic white panels with a blue trim line |
| | `imperial_plating`, `_stairs`, `_slab` | cube, stairs, slab | gunmetal black panels with a thin red line |
| Lights | `light_panel` (14), `light_panel_amber` (13), `light_panel_red` (9) | cube | recessed light strips: cool white, warm amber, emergency red |
| Machines | `console` (light 6), `holo_screen` (light 9) | cube, faces the player | a control console with a lit screen; a wall display with a blue holo glow |
| Cargo | `cargo_crate`, `cargo_crate_republic`, `cargo_crate_imperial` | cube | ribbed cargo containers: grey with hazard corners, white and blue, black and red |
| Jedi masonry | `ruin_bricks`, `ruin_brick_stairs`, `_slab`, `_wall`, `ruin_pillar` | cube, stairs, slab, wall, pillar | sand-coloured dressed stone of the ruin_stone family; a fluted column |
| Sith masonry | `tomb_bricks`, `tomb_brick_stairs`, `_slab`, `_wall`, `tomb_pillar` | as above | black basalt blocks with faint red-brown seams; a carved black column |
| Watch stone (Selvaren) | `watch_stone`, `_stairs`, `_slab`, `_wall` | | pale grey-green ashlar |
| Ash brick (Varkhast) | `ash_bricks`, `ash_brick_stairs`, `_slab`, `_wall` | | ash-grey brick with rust-red mortar |
| Adobe (Ghessar) | `adobe`, `adobe_stairs`, `_slab`, `_wall`, `adobe_bricks` | | sand-plaster render; rough mud brick |
| Salt (Ghessar) | `salt_bricks`, `salt_brick_slab` | | white crystalline blocks |
| Stormstone (Tyrvann) | `stormstone`, `stormstone_bricks`, `stormstone_brick_stairs`, `_slab`, `_wall` | | dark blue-grey basalt; its dressed brick |
| Tiles | `pale_tiles`, `pale_tile_stairs`, `pale_tile_slab` | | clean white-grey floor tiles of Republic interiors |
| Cloth | `canvas`, `canvas_stairs`, `canvas_slab`, `canvas_navy`, `canvas_crimson` | cube, stairs, slab | tent cloth: tan, Republic navy, Sith crimson |
| Decals | `scorch_mark`, `debris` | carpet-thin | a blast scorch on the ground (soft transparent edges); scattered scrap and grit |

Ids are exact: the stairs, slabs and walls are `<base>_stairs`, `<base>_slab`, `<base>_wall` with these
bases: `durasteel_plating`, `durasteel_deck` (slab only), `republic_plating`, `imperial_plating`,
`ruin_brick`, `tomb_brick`, `watch_stone`, `ash_brick`, `adobe`, `salt_brick` (slab only),
`stormstone_brick`, `pale_tile`, `canvas`.

---

## 5. The structure catalogue

"Now" is today's size (x × y × z). "Target" is the size to aim for. "Keep" lists what Java or the
tests read: those anchors stay, and a mechanism kept "exact" keeps every mechanism cell where it is
(re-skin and add detail around it only). Every other cell is free. A builder reports every size,
buried value, piece count and hard-coded cell that changes (section 8.4), and the lead updates Java
and the tests.

### 5.1 O1: the overworld landmarks (worldgen)

| Template | Now | Target | Keep |
|---|---|---|---|
| `jedi_ruin/courtyard` | 31×14×31 | 45–55 square | STAIR_MOUTH (veiled_stone), SURFACE_GUARD, DIGGERS (spawner diggers), CHEST_1; the stair jigsaw |
| `jedi_ruin/stair` | 7×12×9 | free (a longer, grander descent is welcome) | no anchors; the two jigsaws |
| `jedi_ruin/listening_hall` | 25×14×25 | keep size; mechanism exact | HEART, PEDESTAL_JEDI, COUNTERWEIGHT_W/E/S, SENTINEL_1/2 (watcher), CHEST_1; step stones, gallery, ledge, panel |
| `jedi_ruin/pilgrim_niche`, `anchor_cache` | 7×6×7 | up to 11×8×11 | PEDESTAL_WAYFARER; CACHE (chest) |
| `sith_tomb/gate` | 17×12×17 | 31–41 square | STAIR_MOUTH, SURFACE_GUARD; the jigsaws |
| `sith_tomb/path` | 5×3×9 | free | the jigsaws |
| `sith_tomb/dig_camp` | 19×8×19 | 27–33 square | SQUAD (dig_team), CHEST_1 |
| `sith_tomb/processional` | 9×14×27 | keep size; mechanism exact | VENT_1..4 (tomb_vent), SENTINEL_1 (tomb), DIGGERS, NICHE (veiled) |
| `sith_tomb/burial_chamber` | 27×14×27 | keep size; mechanism exact | HEART, URN_KEEPER, URN_1..4, PEDESTAL_SITH, SENTINEL_1/2, CHEST_1 |
| `sith_tomb/pilgrim_niche`, `rubble_room` | 7×6×7, 9×7×9 | up to 11×8×11 | PEDESTAL_WAYFARER; CACHE |
| `wreck_field/hull` | 41×18×19 | 61–81 long | HEART, HATCH (veiled), COUNTERWEIGHT, PEDESTAL_WAYFARER, DECK_CACHE, ANCHOR_LOCKER, SCAVENGER_1/2, SURFACE_GUARD; the hatch puzzle's cells keep their places relative to each other |
| `wreck_field/path`, `salvage_camp`, `scar` | 5×3×9, 15×8×15, 15×6×15 | camp 25–31, scar 21–27 | SQUAD (salvage_crew), CHEST_1 |
| `crystal_cave/camp` | 25×16×25 | up to 41 square, height ≥ 16 | the yard at local y 5 and `clear_from` 13 (`CampPoolElement`), the three poacher entities, the descent jigsaw |
| `crystal_cave/descent` | 9×30×32 | keep | the jigsaws |
| `crystal_cave/heart` | 45×22×48 | keep size; route exact | the meditation stone (22,1,38), the entrance (22,1,5), the seal (22,2,29), the side route, the veined wall x 37..39 z 7, the chest (38,1,2): more crystal and detail only where the route is not |

Piece counts change only by adding side pieces; report them. Variants of a surface piece are named
`<piece>_2` (the client test matches by prefix after integration).

### 5.2 O2 and O3: the overworld wilds (worldgen)

Rebuilt: `outpost/republic_post` and `outpost/imperial_post` (23×12×23 and 23×14×23 → 39–47 square, a
real garrison: walls, a gate, a barracks, a comms tower, a landing pad, a motor pool; keep TERMINAL
with its bounty_terminal of the right army facing north, OFFICER and GARRISON_1..4 as air feet cells;
both the same size so `Outposts` centres them alike).

New structures (ids `oldrepublic:wilds/<name>`). O2 builds the outposts and `homestead`, `watch_hermitage`,
`war_scar`, `downed_fighter` and `jungle_shrine` (salts 960100–960149); O3 builds `smuggler_cove`,
`stilt_hamlet`, `poacher_dig`, `treaty_stone` and `sunken_freighter` (salts 960150–960199):

| Name | Concept | Size | Biomes | Spacing / separation |
|---|---|---|---|---|
| `homestead` | a moisture farm: a domed homestead half sunk around a courtyard, vaporators, a garage with a broken landspeeder, a family's things | 35–45 | desert, badlands, savanna, plains | 44 / 16 |
| `watch_hermitage` | an old Jedi watcher's stone tower on a ridge, a meditation ledge, a garden, a library alcove | 21–27 wide, 30–40 tall | windswept hills, meadows, taiga, stony peaks' edges | 52 / 20 |
| `war_scar` | an old battlefield: a toppled walker, trenches, shattered barricades, turret stumps, scorch fields | 51–71 | plains, savanna, snowy plains, meadow | 56 / 22 |
| `downed_fighter` | a crashed starfighter with a long gouge, a debris trail, a pilot's lean-to | 31–45 long | any land biome except oceans and rivers | 40 / 14 |
| `smuggler_cove` | a hideout on a stony shore: a jetty, a cave dock, crates, a lookout | 35–45 | beaches, stony shores | 48 / 18 |
| `stilt_hamlet` | a swamp village on stilts with boardwalks, a shrine hut, fish racks, an abandoned feel | 45–61 | swamps, mangrove swamps | 48 / 18 |
| `poacher_dig` | a crystal poachers' dig in a hillside: scaffolds, a shaft, sorting tables, a camp (spawner `scavengers`) | 35–45 | windswept hills, badlands, stony areas | 52 / 20 |
| `treaty_stone` | a weathered Old Republic monument plaza with fallen statues and a broken colonnade | 31–41 | plains, meadow, sunflower plains, cherry grove | 56 / 22 |
| `jungle_shrine` (stretch) | a vine-choked Jedi shrine with a pool | 31–41 | jungles | 48 / 18 |
| `sunken_freighter` (stretch) | a freighter wreck in shallow water | 41–55 long | warm and lukewarm oceans | 64 / 24 |

### 5.3 QS and QS2: the story sites (runtime)

QS builds `story/**`; QS2 builds the echo shrines and `ship/**`.

| Template | Now | Target | Keep and add |
|---|---|---|---|
| `story/crossing` (new) | | 27–35 square | an old crossroads waystation where two paths meet: a fallen arch, a dry fountain, two lanterns, worn benches; anchors HEART, MENTOR_JEDI, MENTOR_SITH (6–10 apart, facing the north entrance) |
| `story/jedi_encampment`, `story/sith_encampment` (new) | | the Crossing's size | the Crossing with the side's camp built into it (tents, cots, a meditation spot or a war table, crates, lamps, a cook fire); same HEART and MENTOR anchors; structure void wherever the Crossing is unchanged |
| `story/jedi_camp`, `story/sith_camp` | 7×5×7 | keep 7×5×7 | more detail; the lantern at (5,3,5); (3,1,3) and (3,2,3) explicit air (the stand) |
| `story/raider_camp` | 13×6×13 | 45–57 square | a raider stronghold on a ridge: scrap-and-log palisade, two watch towers, a gate, a captain's longhouse, cages for three captives, a smoking signal fire (a lit campfire with `signal_fire=true` on hay), workshop, speeder; anchors HEART, GATE, CAPTAIN, ACOLYTE, CAPTIVE_1..3, CREW_1..7, SIGNAL_FIRE |
| `story/couriers_fall` | 15×9×11 | 41–55 long | the courier ship crashed with a long gouge, scattered cargo, a small fire; PEDESTAL_WAYFARER, SCAVENGER_1/2, STRONGBOX, add HEART |
| `site/echo_shrine_jedi`, `_sith`, `_wayfarer` | 15×14×15 | 21–27 square | a half-buried shrine you descend into; HEART (air), PEDESTAL (pedestal of the kind), COUNTERWEIGHT, GUARD (spawner watcher / tomb / scavengers); the three share a layout but not materials |
| `ship/cradle` | 17×7×21 | 29–37 by 33–41 | a field repair yard around the hulk: cradle, scaffolds, a crane, tarps, power cells, tool benches; HEART (where the hulk sits; keep at least the old 17×21 clear around it); fix `minecraft:chain` → `minecraft:iron_chain` |
| `ship/beacon_pad` | 11×3×11 | keep 11×3×11 | a better landing pad (deck plates, corner lights, edge stripes; no circles); HEART |

### 5.4 QM: the mission sites (runtime)

Each gets a second variant `<name>_2` (different layout and look, same anchors).

| Template | Now | Target | Anchors |
|---|---|---|---|
| `mission/raider_camp_small` | 11×7×11 | 25–31 | CREW_1..4 |
| `mission/fence_camp` | 15×8×15 | 27–33 | BOOK (a lectern with a book), CREW_1..4, FENCE, FIRE |
| `mission/survey_post` | 15×8×15 | 27–33 | SQUAD_1..5, WOUNDED |
| `mission/skirmish_line` | 17×6×9 | 35–41 × 21–25 | SQUAD_1..5 |
| `mission/patrol_ambush` | 13×7×13 | 25–31 | CRATE, DOWNED_1..3, SENTINEL_1..3 |
| `mission/smuggler_cache` | 13×8×13 | 25–31 | CHIEF, CRATE, CREW_1..4 |
| `mission/ambush_rocks` | 13×6×13 | 21–25 | HEART (no variant needed) |

### 5.5 P1 and P3: Selvaren and Varkhast

P1 builds Selvaren (the waystones, the Heartstone, the Wayfarer cache, `selvaren/**`); P3 builds Varkhast
(the detention post, `varkhast/**`).

Rebuilt:

| Template | Now | Target | Keep |
|---|---|---|---|
| `planet/waystone`, `planet/waystone_corrupted` | 11×9×11 | 19–23 square, both the same size | STONE (same cell in both), CACHE, HEART; GUARD (hollowhorns, bound) in the corrupted one |
| `planet/heartstone` | 19×12×19 | 31–37 | HEART, STONE (heartstone) |
| `planet/wayfarer_cache` | 13×9×13 | 21–27 | HEART, CACHE, PLATFORM_1..3 (the platform puzzle keeps its spacing) |
| `site/detention_post` | 31×16×31 | 41–51 | HEART, WARDEN, GUARD_1..4, PRISONER_1..3 |

New (P1's salts 960200–960249, P3's 960250–960299; planet sets use `clear_of_origin`):

| Id | Concept | Size | Biomes | Spacing |
|---|---|---|---|---|
| `selvaren/watch_cloister` | a Watch monastery ruin around a cloister garden, a bell tower | 41–55 | meadow, highwood | 40 / 14 |
| `selvaren/tarn_causeway` | a stone causeway over a tarn to a shrine island, broken spans | 41–61 long | tarn | 36 / 12 |
| `selvaren/ridge_tower` | a Watch signal tower, a beacon cage, a keeper's hut | 17–21 wide, 35–45 tall | highwood | 40 / 14 |
| `selvaren/hollowhorn_fold` | a herders' stone fold and byre (spawner `hollowhorns`) | 31–41 | meadow | 36 / 12 |
| `selvaren/aurelite_dig` (stretch) | a small mine into a cliff with aurelite ore | 25–31 | highwood | 44 / 16 |
| `varkhast/cliff_mausoleum` | a carved tomb facade in a red rock mass, a stair, inner crypt (spawner `tomb`) | 31–41 | redrock, tomb valley | 40 / 14 |
| `varkhast/obelisk_way` | an avenue of leaning obelisks to a sunken shrine | 51–71 long | tomb valley | 44 / 16 |
| `varkhast/imperial_dig` | an Imperial excavation: crane, prefab huts, a pit, `security_droids` | 41–55 | redrock, ashfall | 40 / 14 |
| `varkhast/ash_village` | an abandoned village half buried in ash | 45–61 | ashfall | 44 / 16 |
| `varkhast/hound_den` (stretch) | an ash hound lair in a cave mouth (`ash_hounds`) | 25–31 | ashfall | 40 / 14 |

### 5.6 P2 and P4: Ghessar and Tyrvann

P2 builds Ghessar (the crashed freighter, the kajidic post, the debtor camp, `ghessar/**`); P4 builds
Tyrvann (the hymn shrine, the mire grave, the verse stone, `tyrvann/**`).

Rebuilt:

| Template | Now | Target | Keep |
|---|---|---|---|
| `planet/crashed_freighter` | 33×14×17 | 55–71 long | HEART, CHEST_1, CRATE, CREW_1..4, SURFACE_GUARD |
| `planet/kajidic_post` | 21×14×21 | 35–45 | HEART, CHEST_1, SQUAD (cartel_enforcers), SURFACE_GUARD |
| `site/debtor_camp` | 23×10×23 | 31–39 | HEART, NPC_DEBTOR, SPAWNER, STRONGBOX (a chest with no loot table) |
| `planet/hymn_shrine` | 17×10×17 | 27–33 | HEART, CHEST_1, SQUAD (hymnsworn), SURFACE_GUARD |
| `planet/mire_grave` | 13×9×13 | 21–27 | HEART, CHEST_1, SURFACE_GUARD |
| `site/verse_stone` | 15×10×15 | 21–25 | STONE (verse_stone, `awake=true`), HEART, SPAWNER |

New (P2's salts 960300–960349, P4's 960350–960399):

| Id | Concept | Size | Biomes | Spacing |
|---|---|---|---|---|
| `ghessar/freighter_graveyard` | several hull sections half sunk in the dunes, salvage rigs | 71–101 (several pieces) | dunes | 48 / 18 |
| `ghessar/salvager_squat` | a shanty town of scrap, awnings and a market (spawner `scavengers`) | 41–55 | dunes, canyons | 40 / 14 |
| `ghessar/caravan_waystation` | an adobe caravanserai: walls, a well court, stalls, stables | 41–55 | dunes, saltflat | 44 / 16 |
| `ghessar/salt_works` | evaporation pans, a refinery shed, conveyors | 45–61 | saltflat | 40 / 14 |
| `ghessar/mesa_lookout` | a rock pillar with a lookout platform and a lift | 15–21 wide, 30–40 tall | canyons | 36 / 12 |
| `ghessar/escape_pod` (stretch) | a lone escape pod in a crater | 15–21 | dunes, saltflat | 32 / 10 |
| `tyrvann/silenced_city` | a district of a drowned, silent city: a plaza and 3–6 building pieces by jigsaw | 90–120 across | basalt, mire | 64 / 24 |
| `tyrvann/choir_ruin` | a ruined Hymnsworn choir hall with a collapsed vault (`hymnsworn`) | 31–41 | stormwood, basalt | 40 / 14 |
| `tyrvann/hask_camp` | a Hask hunting camp: hide tents, drying racks, a trophy post (no hostile spawner) | 31–41 | stormwood, mire | 40 / 14 |
| `tyrvann/mire_causeway` | a sunken stone road across the mire with a toppled gate | 41–61 long | mire | 36 / 12 |
| `tyrvann/downed_interceptor` (stretch) | a crashed interceptor in the stormwood | 25–31 | stormwood | 40 / 14 |

### 5.7 H and H2: the hubs

H builds the Greenwatch, the Proving and the Sepulchre; H2 builds Saltgate and the Holdfast.

| Template | Now | Target | Keep |
|---|---|---|---|
| `hub/greenwatch` | 63×24×63 | 81–95 square | HEART, PAD_BAY_1..4 (flat, open to the sky, 11×11 each), MEDBAY, HOST, VENDOR_1..4, RESIDENT_1..3, PAD_TERMINAL, CONTRACT_TERMINAL; every hub_spawner role |
| `hub/proving` | 63×28×63 | 81–95 | as above plus RESIDENT_4..7, STAIR_DOWN; the stair to the Sepulchre (report SEPULCHRE_OFFSET and STAIR_LOCAL, or write them as `links` and anchors) |
| `hub/sepulchre` | 33×16×41 | keep or grow (report) | HEART, ROOM_1..3, ECHO |
| `hub/saltgate` | 63×24×63 | 81–95 | the M5 anchors (PAD anchors, HOST, CANTINA, VENDOR_FENCE, VENDOR_SHIPWRIGHT, VENDOR_SPACER, NPC_VARRA, NPC_DASK, NPC_VESSBA, NPC_TEEL, COUNTING_HOUSE_DOOR, COUNTING_HOUSE_1..8, ENFORCER_1..6) |
| `hub/holdfast` | 57×26×57 | 75–89 | the M5 anchors (PAD anchors, NPC_DORRUN, NPC_VARRA, VENDOR_ARMOURER, VENDOR_SPACER, WARRIOR_1..6, HOLDFAST_GATE ×9 as `holdfast_gate`) |

Hubs are towns: streets, a market, workshops, homes with interiors, a cantina or mess, a med bay, a
landing field, walls and gates, skyline landmarks. Extra `hub_spawner`s of the roles a hub already has
are welcome.

### 5.8 D: dungeons and bosses

| Template | Now | Target | Keep |
|---|---|---|---|
| `boss/reliquary_surface` | 17×16×17 | 25–33 | DESCENT_TOP; add SHAFT (the shaft's top cell) |
| `boss/reliquary_hall` | 31×18×31 | keep size; mechanism exact | every anchor (COLUMN, DEBRIS, PYLON, SENTINEL, DOOR_SEAL, VAULT_SEAL, GALLERY, DAIS, HEART, LECTERN, PLINTH, BED, THRESHOLD); add SHAFT (the shaft's foot) |
| `site/hymnal_arch` | 15×31×15 | keep size; shaft cells exact | STAIR_MOUTH; add ARCH_TOP (7,30,5) |
| `site/hymnal` | 37×28×79 | keep size; mechanism exact | every anchor; doors' inner sides (heart SOUTH, undercroft EAST); the round Heart hall (radius 15.5) |
| `expedition/singing_hollow_surface` | 13×10×13 | 25–31 | HEART, GUARD (hollowhorns) |
| `expedition/singing_hollow_grotto` | 31×16×31 | keep; mechanism exact | HEART, CHIME_1..6, WEIGHT_1..6, GATE, RELIC; the fronts, the middle (15,2,13), the hub cell (15,2,11), the seal (15,2,25) |
| `expedition/duskglass_rift_surface` | 15×8×15 | 25–31 | HEART |
| `expedition/duskglass_rift_rift` | 23×28×31 | keep or grow (report) | HEART, RELIC, SQUAD_1/2 (ash_hounds) |
| `boss/hunting_camp` | 41×14×41 | 51–61 | HEART, NEST_1..4, GATE, MINE_1..6; the pit ring (radius 20) |
| `greater_tomb/gate` | 21×14×21 | 31–41 | STAIR_MOUTH, SQUAD, GUARD, CHEST_1, SURFACE_GUARD; spawners dig_team and security_droids |
| `greater_tomb/descent`, `urn_vault`, `sealed_crypt` | 11×18×27, 21×12×21 ×2 | keep sizes; mechanism exact | every anchor; the urn keeper's urn offsets |

### 5.9 B: the build kit's art

Final 16×16 textures for the 64 blocks of section 4 in the mod's style (look at the existing ruin,
tomb and wreck textures), generated by `tools/assets/gen_textures_buildkit.py`; adjusted models
where a block needs more than one texture (pillar tops, the console's screen face, crate sides);
average colours that match the table, so the structure previews (which colour blocks from the
textures' averages) read right.

---

## 6. Worldgen rules

- Every structure follows `docs/STRUCTURES.md` 6: jigsaw, `adapted_pool_element` for terrain,
  `max_distance_from_center` ≤ 128, at least two start variants for new structures, monsters kept out
  of the structure's own pieces (`spawn_overrides`) unless it is a lair.
- **Planet sets** use `"type": "oldrepublic:clear_of_origin"` with `min_distance` 256 (the hub's
  area). The existing planet sets switch to it too: P1 `waystones`, P2 `crashed_freighters` and
  `kajidic_posts`, P4 `hymn_shrines` and `mire_graves`, D `greater_tombs`.
- **Exclusion**: new overworld sets keep 4 chunks from `ruins` (`exclusion_zone`); new planet sets need
  none beyond `clear_of_origin`.
- **Salts**: O2 960100–960149, O3 960150–960199, P1 960200–960249, P3 960250–960299, P2 960300–960349,
  P4 960350–960399. The existing sets keep theirs.
- **Spawning**: a new structure that houses hostiles uses `site_spawner` blocks of the existing groups;
  lairs may allow monster spawns.
- **Loot**: new chests use new tables `chests/<world>/<name>[_<n>]` built from the items the mod's
  existing tables already use, in the same value range as the nearest comparable existing table.

---

## 7. Frozen Phase 0 contracts

Phase 0 (the lead) builds these before the workers start. Workers may add to them only through a
request; signatures never change.

### 7.1 Java

- `com.oldrepublic.world.site.far.FarSites` (J1 implements; Phase 0 writes the signatures and a stub):

  ```java
  public final class FarSites {
      public enum Phase { CHOOSING, LOADING, PLACED, FAILED }
      public enum Terrain { ANY, FLAT, HIGH, LOW }
      public record Status(Phase phase, @Nullable BlockPos target, @Nullable RuntimeSites.Placement placement, String detail) {
          public boolean placed();            // phase == PLACED
          public boolean failed();            // phase == FAILED
          public @Nullable BlockPos pointer(); // the placement's HEART anchor (else its origin) when placed, else the target
      }
      public interface SpotCheck { boolean accept(ServerLevel level, RuntimeSites.Spot spot); }
      public static final class Request {
          public static Request of(String key, @Nullable UUID owner, Identifier template, BlockPos from, int minDistance, int maxDistance);
          public static Request point(String key, @Nullable UUID owner, BlockPos from, int minDistance, int maxDistance, int footprint); // a ground point, no template
          public static Request at(String key, @Nullable UUID owner, Identifier template, BlockPos origin, Rotation rotation); // a fixed stamp once loaded
          public Request facing(Vec3 direction, float spreadDegrees);   // prefer bearings within the spread
          public Request inBiomes(TagKey<Biome> biomes);
          public Request avoiding(Collection<BlockPos> others, int separation);
          public Request preferring(Terrain terrain);                  // default: the template's site meta
          public Request check(SpotCheck check);                       // extra veto on the precise spot
          public Request loadRadius(int chunks);                       // override the computed ticket radius
          public Request kind(@Nullable SiteKind kind);                // default: SiteKind.ofTemplate
      }
      public static Status request(ServerLevel level, Request request); // idempotent by key
      public static Status poll(ServerLevel level, String key);         // FAILED with detail "unknown" for an unknown key
      public static void cancel(ServerLevel level, String key);
      public static Component describe(BlockPos from, BlockPos to);
  }
  ```

- `com.oldrepublic.world.site.SiteMeta` (J1 implements; Phase 0 writes the record and a stub that
  returns the defaults):

  ```java
  public record SiteMeta(int buried, int maxSlope, int clearance, int supportDepth, int rampRings, int clearAbove,
          FarSites.Terrain terrain, @Nullable TagKey<Biome> biomes, int spawnRadius, Map<Identifier, BlockPos> links) {
      public static final SiteMeta DEFAULT; // 1, 4, 8, 8, 2, 6, ANY, null, 64, {}
      public static SiteMeta of(Identifier template);
      public static void init(); // reload listener, server start
  }
  ```

- `SiteKind` gains (appended) `STORY_CAMP` (`story/crossing`, `story/*_encampment`, `story/jedi_camp`,
  `story/sith_camp`) and `RAIDER_STRONGHOLD` (`story/raider_camp`).
- `SiteAnchors.Anchor` gains (appended) `MENTOR_JEDI`, `MENTOR_SITH`, `CAPTAIN`, `ACOLYTE`,
  `CAPTIVE_1`, `CAPTIVE_2`, `CAPTIVE_3`, `CREW_5`, `CREW_6`, `CREW_7`, `SIGNAL_FIRE`, `SHAFT`,
  `ARCH_TOP`.
- `com.oldrepublic.world.gen.ClearOfOriginPlacement`: a `RandomSpreadStructurePlacement` whose
  `isPlacementChunk` also needs the chunk's centre at least `min_distance` blocks from (0, 0);
  registered as `oldrepublic:clear_of_origin`, codec = the random spread fields plus `min_distance`
  (default 256).
- `com.oldrepublic.world.build.BuildBlocks`: the 64 blocks of section 4 as public constants.
- `com.oldrepublic.world.StructuresFeature.init()`: called from `OldRepublicMod` after the Milestone 5
  features; registers the build kit and the placement type, and calls `FarSitesFeature.init()` (J1's)
  and `SiteMeta.init()`.

### 7.2 Tools

- `tools/assets/structlib/`: `mod.py`, `meta.py`, `jigsaw.py`, shared palettes, `light_fill`, the
  `clear_of_origin` option of `worldgen.structure_set` (`docs/STRUCTURES.md` 4).
- `tools/check_m6_structures.py` (`docs/STRUCTURES.md` 8), run by CI's checks job with `--report`
  until integration, then strictly. It reads `tools/m6_baseline.json` (the anchors, sizes and
  mechanism cells that code and tests rely on) and `tools/vanilla_blocks_26_2.txt`.
- `tools/ownership_m6.json` and the CI overlap check for it.
- `tools/assets/gen_textures_buildkit.py` with placeholder textures and every JSON file of the kit.

---

## 8. Ownership and workers

### 8.1 Workers

| Code | Area | Owns |
|---|---|---|
| J1 | far placement (Java) | `world/site/**` except the Phase 0 contract lines, `world/outpost/**`, `mission/**` (code and `data/oldrepublic/mission/*.json`), `planet/**` except `planet/block/**`, `client/mission/**`, `travel/Pads.java`, `travel/TravelService.java` constants, `quest/turning/TurningRules.java`, lang parts `world.json` (the new far keys), `mission.json`, `planet.json`; its test class `FarSitesGameTests` |
| J2 | story placement (Java) | `quest/story/**`, `world/EchoShrines.java`, `boss/ReliquarySites.java`, `boss/ReliquaryBuild.java`, `travel/Ships.java`, `travel/ShipBalance.java`; lang part `quest.json`; its test class `StoryFarGameTests` |
| B | build-kit art | `assets/oldrepublic/textures/block/build/**`, `assets/oldrepublic/models/block/build/**`, the kit's blockstates and item definitions (`assets/oldrepublic/items/<id>.json`), `tools/assets/gen_textures_buildkit.py` |
| O1 | overworld landmarks | `structure`, `site_anchors`, `site_meta`, pools, processors and loot of `jedi_ruin`, `sith_tomb`, `wreck_field`, `crystal_cave`; the worldgen structures and sets `ruins`, `crystal_caves`; `tools/assets/structures/o1/**` |
| O2 | outposts and wilds (first half) | the same for `outpost/**` (incl. the outposts' pools, structures and set) and `wilds/{homestead,watch_hermitage,war_scar,downed_fighter,jungle_shrine}`; `tools/assets/structures/o2/**` |
| O3 | wilds (second half) | the same for `wilds/{smuggler_cove,stilt_hamlet,poacher_dig,treaty_stone,sunken_freighter}`; `tools/assets/structures/o3/**` |
| QS | story sites | `story/**` (templates, anchors, meta); `tools/assets/structures/qs/**` |
| QS2 | echo shrines and ship sites | `site/echo_shrine_*`, `ship/**` (templates, anchors, meta); `tools/assets/structures/qs2/**` |
| QM | mission sites | `mission/**` templates, anchors and meta; `tools/assets/structures/qm/**` |
| P1 | Selvaren | `planet/{waystone*,heartstone,wayfarer_cache}`, `selvaren/**` (all file kinds), the `waystones` set and pool, the `wayfarer_cache_m4` loot; `tools/assets/structures/p1/**` |
| P2 | Ghessar | `planet/{crashed_freighter,kajidic_post}`, `site/debtor_camp`, `ghessar/**`, their existing sets, pools and loot; `tools/assets/structures/p2/**` |
| P3 | Varkhast | `site/detention_post`, `varkhast/**` (all file kinds); `tools/assets/structures/p3/**` |
| P4 | Tyrvann | `planet/{hymn_shrine,mire_grave}`, `site/verse_stone`, `tyrvann/**`, their existing sets, pools and loot; `tools/assets/structures/p4/**` |
| H | Chapter Four hubs | `hub/{greenwatch,proving,sepulchre}` templates, anchors and meta; `tools/assets/structures/h/**` |
| H2 | Chapter Five hubs | `hub/{saltgate,holdfast}` templates, anchors and meta; `tools/assets/structures/h2/**` |
| D | dungeons and bosses | `boss/**` templates, `site/hymnal*`, `expedition/**`, `greater_tomb/**` (all file kinds incl. the `greater_tombs` set); `tools/assets/structures/d/**` |

`tools/ownership_m6.json` is the machine-readable copy; everything else is the lead's (the Phase 0
contracts, `structlib`, the check scripts, the tests and fixtures, every other lang part, the old
generator scripts, the top-level docs).

### 8.2 How the Java workers split the callers

- J1: `FarSites` and `SiteMeta` themselves, `RuntimeSites` (terrain adaptation with ramps and tree
  clearing, the precise search), `StructureLocator`, outposts, missions (including variants and the
  tracker's distance), planet sites, the cull, the Hymnal halls through `Request.at`, the hubs' sizes
  (`PlanetHubs` reading anchors, meta and links), `Pads`, `TravelService` and `TurningRules` reaches.
- J2: the Crossing and encampment, the raider stronghold and its spawns, Courier's Fall, the echo
  shrines (`FirstEcho`, `EchoShrines`), the Reliquary, the hulk's cradle, the matriarch, and every
  story message that names a far place.
- Until J1's `FarSites` lands, J2 codes against the Phase 0 stub (it returns FAILED, so J2's fallback
  path runs). The lead integrates J1 first.

### 8.3 Process

- One working tree; workers never commit, push, stash or switch branches. Never touch a file you do
  not own: put the exact change in your report under "Requests".
- Check every path you touched with `python3 tools/check_ownership.py --table tools/ownership_m6.json
  <code> --files <paths>` before you report.
- Template workers run `python3 tools/check_m6_structures.py --only <your prefixes>` until it is clean,
  and render previews of everything they built into their scratch folder.
- There is no local Java build: the lead compiles on CI. Mark every unverified API with
  `// API-CHECK: <what>`.

### 8.4 The report

Every worker reports:

1. Every file created, changed or deleted, and the ownership check's output.
2. For template workers, a table per template: final size, buried value, piece count (jigsaw), anchors
   added or removed, and every cell or constant from section 5's "Keep" column (or from Java and the
   tests, as listed in the survey the brief points to) that moved: old value → new value.
3. Requests for files you do not own (exact changes).
4. API-CHECKs, tests written, anything cut (with section 1's cut order), and what a person should look
   at in game.
5. The preview PNG paths of the final versions.

## 9. Tests

- **The lead** updates every server test that freezes a size, an anchor set or a placement constant
  (`WorldGameTests`, `StoryGameTests`, `WorldM3GameTests`, `PlanetGameTests`, `PlanetM5GameTests`,
  `ContractsM4/M5GameTests`, `TurningGameTests`, `BossGameTests`) from the workers' reports, and the
  client tests' cameras, piece counts and the playthrough's cells.
- **J1** writes `FarSitesGameTests`: the chooser on a synthetic height field (flat beats rough, high
  beats low for HIGH, wet candidates refused, the band respected, the bearing preferred, determinism by
  key), `describe`, the persistence round trip, `SiteMeta` defaults and parsing, mission variant choice.
  No test generates far chunks: the chooser takes a height sampler.
- **J2** writes `StoryFarGameTests`: anchor-based raider spawns with the constant fallback, the
  encampment overlay's stamp rule (distance), the fallback to the near search on FAILED.
- **The lead** writes `StructureTourClientTest`: a superflat world where every rebuilt and new
  structure is placed (`/place structure` for worldgen, `/place template` for runtime templates) and
  photographed from two angles, shots `oldrepublic_m6_<nnn>_<name>`. Its screenshots are the
  acceptance review and go in the report to Gabriel.
- `tools/check_m6_structures.py` runs on every CI push.

## 10. Docs

The lead updates `docs/ASSET_MANIFEST.md` (generators, build kit), `docs/BALANCE.md` (section 3.2),
`docs/KNOWN_ISSUES.md`, `docs/FEATURE_CHECKLIST.md`, and the how-to-play guide (distances, the new
places). Workers put their text for those in their reports.

## 11. Revision notes

(The lead records here how Phase 0 and the integration actually went.)

- **Phase 0** (b963a33, 9553572): the build kit's 64 blocks, the far-placement and site-meta
  contracts, the structure checker and `Template.load` landed as planned and passed every server
  test on CI. The `.gitignore` rule `build/` had been hiding every source folder named `build`; it
  is now `/build/`.
- **Workers.** The eleven planned workers became sixteen: outposts and wilds, story sites, Selvaren
  and Varkhast, Ghessar and Tyrvann and the two hub pairs were each split in two, so no worker
  carries more than a world's worth of templates (`tools/ownership_m6.json`, sections 5.2 to 5.7).
- **The tour** (section 9) photographs each structure on its own world's terrain instead of a
  superflat world: worldgen structures where the seeded world itself generates them (found as
  `/locate` finds them, so terrain blending and biome placement are part of the review), runtime
  sites on the flattest dry spot near a slot, hubs where the game places them. It takes long, so it
  runs in the `visuals` workflow with looks `tour`, not in every build. A first run on the
  structures as they were gives the before pictures; workers name one close view per structure
  (`CAMERA` lines in their reports) for the after run.
