# Planets design: Selvaren, Varkhast, their sites and hubs (Milestone 4, W1 worlds)

Owner: W1 (worlds). Covers spec `docs/MILESTONE_4.md` section 3 (planets, biomes, sites, hubs, the terrain palette),
the W1 rows of 13 (effects, particles, sounds), the `planets` balance section (14), the W1 ids of 16, and the W1 tests
of 17.1 and 17.2. The story uses of every site (keepers, vendors, quests, contracts) are W2's; the pads as travel
endpoints and `TravelService` are W3's; the creatures and the hunting camp fight are W6's.

Status legend for APIs: **[V]** verified against the 26.2 dumps or code that compiled in M1 to M3, **[U]** unverified
(marked `// API-CHECK` and listed in `tools/api-probe/m4_w1.txt`).

## 0. Implementation status

Implemented as specified, with these differences:

- **Cut item 1: Selvaren's second moon** is cut (spec 1, cut order). Nothing draws in `LevelRenderEvents`.
- **Biome ambience beds are a client emitter** (`client.planet.PlanetAmbience`), not biome JSON `ambient_sounds`: the
  26.2 fields of the biome's ambient loop and additions are unverified [U]. The same goes for **ashfall**: the
  `AmbientParticle` fields of `EnvironmentAttributes.AMBIENT_PARTICLES` are unverified [U], so the flakes come from the
  documented fallback, a `ClientTickEvents` emitter bounded by `Vfx.count` (3.1). Both can move into data once the
  probe in `tools/api-probe/m4_w1.txt` answers.
- **Greater tomb crypt templates** are `greater_tomb/urn_vault` and `greater_tomb/sealed_crypt` instead of the spec's
  working names `crypt_a` and `crypt_b` (16): W2's relic check keys on the word `crypt`, which must name the sealed one
  only. The jigsaw chain is gate, descent, urn vault, sealed crypt (each joined south). The spec's `path` and
  `dig_camp` pieces of the greater tomb are folded into the gate (its yard holds the Imperial dig camp).
- **Hub beds follow `planetAmbience`** like the biome beds (the M3 site beds do not); with planet ambience off, a hub
  is silent apart from its people.
- The hub search has a middle step the spec does not list: when no ring has a bare 63 by 63 square (Selvaren's
  highwood rarely has one), the same rings are searched again with trees allowed, and the trees and loose ground in the
  hub's box are cleared first. The stilt platform at (0, 0) stays the last resort.
  (Milestone 6: the square is the template's own, 95 by 95, and a third step fits the hub to the ring stand with
  the most even ground before the stilts.)
- Runtime sites get the same middle step (`PlanetSiteSearch`): when no loaded ring candidate is a bare, level square
  of story ground, a grid around the ring point (every 8 blocks within 96, near the rings) is searched with trees
  allowed and up to 8 blocks of slope; the flattest stand is cleared and the site placed there facing the hub. The
  first failed attempt per owner and site logs why every candidate was refused (`PlanetSiteSearch.explain`).

## 1. Packages and classes

| Class | Role |
|---|---|
| `planet.PlanetFeature` | Common init, called by the lead: blocks, items, particles, effect ids, balance, worldgen hooks, commands, events |
| `planet.PlanetBalance` | Balance section `planets` (14) |
| `planet.PlanetBlocks`, `planet.block.*` | The planet blocks (2) |
| `planet.PlanetParticles`, `planet.PlanetEffects`, `planet.PlanetDamage` | Particle types, effect event ids, the `duskglass_cut` damage type |
| `planet.worldgen.PlanetWorldgen`, `PlanetDecorFeature` | Biome keys and tags, palette rows, the hand-written decoration features |
| `planet.TerrainPalette` | The ground a runtime site is fitted with, per biome (3.6) |
| `planet.PlanetHubs` | Places each hub once per server (3.5); the hub spawn guard |
| `planet.PlanetSites`, `planet.PlanetSiteData` | Runtime planet sites (3.4) and where they are |
| `planet.PlanetSiteSearch` | The sites' clearing search when no ring candidate fits, and the report of why a site found no spot |
| `planet.Waystones`, `planet.Heartstones` | Waystone corruption and cleansing, the Heartstone's look and result |
| `planet.Expeditions` | The Singing Hollow, the duskglass rift and the Wayfarer cache puzzles (8.4) |
| `planet.HubSpawners` | The hub people's spawn roles; W2 and W6 register factories |
| `planet.PlanetRespawn` | Respawn at the hub medbay (12.2) |
| `planet.PlanetCommands` | `/oldrepublic planet hub|site|cleanse` |
| `client.planet.*` | Particles, effect handlers, ambience (beds, ashfall, corrupted stones, block display ticks) |
| `client.world.site.SiteAmbience` | Gains the Greenwatch and Proving beds and the M4 site scenes |
| `world.site.*` (M4 additions) | `SiteKind`s, `StructureLocator` tags, `SiteAnchors` M4 anchors, `SiteCommands` planet templates |

