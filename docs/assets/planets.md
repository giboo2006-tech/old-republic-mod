# Planet assets: Selvaren, Varkhast, their sites and hubs (Milestone 4, W1 worlds)

Every asset here is an **original placeholder** made by a script in this repository; no Star Wars game or film asset is
used or imitated, and nothing is sampled. Textures and asset JSON: `tools/assets/gen_textures_planet.py` (PIL, seeded,
deterministic). Structure templates and sidecar anchors: `tools/assets/gen_structures_m4.py` (deterministic NBT, fixed
gzip time). Sounds: `tools/assets/sounds/world_m4.py`, rendered with
`python3 tools/assets/gen_sounds.py --m4 --feature world_m4` (numpy synthesis: filtered noise, inharmonic bell
partials, oscillators, convolution reverb; every loop built seamless). The art rule holds throughout: **no glowing
runes, sigils, halos, rings or ground circles**; the waystone's face is a plain carved groove and its corruption is veins
in the rock. Design and use: `docs/design/planets.md`.

## Textures (under `src/main/resources/assets/oldrepublic/`)

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/block/silverleaf.png` | Silverleaf plant, 16x16, `cross`, cutout | placeholder generated | Low fronds of pale silver-green with a few small white buds, growing in the shade of Selvaren's silver trees. It should read as a medicinal herb you would stop to pick, brighter than ferns, never glowing. Transparent background. |
| `textures/block/ashroot.png` | Ashroot plant, 16x16, `cross`, cutout | placeholder generated | Twisted, rust-dark stalks with ash-grey tips, as if half burnt, growing in gully shade on Varkhast. Wiry and dry, readable against red sand. |
| `textures/block/aurelite_ore.png` | Aurelite ore in stone, 16x16, `cube_all` | placeholder generated | Vanilla-family stone with warm amber-gold veins and a few bright flecks: a gold crystal seam, not gold nuggets. Must tile and must not look like vanilla gold or copper ore. |
| `textures/block/deepslate_aurelite_ore.png` | Aurelite ore in deepslate, 16x16 | placeholder generated | The same veins in banded deepslate. |
| `textures/block/duskglass_cluster.png` | Duskglass cluster, 16x16, `cross`, cutout | placeholder generated | Shards of black volcanic glass with a dull red light caught along their edges, like amethyst's silhouette family but dark, sharp and dangerous (it cuts). Must read on dark valley walls at a distance. |
| `textures/block/budding_duskglass.png` | Budding duskglass, 16x16, `cube_all` | placeholder generated | Faceted black glass rock with dark red seams; the bed the clusters grow from. Tiles. |
| `textures/block/waystone_front.png`, `_side.png`, `_top.png` | Watch waystone, 16x16, `orientable` | placeholder generated | A pale, weathered standing stone with lichen. The front has one plain carved groove (an old way-mark, not a rune, not lit). Old and calm. |
| `textures/block/waystone_corrupted_front.png`, `_side.png`, `_top.png` | Corrupted waystone, 16x16, `orientable` | placeholder generated | The same stone with dark red-black veins spreading through it and the lichen dead and brown. The veins are in the rock, not glowing on it; the pulsing comes from particles. |
| `textures/block/heartstone_side.png`, `_top.png` | The Heartstone, 16x16, `cube_column` | placeholder generated | A great dark stone with deep fissures and black veins; heavier and older than a waystone, built into a tall column in the template. |
| `textures/block/sealed_plating.png` | Sealed plating, 16x16, `cube_all` | placeholder generated | Riveted durasteel plates with a heavy weld seam across them: an old alloy door that clearly needs a cutting tool or a great push. Tiles as a wall. |
| `textures/block/hub_spawner.png` | Hub spawner particle texture, 16x16 | placeholder generated | Dust specks; the block has no model and is never drawn. Final: keep as is. |
| `textures/item/aurelite_shard.png` | Aurelite shard item, 16x16 | placeholder generated | A single gold crystal shard with a bright facet; a crafting material. |
| `textures/item/duskglass_shard.png` | Duskglass shard item, 16x16 | placeholder generated | A black glass sliver with a red edge light. |
| `textures/particle/ash_flake_0..2.png` | `oldrepublic:ash_flake`, 8x8 | placeholder generated | Irregular grey-white flakes of different shapes, soft-edged; tinted in code. Must read as ash, not snow (smaller, greyer, ragged). |
| `textures/particle/aurelite_glint_0..3.png` | `oldrepublic:aurelite_glint`, 8x8, played in order | placeholder generated | A four-point twinkle growing and shrinking over four frames, white (tinted gold in code, full-bright). A star twinkle, never a ring. |
| `textures/particle/duskglass_shard_0..2.png` | `oldrepublic:duskglass_shard`, 8x8 | placeholder generated | Small black glass slivers with a dull red edge, drawn in colour, at different angles so the tumble reads. |

## Models, block states, items and particles

| Path | What it is | Status |
|---|---|---|
| `blockstates/{silverleaf,ashroot,aurelite_ore,deepslate_aurelite_ore,budding_duskglass,sealed_plating,heartstone,hub_spawner}.json` | single variant | final |
| `blockstates/duskglass_cluster.json` | 6 `facing` variants, rotated as vanilla `amethyst_cluster` | final |
| `blockstates/waystone.json`, `waystone_corrupted.json` | 4 horizontal `facing` variants | final |
| `models/block/*.json` | `cross` (plants, cluster), `cube_all` (ores, budding, plating), `cube_column` (Heartstone), `orientable` (waystones), particle-only (hub spawner) | final (parents confirmed by the `m4_w1` probe) |
| `items/*.json`, `models/item/*.json` | the ten block items and the two shards | final |
| `particles/ash_flake.json`, `aurelite_glint.json`, `duskglass_shard.json` | sprite lists | final |

## Structure templates (under `src/main/resources/data/oldrepublic/structure/`)

All **placeholder generated**, functional (walkability checked: every anchor reachable except the designed locks).
A builder replacing one must keep its size, buried layers, sidecar anchors (`data/oldrepublic/site_anchors/<same
path>.json`), jigsaw blocks (greater tomb) and the block entities' data (waystone `pending`/`local`, site and hub
spawner roles), and the art rule. (Milestone 6: every template below was rebuilt by the generators under
`tools/assets/structures/` (P1, D, H and QM), the greater tomb's deep pieces and the two underground expedition pieces at
their size; the new sizes are noted in the table.)

| Template | Size | Brief |
|---|---|---|
| `planet/waystone.nbt` | 11x9x11, 2 buried (Milestone 6: now 21x18x21) | A standing stone (the waystone its carved heart, face north) on old flagstones, in a ring of fallen stones; a cache cairn whose face is a veiled stone Sense reveals. |
| `planet/waystone_corrupted.nbt` | 11x9x11, 2 buried (Milestone 6: now 21x18x21) | The same with the corrupted stone, the ground wilted to coarse dirt and podzol, and the spawner of the twisted hollowhorns it wakes. |
| `planet/heartstone.nbt` | 19x12x19, 3 buried (Milestone 6: now 35x26x35) | The great corrupted stone (the heartstone its carved heart, dark veins running out from it) in the ring of standing stones where the cleansing is held. |
| `planet/wayfarer_cache.nbt` | 13x9x13, 4 buried (Milestone 6: now 27x25x27, 15 buried) | Tessaly's cache: a brass vault half in the ground, three sinking plates over its pit. |
| `greater_tomb/gate.nbt` | 21x14x21, 3 buried (Milestone 6: now 35x22x35, 6 buried) | Obelisk gateway, the Imperial dig camp (tents, crates, lamps, the droids' post) and the stair mouth. |
| `greater_tomb/descent.nbt` | 11x18x27 | A long carved stair down, a sentinel's landing. |
| `greater_tomb/urn_vault.nbt` | 21x12x21 | The urn puzzle (four urns, the keeper), the sealed plating door south. |
| `greater_tomb/sealed_crypt.nbt` | 21x12x21 | The crypt behind the plating: the relic, sentinels, the chest. |
| `hub/greenwatch.nbt` | 63x24x63, 3 buried (Milestone 6: now 95x51x95, 8 buried) | The half-ruined Watch enclave: a hall with a broken dome, cloisters, garden, tents, a four-bay moss-cracked pad. |
| `hub/proving.nbt` | 63x28x63, 3 buried (Milestone 6: now 95x59x95, 8 buried) | The Imperial academy outpost: barracks, drill yard, banners, searchlights, a four-bay pad with blast walls, the carved stair down. |
| `hub/sepulchre.nbt` | 33x16x41 (Milestone 6: now 55x16x51) | Zarvos's Sepulchre under the Proving: the stairwell continuing the Proving's, an antechamber, the hall, three trial rooms, the echo. |
| `expedition/singing_hollow_surface.nbt`, `singing_hollow_grotto.nbt` | 13x10x13 (3 buried), 31x16x31 (Milestone 6: the surface now 27x22x27) | A mossy mound over a sinkhole, a ladder down a rock column; the aurelite-lit grotto with six humming clusters, six hanging stones, the seal and the relic alcove. |
| `expedition/duskglass_rift_surface.nbt`, `duskglass_rift_rift.nbt` | 15x8x15 (2 buried), 23x28x31 (Milestone 6: the surface now 27x12x27) | A crack in the ash plain and a rope ladder; a chasm of black-glass ledges with ladders up every drop, down to the crystal. |
| `boss/hunting_camp.nbt` | 41x14x41, 4 buried (Milestone 6: now 57x16x57) | Kaast's arena: a pit, four nests on posts, the camp gate, six mine spots. |
| `mission/ambush_rocks.nbt` | 13x6x13, 1 buried (Milestone 6: now 23x12x23) | A tumble of rocks for a hunter ambush. |

Worldgen data (final, tuning may change spacing): `worldgen/structure/{waystone,greater_tomb}.json`,
`worldgen/structure_set/{waystones,greater_tombs}.json`, `worldgen/template_pool/waystone/start.json`,
`worldgen/template_pool/greater_tomb/{start,descent,vault,crypt}.json`, tags `worldgen/structure/{waystones,greater_tombs}`
and `worldgen/biome/{is_selvaren,is_varkhast,is_planet,has_structure/waystone,has_structure/greater_tomb}`. World data
(final, tuning may change): `dimension_type/{selvaren,varkhast}.json`, `dimension/*.json`, `worldgen/noise_settings/*`,
`worldgen/biome/<six>.json`, `worldgen/configured_feature/*` (15) and `placed_feature/*` (17); loot
`loot_table/blocks/{silverleaf,ashroot,aurelite_ore,deepslate_aurelite_ore,duskglass_cluster,budding_duskglass}.json`
and `loot_table/chests/{greater_tomb,wayfarer_cache_m4}.json`; `damage_type/duskglass_cut.json`;
`tags/block/force_reactive.json`.

## Sounds (under `src/main/resources/assets/oldrepublic/sounds/`)

All **placeholder generated** by `tools/assets/sounds/world_m4.py`; ids, subtitles and categories are in
`tools/assets/sounds/events_m4.json`. Loops must stay seamless in any replacement.

| File | Id | Length | What it is now | Brief for the final sound designer |
|---|---|---|---|---|
| `waystone/corrupt.ogg` | `waystone.corrupt` | 4 s loop | A low beating drone with a throb every 2 s and grit | Something wrong living inside old stone: a slow, sick pulse in time with the veins (every 2 s), low and close, unsettling but not a monster. Seamless. |
| `waystone/cleanse.ogg` | `waystone.cleanse` | 4.3 s | A falling filtered rumble with a clear tone rising through it | The darkness draining out of the stone as the stone's own clear note comes back up through it; relief. |
| `waystone/hum.ogg` | `waystone.hum` | 5.5 s | A low bell-like hum that blooms and fades | The clean stone's own voice: a soft, low, bell-like hum, felt more than heard; heard now and then near a clean stone, more at night. |
| `heartstone/wave.ogg` | `heartstone.wave` | 3.9 s | A deep surge, a sub thump and a dissonant swell | Corruption welling up out of the ground around the great stone; three waves of it, each played lower and louder. |
| `heartstone/cleansed.ogg` | `heartstone.cleansed` | 7.2 s | A reversed swell opening into a warm open chord | A long exhale after a fight: wide, warm, a held breath let go. |
| `hollow/chime.ogg` | `hollow.chime` | 4.6 s | A bright glassy bell at its root pitch | One crystal of the Hollow ringing; the client pitches it to three tones a third apart (and down for a wrong stone), so keep it a clean single pitch with a long ring. |
| `hollow/answer.ogg` | `hollow.answer` | 6.6 s | A cascade of chimes settling on an open chord | Every crystal in the grotto answering at once, settling into a chord that hangs in the cave. |
| `rift/whisper.ogg` | `rift.whisper` | 4.3 s (restarted every 2.5 s) | Breathy wordless whispers over a low glassy drone | Whispers from the black glass while the crystal is held: wordless, breathy, many voices; no intelligible words in any language. |
| `rift/bleed.ogg` | `rift.bleed` | 4.5 s | A glass crack, a groan sliding down, a dark bloom | The crystal turning: a crack of glass, a falling groan and a dark, ringing bloom. |
| `ambient/selvaren_highwood.ogg` | `ambient.selvaren.highwood` | 12 s loop | Wind high in the trees, leaves, far birds | Cool highland forest: wind high in tall trees, leaves, a few distant (invented) birds. Seamless; mixed low as a bed. |
| `ambient/selvaren_meadow.ogg` | `ambient.selvaren.meadow` | 12 s loop | An upland breeze, grass hiss, a thin insect hum | Open heather uplands under a wide sky; breeze and grass. |
| `ambient/selvaren_tarn.ogg` | `ambient.selvaren.tarn` | 12 s loop | Water lapping at stone, reeds, a long low call | A misty lake in a stone cup: lapping water, reeds, one long low call across the water. |
| `ambient/varkhast_redrock.ogg` | `ambient.varkhast.redrock` | 12 s loop | Dry wind whistling through hoodoos, skittering grit | Dry wind over red rock and through hoodoos; grit. |
| `ambient/varkhast_ashfall.ogg` | `ambient.varkhast.ashfall` | 12 s loop | A soft high hiss of falling ash over low wind, faint crackle | Ash falling on a grey plain: a soft steady hiss and a low wind. |
| `ambient/varkhast_tomb_valley.ogg` | `ambient.varkhast.tomb_valley` | 12 s loop | Wind moaning along the valley, far dry thunder | Wind moaning along carved cliffs and far thunder that brings no rain. |
| `ambient/greenwatch.ogg` | `ambient.greenwatch` | 12 s loop | Wind in the clearing, a distant mallet on wood, a faint chime, birds | The enclave quietly at work: tools on wood, a chime, a clearing in a forest. |
| `ambient/proving.ogg` | `ambient.proving` | 12 s loop | Wind off the cliff, a wordless filtered tannoy, boots and clanks | An Imperial outpost: distant wordless loudspeaker, drill boots, metal; no intelligible words. |
| `music/selvaren.ogg` | `music.selvaren` | 48 s loop | An open Dorian pad shifting between two chords, a sparse bell melody | Selvaren's music: old, open, patient; cool strings or pads and a few bell notes. Original composition, no resemblance to any franchise theme. |
| `music/varkhast.ogg` | `music.varkhast` | 48 s loop | A low Phrygian drone, a frame-drum pulse, a severe low line | Varkhast's music: severe, dry, martial without marching; low drones and drums. Original composition, no resemblance to any franchise theme. |
