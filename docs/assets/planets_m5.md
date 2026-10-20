# Planet assets, Milestone 5: Ghessar, Tyrvann, their sites and weather (W3 worlds)

Every asset here is an **original placeholder** made by a script in this repository; no Star Wars game or film asset is
used or imitated, and nothing is sampled. Textures and asset JSON: `tools/assets/gen_textures_planet.py --m5` (PIL,
seeded, deterministic). Structure templates, sidecar anchors, worldgen data and loot:
`tools/assets/gen_structures_m5.py` (deterministic NBT, fixed gzip time; walkability checked). Sounds:
`tools/assets/sounds/world_m5.py`, rendered by `python3 tools/assets/gen_sounds.py --m5 --feature world_m5` (numpy
synthesis: filtered noise, formant-filtered saws, inharmonic bell partials, oscillators, convolution reverb; every loop
built seamless). The art rule holds throughout: **no glowing runes, sigils, halos, rings or ground circles**; the verse
stone's light is a faint warmth in its fissures, and everything the Force or the storm does is bent air, dust, grit,
motes, sparks and light. Design and use: `docs/design/planets_m5.md`; the M4 planets' assets: `docs/assets/planets.md`.

## Textures (under `src/main/resources/assets/oldrepublic/`)

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/block/salt_crust_top.png`, `salt_crust_side.png` | Salt crust, 16x16, `cube_column` | placeholder generated | The top: a dried salt pan split into tiling polygons whose edges curl up white; the side: a thin white crust over damp, sandy salt. Bright under Ghessar's sun but not snow: greyer, grainier, cracked. |
| `textures/block/sunstone_ore.png` | Sunstone ore in canyon rock, 16x16, `cube_all` | placeholder generated | Red sandstone strata (darker bands with a paler parting under each) with rounded amber nodules, hot at their hearts. Must tile, and must not read as vanilla gold, copper or redstone ore. |
| `textures/block/stormglass_cluster.png` | Stormglass cluster, 16x16, `cross`, cutout | placeholder generated | Blue-grey glass shards fused by lightning, slate-dark at their roots, with a bright blue-white edge light; amethyst's silhouette family, but colder and more jagged. Reads on black basalt in the rain. |
| `textures/block/peat.png` | Peat, 16x16, `cube_all` | placeholder generated | Compressed dark-brown layers, fibrous strands along the bedding, pale root threads and a few wet glints. Earthy and wet, clearly fuel, not dirt or mud. |
| `textures/block/verse_stone_side.png`, `verse_stone_top.png` | Verse stone, awake, 16x16, `cube_column` | placeholder generated | A near-black tooled monolith (worn horizontal chisel courses); its fine fissures hold a faint, pale warmth while the verse is awake. No runes, no symbols, no outline glow: the light is inside the cracks only. |
| `textures/block/verse_stone_silent_side.png`, `verse_stone_silent_top.png` | Verse stone, silenced | placeholder generated | The same stone with the warmth gone from the fissures: cold, matte, a little dusty. The change from awake must be visible from 10 blocks. |
| `textures/block/holdfast_gate.png` | Holdfast gate plate, 16x16, `cube_all` | placeholder generated | Clan iron: two dark plates per block with seams down the edge and the middle, riveted straps across, rust running down from the rivets. Heavy, hand-made, storm-worn. |
| `textures/item/sunstone.png` | Sunstone item, 16x16 | placeholder generated | A rough amber stone with an irregular rounded outline, lit from the upper left, hottest at its heart. A crafting material, not a gem cut for jewellery. |
| `textures/item/stormglass_shard.png` | Stormglass shard item, 16x16 | placeholder generated | One blue-grey glass sliver, dark at its base, with a bright blue-white edge. |
| `textures/particle/sand_gust_0..3.png` | `oldrepublic:sand_gust`, 8x8 | placeholder generated | Soft streaks of blown sand along the wind (left to right), thinning at the tail; tinted in code. Four lengths so a storm reads as moving air, never as a pattern. |
| `textures/particle/verse_mote_0..2.png` | `oldrepublic:verse_mote`, 8x8 | placeholder generated | Tiny pale-gold specks (a dot with a soft falloff), full-bright; three sizes. Dust in a sunbeam, never a sparkle or a star. |

## Models, block states, items and particles

| Path | What it is | Status |
|---|---|---|
| `blockstates/{salt_crust,sunstone_ore,peat}.json` | single variant | final |
| `blockstates/stormglass_cluster.json` | 6 `facing` variants, rotated as vanilla `amethyst_cluster` | final |
| `blockstates/verse_stone.json` | `awake=true` / `awake=false` (the silent model) | final |
| `blockstates/holdfast_gate.json` | `open=false` / `open=true` (the open plate model) | final |
| `models/block/{salt_crust,sunstone_ore,peat,stormglass_cluster,verse_stone,verse_stone_silent,holdfast_gate,holdfast_gate_open}.json` | `cube_column` (salt crust, both verse stones), `cube_all` (ore, peat, the shut gate), `cross` (cluster), particle-only (an open gate draws nothing and lets everyone through) | final |
| `items/*.json`, `models/item/*.json` | the four block items (`salt_crust`, `sunstone_ore`, `stormglass_cluster`, `peat`) and `sunstone`, `stormglass_shard` | final |
| `particles/sand_gust.json`, `verse_mote.json` | sprite lists | final |

## Structure templates (under `src/main/resources/data/oldrepublic/structure/`)

All **placeholder generated**, functional (walkability checked: every anchor reachable except the designed locks). A
builder replacing one must keep its size, buried layers, sidecar anchors (`data/oldrepublic/site_anchors/<same
path>.json`), the block entities' data (hub and site spawner roles and groups, the verse stone) and the art rule.

| Template | Size | Brief |
|---|---|---|
| `hub/saltgate.nbt` | 63x24x63, 3 buried | Ghessar's port: a walled town of sand-scoured prefab and old freighter hulls. North gate; the cantina and the counting-house (eight rooms, Dask upstairs), the market street, Vessba's audience hall; the four-bay pad behind its own gate between the medbay and the hangar; enforcer posts on the walls. Hot, bleached, busy, a little lawless. |
| `hub/holdfast.nbt` | 57x26x57, 3 buried | Clan Hask's camp on a stilted deck above the mire: a clan-iron wall with the gate north and a stair from the mud, Dorrun's longhouse and the forge by the fire yard, the barracks, stores, medbay and spacer round the four-bay pad, lightning rods on every roof. Wet, dark wood, firelight, defiance. |
| `site/verse_stone.nbt` | 15x10x15, 2 buried | A black monolith (the verse stone at eye height in its heart) on old flagstones; the ground left to the biome. Ancient, alone, listening. |
| `site/hymnal_arch.nbt` | 15x31x15, 17 buried | The Hymnal's arch on a basalt ridge: two piers and a span over an open cleft, the keystone the storm strikes, the processional's first flights going down. Monumental and storm-scarred. |
| `site/hymnal.nbt` | 37x28x79 | The halls under the arch: the processional (three flights and landings, the storm-caller alcoves, the pit), the Heart (a domed round hall 31 across: dais, lectern, rods, pylons, resonators) and the undercroft under it (the keystone, the Cantor's ring). Black stone, old song-engine pipes, echo. |
| `site/debtor_camp.nbt` | 23x10x23, 2 buried | Desperate salvagers behind a scrap barricade round a fire in a canyon; the strongbox in the east lean-to. |
| `site/detention_post.nbt` | 31x16x31, 2 buried | An Imperial post on Varkhast: a fenced yard, a guard tower, the cell block with three barred cells and their levers. Clean, cold, efficient. |
| `planet/crashed_freighter.nbt` | 33x14x17, 4 buried | An old freighter half-buried in the dunes, engines west, cockpit east, a torn breach over a sand spill into the hold. |
| `planet/kajidic_post.nbt` | 21x14x21, 1 buried | A fortified kajidic waystation: a walled sandstone yard with corner turrets, a two-storey tower with a roof terrace, a striped awning over the trade stall. |
| `planet/hymn_shrine.nbt` | 17x10x17, 1 buried | Eight carved stones round a basalt cairn crowned with stormglass, two hide shelters and a fire: a Hymnsworn camp. |
| `planet/mire_grave.nbt` | 13x9x13, 3 buried | A fallen warrior's cairn sunk in the mud, a blade driven into its head, the chest in a shallow pit, puddles and peat. |

World data (final, tuning may change): `dimension/{ghessar,tyrvann}.json`, `dimension_type/{ghessar,tyrvann}.json`,
`worldgen/noise_settings/{ghessar,tyrvann}.json` and `worldgen/density_function/{ghessar,tyrvann}/*`,
`worldgen/biome/{ghessar_dunes,ghessar_saltflat,ghessar_canyons,tyrvann_stormwood,tyrvann_mire,tyrvann_basalt}.json`,
`worldgen/configured_feature/{bleached_ribs,hanging_moss,mire_pool,mire_roots,ore_sunstone,salt_pillar,stormglass_tops,
stormwood_giant,stormwood_tree,stormwood_tree_low}.json` and `placed_feature/{bleached_ribs,hanging_moss,mire_pool,
mire_roots,mire_trees,ore_sunstone,salt_pillar,stormglass_tops,stormwood_giant,stormwood_trees}.json`; biome tags
`is_ghessar`, `is_tyrvann`, `is_planet` (four planets). Worldgen structures (final, spacing may change):
`worldgen/structure/{crashed_freighter,kajidic_post,hymn_shrine,mire_grave}.json`, their `template_pool/<name>/start.json`,
`structure_set/{crashed_freighters,kajidic_posts,hymn_shrines,mire_graves}.json`, structure tags of the same names and
`tags/worldgen/biome/has_structure/<name>.json`. Loot (final, tuning may change):
`loot_table/blocks/{salt_crust,peat,sunstone_ore,stormglass_cluster,verse_stone,holdfast_gate}.json` and
`loot_table/chests/{crashed_freighter,kajidic_post,hymn_shrine,mire_grave}.json`. Tag: `tags/block/force_reactive.json`
(adds `verse_stone`). Lang: `src/main/lang-parts/planet.json` (blocks, items, biomes, dimensions, site kinds, planet
sites, the verse stone, gate and Hymnal lines, the sandstorm overlay lines and command replies).

## Sounds (under `src/main/resources/assets/oldrepublic/sounds/`)

All **placeholder generated** by `tools/assets/sounds/world_m5.py`; ids, subtitles and categories are in
`tools/assets/sounds/events_m5.json` (Phase 0). Loops must stay seamless in any replacement. Music and beds are
streamed.

| File | Id | Length | What it is now | Brief for the final sound designer |
|---|---|---|---|---|
| `verse_stone/hum.ogg` | `verse_stone.hum` | 4 s loop | Wordless voices on a low open fifth, breathing | The stone singing to itself: a low, patient hum of many wordless voices, felt as much as heard; heard within 24 blocks while the stone is awake. Seamless. No words in any language. |
| `verse_stone/pulse.ogg` | `verse_stone.pulse` | 3.6 s | An outward thump with the chord swelling through it and fading | The slowing hum: the air shoved outward once, the stone's chord swelling through the push; heavy enough that players know it slowed them. |
| `verse_stone/channel.ogg` | `verse_stone.channel` | 3 s loop | A rising, beating resonance with a breath through it | The verse being drawn out of the stone toward the player: a resonance that beats and strains, tense but not hostile. Played higher when the Force cuts the channel. Seamless. |
| `verse_stone/silence.ogg` | `verse_stone.silence` | 4.5 s | The chord sinking an octave and closing, grit sifting, a settle | A song stopping for good: the chord sinks and closes down, grit sifts off the stone, a last settle. Relief and loss together. |
| `sandstorm/wind.ogg` | `sandstorm.wind` | 8 s loop | A heavy wind through sand: whistling bands, hissing grains, grit | A sandstorm from inside it: a howling, sand-laden gale with whistles and stinging grains, loud but not shrill (it plays for minutes). Seamless. |
| `stormglass/form.ogg` | `stormglass.form` | 2.7 s | Static snaps across rock, then glass pushing up and chiming | Lightning's afterglow on basalt: static crawling over the rock, then glass growing out of it with a cold chime. |
| `holdfast/gate.ogg` | `holdfast.gate` | 3.1 s | Riveted plates scraping along runners and shutting against the stop | Clan Hask's gate: heavy hand-forged iron plates sliding on wet runners, a deep clang at the stop. Pitched down when it shuts. |
| `hymnal/door.ogg` | `hymnal.door` | 4.7 s | A stone slab grinding aside over grit, a deep thud | The Hymnal's sealed doors: an old slab lifted and ground aside, grit pouring, a deep settle. Pitched down when it seals. |
| `ambient/ghessar_dunes.ogg` | `ambient.ghessar.dunes` | 12 s loop | A warm steady wind, sand hissing off the crests and trickling | The dune sea at midday: warm, endless wind and moving sand. Seamless; mixed low as a bed. |
| `ambient/ghessar_saltflat.ogg` | `ambient.ghessar.saltflat` | 12 s loop | Thin high air over emptiness, the crust ticking in the heat | The salt flats: a vast, still emptiness and the tick of crust in the heat. |
| `ambient/ghessar_canyons.ogg` | `ambient.ghessar.canyons` | 12 s loop | Wind moaning through narrows, a far rockfall echo, a high cry | The canyons: moaning narrows, echoes, a far bird of prey (invented). |
| `ambient/tyrvann_stormwood.ogg` | `ambient.tyrvann.stormwood` | 12 s loop | Rain on broad leaves, gusts through the canopy, far thunder | The storm jungle: heavy rain on huge leaves, creaking trunks, thunder far off (vanilla's own thunder plays on top). |
| `ambient/tyrvann_mire.ogg` | `ambient.tyrvann.mire` | 12 s loop | Rain on still water, bubbling mud, low croaks, an insect whine | The black mire: rain on still water, mud that bubbles, frogs (invented), a thin whine. |
| `ambient/tyrvann_basalt.ogg` | `ambient.tyrvann.basalt` | 12 s loop | A hard wind over bare rock, sideways rain, static, nearer thunder | The ridges: wind and rain driven across bare rock, the air charged. |
| `ambient/saltgate.ogg` | `ambient.saltgate` | 12 s loop | Dry wind over the port, an idling freighter, far clanks, a murmur | Saltgate: a port in the heat, ships idling, metal, traders talking far off (wordless). |
| `ambient/holdfast.ogg` | `ambient.holdfast` | 12 s loop | Rain drumming on hide roofs, a crackling fire, low voices | The holdfast: rain on hide and wood, a fire, the clan's voices low under the storm (wordless). |
| `ambient/hymnal.ogg` | `ambient.hymnal` | 12 s loop | A deep stone hush, a far choir on one breath, slow drips | The Hymnal: a deep hush with the memory of a choir in it, water dripping into stillness. |
| `music/ghessar.ogg` | `music.ghessar` | 48 s loop | A wide drone on E, a sparse plucked line, a soft frame drum | Ghessar's music: heat-haze, distance and hard people; an open, bright mode, plucked strings and hand drums. Original composition, no resemblance to any franchise theme. |
| `music/tyrvann.ogg` | `music.tyrvann` | 48 s loop | A dark drone on A, a slow low choir between two chords, far thunder | Tyrvann's music: dark, old, storm-bound; a low wordless choir and a drone. Original composition, no resemblance to any franchise theme. |