## 2. Planets, biomes and blocks

**Dimensions.** `data/oldrepublic/dimension_type/{selvaren,varkhast}.json` copy the overworld type (skylight, no
ceiling, -64 to 320, the overworld clock and timelines) and set the planet's attributes: sky, fog and cloud colours,
`background_music` (`music.selvaren`, `music.varkhast`), the cave mood sound, `bed_rule` and `respawn_anchor_works`
false. `dimension/*.json` use a `multi_noise` source over the planet's three biomes with explicit climate points and
the planet's noise settings (Selvaren: sea level 58, deeper valleys; Varkhast: sea level -64, aquifers off, terracotta
and red sandstone bands, no oceans). The planet biomes are in `#oldrepublic:is_planet` and their planet's tag, in no
vanilla `has_structure` tag and not in `#minecraft:is_overworld`, so no vanilla or M3 structure grows there. Vanilla
portals do not light on a planet (vanilla `BaseFireBlock#inPortalDimension`); `PlanetGameTests.vanillaRefusesPortalsOnPlanets` guards it.

| Biome | Palette row (top, fill, base) | Features |
|---|---|---|
| `selvaren_highwood` | grass block, dirt, stone | silver trees (birch with pale tinted leaves), saplings, spruce, moss and fern floor, silverleaf, berries; aurelite ore |
| `selvaren_meadow` | grass block, dirt, stone | standing stones, scattered trees, silverleaf, tall grass; aurelite ore |
| `selvaren_tarn` | grass block, dirt, stone | gravel and clay shores, aurelite in exposed cliffs, sugar cane, firefly bushes; aurelite ore |
| `varkhast_redrock` | red sand, red sandstone, stone | hoodoos, dead trees, ashroot in gully shade, dead bushes, dry grass |
| `varkhast_ashfall` | coarse dirt, tuff, stone | basalt spires, dead trees, dead bushes |
| `varkhast_tomb_valley` | red sand, smooth sandstone, stone | obelisks, carved walls, duskglass walls, ashroot, dead bushes |

**Blocks.** `silverleaf` and `ashroot` (plants, drop themselves), `aurelite_ore` and `deepslate_aurelite_ore`
(`aurelite_shard`), `duskglass_cluster` (`duskglass_shard`; cuts for `planets.duskglassDamage`, `duskglass_cut`) and
`budding_duskglass` (grows clusters, drops nothing), `waystone` and `waystone_corrupted` (block entity: corruption
decision, who it is cleansed for), `heartstone` (block entity: who it is cleansed for), `sealed_plating`
(unbreakable; opened only by the fusion cutter `SealedPlatingBlock.cut` or a Force Push `push`, the whole connected
sheet, at most `MAX_SHEET` blocks) and `hub_spawner` (no item; a role and its last person).

## 3. Sites and templates

All templates are generated by `tools/assets/gen_structures_m4.py` (deterministic) with sidecar anchors in
`data/oldrepublic/site_anchors/`. (Milestone 6: all were rebuilt by the generators under `tools/assets/structures/`;
the new sizes are noted in the table. The runtime sites on rings around the hub are placed by far placement between
their first and last ring before any ring search, and both worldgen sets keep 256 blocks clear of (0, 0).) Art rule:
no floor circles, sigils or glowing runes; the waystones are carved stone with plain courses, the corrupted one has
dark veins in its texture only.

| Template | Size | Buried | Anchors | Placed by |
|---|---|---|---|---|
| `planet/waystone`, `planet/waystone_corrupted` | 11x9x11 (Milestone 6: now 21x18x21) | 2 | `STONE`, `CACHE`, `HEART` (+ `GUARD` corrupted) | worldgen `oldrepublic:waystones` (spacing 20, separation 8, salt 905117) |
| `greater_tomb/gate` (start, adapted), `descent`, `urn_vault`, `sealed_crypt` | 21x14x21, 11x18x27, 21x12x21, 21x12x21 (Milestone 6: the gate now 35x22x35, the rest re-dressed at their size) | 3, 0, 0, 0 (Milestone 6: the gate 6) | gate: `STAIR_MOUTH`, `SURFACE_GUARD`, `SQUAD`, `GUARD`, `CHEST_1`; vault: `URN_KEEPER`, `URN_1..4`, `DOOR_SEAL`, ...; crypt: `HEART`, `RELIC`, ... | worldgen `oldrepublic:greater_tombs` (spacing 36, separation 14, salt 905123) |
| `hub/greenwatch`, `hub/proving` | 63x24x63, 63x28x63 (Milestone 6: now 95x51x95, 95x59x95) | 3 (Milestone 6: 8) | `HEART` (pad centre), `PAD_BAY_1..4`, `MEDBAY`, `HOST`, `VENDOR_1..4`, `RESIDENT_*`, `PAD_TERMINAL`, `CONTRACT_TERMINAL` (+ `STAIR_DOWN`) | `PlanetHubs.ensure` |
| `hub/sepulchre` | 33x16x41 (Milestone 6: now 55x16x51) | 0 | `HEART`, `ROOM_1..3`, `ECHO`, ... | with the Proving, at `SEPULCHRE_OFFSET` |
| `planet/heartstone` | 19x12x19 (Milestone 6: now 35x26x35) | 3 | `HEART`, `STONE` | `PlanetSites.ensure(HEARTSTONE)`, rings 80/100/120 (Milestone 6: 250/325/400) |
| `expedition/singing_hollow_{surface,grotto}` | 13x10x13, 31x16x31 (Milestone 6: the surface now 27x22x27) | 3, sunk | `HEART`; grotto: `CHIME_1..6`, `WEIGHT_1..6`, `GATE`, `RELIC` | `PlanetSites.ensure`, rings 300/375/450 (Milestone 6: 400/525/650) |
| `expedition/duskglass_rift_{surface,rift}` | 15x8x15, 23x28x31 (Milestone 6: the surface now 27x12x27) | 2, sunk | `HEART`; rift: `RELIC`, `SQUAD_1..2` | same |
| `planet/wayfarer_cache` | 13x9x13 (Milestone 6: now 27x25x27) | 4 (Milestone 6: 15) | `HEART`, `CACHE`, `PLATFORM_1..3` | rings 150/200/250 (Milestone 6: 350/450/550) |
| `boss/hunting_camp` | 41x14x41 (Milestone 6: now 57x16x57) | 4 | `HEART`, `NEST_1..4`, `GATE`, `MINE_1..6` | rings 400/500/600 (W6 calls `PlanetSites.ensure`) |
| `mission/ambush_rocks` | 13x6x13 (Milestone 6: now 23x12x23) | 1 | `HEART` | 24 to 32 blocks around the player, 96 from the hub and every site |

**Waystone corruption** is decided once, on the stone's first block-entity tick, seeded by its position only
(`Waystones.corrupted(pos, chance)`, `planets.waystoneCorruptedChance` 0.25): a corrupted result stamps
`planet/waystone_corrupted` over the same cells and rotation. Same seed, same stones, on every server; no processor.
**Cleansing** is per player: a Sense cast within `planets.waystoneSenseRange` cleanses a corrupted stone for the
caster only (`Waystones.cleanse`), plays the cleanse and calls `QuestHooks.onWaystoneCleansed`. The corrupt ambience is
sent only to players within `planets.waystoneAmbienceRange` it is not cleansed for.

**Hubs** (`PlanetHubs.ensure`): a `PORTAL` ticket of `planets.hubTicketRadius` chunks around (0, 0), refreshed while
waiting; when all are loaded, the rings `hubRings` are searched (footprint `hubFootprint`, slope `hubMaxSlope`), then
the same rings with trees allowed, then a stilt platform at (0, 0). (Milestone 6: `hubFootprint` is gone, the
footprint is the template's own size and the slope its site meta's, and before the stilt platform the hub is fitted to
the ring stand with the least spread of ground; the ticket's radius grows with the hub, and the ticket is taken one
chunk wider: 15 by 15 chunks for each hub with the defaults.) One placement per server (`ensureIn`); positions in
`PlanetSiteData`, templates in `WorldSites`. The Proving places the Sepulchre under it. A hub's box keeps no wild
creatures: placement clears them and natural spawns are refused in the hub's and the Sepulchre's boxes
(`PlanetHubs.allowLoad`). People spawn from `hub_spawner` blocks by role (`HubSpawners`; W2 registers the factories),
restored once per `planets.hubRestoreTicks` while dead or gone.

**Runtime sites** (`PlanetSites.ensure(owner, site, planet)`): returns the site's `HEART` position, placing it first
when its owner is within `planets.siteEnsureRange` of the ring point in the planet; null while it cannot be placed
yet (chunks not loaded, neither the rings nor the clearing search around the ring point fits), retried at most every
`planets.siteRetryTicks`. The ring point is seeded by
owner and site (by the hub for the shared Heartstone). Expeditions add their underground piece right under the
surface piece (`RuntimeSites.placeSunken`, stamped anyway if the sunken checks refuse).

**The Heartstone** (`Heartstones`): its block entity records who it is cleansed for and sends the corrupt refresh
while the nearest player has not cleansed it. The trial is W2's: it finds the stone with
`Heartstones.stone(level, heart)` (the `HEART` position `PlanetSites.ensure` returned), calls `Heartstones.wave(level,
stone, n)` as each wave starts and `Heartstones.complete(player, stone)` at the end (records it, plays the exhale and
reports `QuestHooks.onWaystoneCleansed(player, stone, true)`).

**Expeditions** (`Expeditions`): the Singing Hollow's three humming clusters in an owner-seeded order, revealed by
Sense, answered by Pushing the counterweights under them; the rift crystal held for `planets.riftBleedTicks` while the
whispers play; the Wayfarer cache's sinking platforms (`sinkingPlatformTicks`, `platformResetTicks`).

## 4. Effects and sounds (13), phase by phase

All particles go through `client/fx/Vfx` (`inRange`, `count`); with flashing off, glints become steady motes; the
only shake (a Heartstone wave) goes through `CameraShake`, which applies the shake setting. Nothing draws on the
ground: mist and dust rise from irregular vents with bare ground between them. Server-authoritative: every event is
sent through `network/EffectEvents` (`around`, or `toPlayer` for the per-player corrupt refresh).

### `waystone` (around; 0 corrupted ambience, 1 cleanse)

| Phase | Look | Sound |
|---|---|---|
| Anticipation (the stone is corrupted for you) | The server refreshes every 40 ticks (`toPlayer`, intensity = keep-alive 3 s) to each player within 16 blocks it is not cleansed for. Dark-vein motes (dust `#2A1220`) seep from the open faces of the stone's column in a slow pulse: 12 ticks of every 40, 6 per second, within 8 blocks | `waystone.corrupt`: a seamless 4 s low beating drone with a throb every 2 s and grit, faded in over a second |
| Activation (a Sense cast cleanses it) | Black grit (falling dust of blackstone, 14) sifts off the stone's faces | `waystone.cleanse`: the darkness drains as a falling filtered rumble while a clear tone rises |
| Travel | For 20 ticks the veins drain: dark dust slides down the four faces into the ground | |
| Impact | At tick 16 the stone's own pale motes rise (10) | `waystone.hum`, the stone's own voice returning |
| Aftermath | The clean stone: a pale mote now and then and its hum, rarely by day (1 in 90 display ticks), more often at night (1 in 40) | `waystone.hum` |

Stop rules: the corrupt loop fades for the cleanser only (the cleanse names the player), lapses 3 s after the last
refresh (leaving 16 blocks), is released at once beyond 20 blocks, on death, and is killed on disconnect and on level
change. The cleanse plays for everyone near; other players keep their own loop.

### `heartstone` (around; 0 wave 1 to 3, 1 complete)

| Phase | Look | Sound |
|---|---|---|
| Anticipation (the stone is corrupted for the nearest player) | Slow dark mist curls from the stone's base while a corrupt refresh names it (the Heartstone sends the `waystone` refresh too) | `waystone.corrupt` loop |
| Activation (a wave starts, W2 calls `Heartstones.wave`) | A ragged burst of dark mist (10 to 18) at 3 + wave vents around the base; a light shake within 12 blocks, stronger each wave | `heartstone.wave`: a deep surge, a sub thump and a dissonant swell, louder and lower each wave |
| Travel | For 2 s the mist keeps welling at the same vents (fixed per wave, so it comes in uneven patches), densest early | |
| Impact (complete, `Heartstones.complete`) | The wave's mist stops at once | `heartstone.cleansed`: a long exhale, a reversed swell opening into a warm open chord |
| Aftermath | For 4 s pale motes leave the stone's faces and drift up and away, swelling and fading like a breath | |

### `crystal_answer` (around; 0 Hollow chime 0 to 3, 1 rift bleed, 2 the Hollow answers)

| Phase | Look | Sound |
|---|---|---|
| Activation: a chime (tone 1 to 3) | Aurelite glints (5 to 9, or 2 steady motes with flashing off) and rising crystal motes on the cluster | `hollow.chime` pitched by tone: 0.794, 1, 1.26 (a third, the root, a third up) |
| Wrong stone (tone 0) | A little stone grit falls | `hollow.chime` at pitch 0.5, quieter: a dull clunk |
| Impact: the Hollow answers (variant 2) | For 2 s glints run over every crystal cluster and the open faces of aurelite within 10 blocks (at most 48 points) | `hollow.answer`: a cascade of chimes settling on an open chord |
| Rift, anticipation (BEGIN) | While held: the crystal seeps red that darkens from bruise to blood (dust, lerped), and black slivers are drawn in from the walls every 3 ticks | `rift.whisper` every 2.5 s, louder as the bleed deepens |
| Rift, impact (DONE) | A burst of black slivers (18) up and out, red drops (14) and dark motes (8) | `rift.bleed`: a glass crack, a groan sliding down and a dark bloom |
| Rift, broken (BROKEN) | Everything stops | the whisper stops at once |

The bleed is found by its holder's entity id (a break is sent from where the holder stood), and lapses on its own
100 ticks after the hold should have ended if a "done" is lost.

### Particles

| Type | Look | Where |
|---|---|---|
| `ash_flake` | Grey flakes, 3 sprites, translucent; carried by the wind with a slow sway, fall capped, fade on landing; 90 to 150 ticks | Varkhast ashfall |
| `aurelite_glint` | A gold four-point twinkle, 4 frames, full-bright | Aurelite veins at air faces, Hollow chimes and answer |
| `duskglass_shard` | A black glass sliver with a red edge, 3 sprites, tumbling, falls, fades late | Duskglass clusters, the rift bleed |

### Ambience

- **Ashfall** (Varkhast, client): flakes start around the camera under open sky only, upwind so the drift carries them
  past: 40 alive at Standard in the ashfall biome, twice that in the storm (the level's rain flag, which never wets
  Varkhast), half the rate elsewhere on Varkhast where they land only over nearby ashfall, or a quarter of them anywhere
  during the storm. The wind turns over five minutes and gusts each minute. Scaled by `planets.ashfallDensity`, the
  preset and the `Vfx` budget; follows `planetAmbience`.
- **Weather**: only the overworld counts down the shared weather timers (`mixin.planet.PlanetWeatherCycleMixin`); a
  planet eases its own rain and thunder toward the shared state, so its storm follows the home world's weather.
- **Biome beds** (client): one non-positional loop per biome (`ambient.selvaren.*`, `ambient.varkhast.*`, 12 s
  seamless), crossfaded over 4 s as the player crosses biomes, ducked to 0.35 under cover and 0.4 under a site's own
  bed, dipped by Sense; released on death, stopped on dimension change, killed on disconnect; off with `planetAmbience`.
- **Hub beds** (`SiteAmbience`): `ambient.greenwatch` and `ambient.proving` while in the hub (site presence), off with
  `planetAmbience`. Greater tombs use the M3 tomb scenes (surface at the gate, underground below); the Sepulchre and
  the rift the tomb's underground scene; the Hollow's grotto the ruin's underground scene.
- **Block display ticks**: duskglass slivers fall and dull red catches in the glass; aurelite glints at air faces; the
  clean waystone's pale mote and hum; the Heartstone's mist while corrupted for you.
- **Music**: `music.selvaren` (an open Dorian pad and a sparse bell line, 48 s) and `music.varkhast` (a low Phrygian
  drone, a frame-drum pulse and a severe low line, 48 s) through the dimension type's `background_music`.

### Final sound of each event

| Event | Sound(s) | Category |
|---|---|---|
| `waystone` 0 | `waystone.corrupt` (loop) | blocks |
| `waystone` 1 | `waystone.cleanse`, then `waystone.hum` | blocks |
| `heartstone` 0 | `heartstone.wave` | blocks |
| `heartstone` 1 | `heartstone.cleansed` | blocks |
| `crystal_answer` 0 | `hollow.chime` (pitched by tone; 0.5 for a wrong stone) | blocks |
| `crystal_answer` 1 | `rift.whisper` (repeated while held), `rift.bleed` on done | blocks |
| `crystal_answer` 2 | `hollow.answer` | blocks |

All 19 W1 sounds are original placeholders synthesised by `tools/assets/sounds/world_m4.py` (see
`docs/assets/planets.md`); render with `python3 tools/assets/gen_sounds.py --m4 --feature world_m4`.

## 5. Balance (`planets`, 14)

`respawnAtHub` true; `hubRings` 0/16/32; `hubFootprint` 63 (removed in Milestone 6); `hubMaxSlope` 6;
`hubTicketRadius` 5; `heartstoneRing` 80/100/120 (Milestone 6: 250/325/400); `siteEnsureRange` 160; `expeditionRings`
300/375/450 (Milestone 6: 400/525/650); `cacheRings` 150/200/250 (Milestone 6: 350/450/550); `campRings` 400/500/600;
`ambushRings` 24/28/32; `ambushClearance` 96; `siteRetryTicks` 40; `waystoneCorruptedChance` 0.25;
`waystoneSenseRange` 8; `waystoneAmbienceRange` 16; `ashfallDensity` 1.0; `duskglassDamage` 2; `sinkingPlatformTicks`
60; `platformResetTicks` 200; discovery XP (paid by W2): planet 400, hub 200, site 150, greater tomb 300;
`hollowHumTicks` 200; `riftBleedTicks` 100; `hubRestoreTicks` 24000.

## 6. Commands

- `/oldrepublic planet hub <selvaren|varkhast>`: places the hub if needed and replies with its position.
- `/oldrepublic planet site <player> <site>`: `PlanetSites.ensure` for the player in their planet (clears their retry
  wait first); replies with the position, or the ring point while it cannot be placed yet.
- `/oldrepublic planet cleanse <player>`: cleanses the nearest corrupted waystone within 16 blocks for the player.
- `/oldrepublic site place <waystone|greater_tomb|template id> <pos>` (M3 command, M4 entries): the worldgen
  structures, or any planet template with its buried layers.

## 7. Tests

**`PlanetGameTests`** (17.1; planets do not exist on the game-test server, so these test the rules, the data and the
templates): `biomesInNoVanillaStructureTag`, `planetBiomesHavePaletteRows`, `hubEnsurePlacesOncePerServer`,
`hubFallbackPlatformWhenNoRingFits`, `planetSiteEnsureRetriesUntilChunksExist`, `waystoneCleansePerPlayer`,
`corruptedChanceSeededByPosition`, `sealedPlatingOnlyByCutterOrPush`, `vanillaRefusesPortalsOnPlanets`,
`everyM4TemplateHasSidecarAnchorsInsideSize`.

**`m4/PlanetClientSteps`** (17.2, run by `M4ClientTest`): both hubs placed through `PlanetHubs.ensure`, one cross-level
move per planet through `TravelService.moveAcross`; asserts each biome is found within 2400 blocks of its hub, a
waystone (Selvaren) and a greater tomb (Varkhast) placed with `/oldrepublic site place` have exactly their pieces and
clean anchor cells (`STONE` a waystone, `DOOR_SEAL` sealed plating), the worldgen waystone decides its corruption by
position, each hub is a recorded `planet_hub` piece and each expedition is placed with its underground piece.
Screenshots: 10 `<biome>_noon` and 11 `<biome>_dusk` for all six biomes, 12 `varkhast_ashfall_storm`, 13
`waystone_clean`, 14 `waystone_corrupted`, 15 `greater_tomb_gate`, 16 `greenwatch_from_pad`, 17 `proving_from_pad`,
18 `singing_hollow`, 19 `duskglass_rift`.
