# Planets design, Milestone 5: Ghessar, Tyrvann, their sites and weather (W3 worlds)

Owner: W3 (worlds). Covers spec `docs/MILESTONE_5.md` section 3 (Ghessar, Tyrvann, the detention post, per-dimension
weather, ambience), the W3 rows of 15 (effects, particles, sounds), the M5 keys of the `planets` balance section
(16.4), the W3 ids of 18, and the W3 tests of 19.1 and 19.2. The M4 design (Selvaren, Varkhast, the hub and site
machinery this builds on) is `docs/design/planets.md`. Story uses of the sites (Vessba, Varra, Dask, the verses, the
Hymnal fight) are W4's and W8's; flight and the pads as landing spots are W1's; the orbits are W2's.

Status legend for APIs: **[V]** verified against the 26.2 dumps, the probe digests or code that compiled before,
**[U]** unverified, marked `// API-CHECK` in the code and listed in the W3 report for the lead's probe.

## 0. Implementation status

Implemented as specified, with these differences:

- **Cut item 1: Ghessar's twin sun** is cut (spec 1, cut order). No second disc is drawn; the client test's shot 139
  is dropped with it. Ghessar's sky is its dimension type's (a hot, pale haze).
- **Biome and hub beds are client emitters** (`client.planet.PlanetAmbience`, `client.world.site.SiteAmbience`), as in
  M4: the 26.2 biome `ambient_sounds` fields are still unverified [U].
- **The Hymnal is placed by its own path, not by `/oldrepublic site place`.** The command places one template; the
  Hymnal is two (the arch, and the halls sunk under it by `Hymnal.sinkHalls`). The client steps stand over its ring
  point and let `PlanetSites.ensure` place it (the M4 expedition recipe); the verse stone is placed with the command.
- **The seed survey has no M5 positions**, so the client steps find each biome with `findClosestBiome3d` within 2,400
  blocks of its hub (the M4 rule) instead of fixed coordinates.
- **The verse stone's hum slows through a pure rule** (`VerseStones.humSlows`): mock players in game tests are
  creative, so the test checks the rule with a survival fake player as well as the live pulse.
- **The storm flash is dimmed, not removed**: with `storm_flashes` or `flashing` off, lightning's whole-sky flash is
  dropped and a steady, low brightening (at most 12% white, rising over 6 ticks, falling over 24) stands in for it,
  so a strike still reads.

## 1. The worlds

Both planets are data dimensions made by `tools/assets/gen_structures_m5.py` (the planet part) like Selvaren and
Varkhast: `dimension/{ghessar,tyrvann}.json`, `dimension_type/{ghessar,tyrvann}.json` (each with its music,
`music.ghessar` and `music.tyrvann`, as the M4 planet tracks), `worldgen/noise_settings/{ghessar,tyrvann}.json` with
their density functions, and a multi-noise biome source of three biomes each. The skies are the biomes': a pale, hot
sand-gold over Ghessar, a low green-grey over Tyrvann. `PlanetWorldgen` holds the six biome keys and their terrain palette rows, and
`PlanetDecorFeature` seven new decor kinds.

| Biome | Ground | Decoration | Bed |
|---|---|---|---|
| `ghessar_dunes` | sand, sandstone, salt crust in the hollows | bleached ribs (old bones of something huge), half-buried | warm steady wind, sand hissing off the crests |
| `ghessar_saltflat` | salt crust over sandy salt | salt pillars | thin high air, the crust ticking in the heat |
| `ghessar_canyons` | red sandstone and terracotta strata | sunstone ore in the walls | wind through the narrows, far rockfall, a high cry |
| `tyrvann_stormwood` | mud, packed mud, moss | stormwood trees and giants (mangrove-built), hanging moss | rain on leaves, gusts, far thunder |
| `tyrvann_mire` | mud, peat, black water pools | low trees, mire roots, mire pools, peat seams | rain on still water, bubbling mud, croaks |
| `tyrvann_basalt` | basalt and blackstone ridges, calcite seams | stormglass on the tops | hard wind, sideways rain, nearer thunder |

The biome tags `is_ghessar`, `is_tyrvann` and `is_planet` (four planets) gate the worldgen structures and the M4
rules (no vanilla structure lists them). Precipitation flags are set as the weather table needs them (Ghessar none,
Tyrvann rain), though the held levels of 5 rule what the player sees.

## 2. Blocks and items

| Block or item | Behaviour |
|---|---|
| `salt_crust` | Ghessar's flats; shovel. Story ground (requested from the tag owner). |
| `sunstone_ore` | in the canyon walls; drops `sunstone`; a faint heat shimmer above it by day (client ambience). Pickaxe, iron tool (requested). |
| `stormglass_cluster` | grows where real lightning strikes exposed basalt (5); drops `stormglass_shard`; static sparks now and then. |
| `peat` | the mire's fuel: burns for `PlanetM5Feature.PEAT_FUEL_SMELTS` (12) items, 1.5 times coal; shovel. |
| `verse_stone` (block only, block entity) | the monolith's heart; `awake` state; unbreakable; Force-reactive (6). |
| `holdfast_gate` (block only) | the holdfast's gate plates; `open` state; unbreakable (7). |
| `sunstone`, `stormglass_shard` | crafting materials (W7's recipes use them). |

Loot: `loot_table/blocks/{salt_crust,peat,sunstone_ore,stormglass_cluster,verse_stone,holdfast_gate}.json` (the last two
empty) and the chests of the four worldgen structures (`loot_table/chests/{crashed_freighter,kajidic_post,hymn_shrine,
mire_grave}.json`). The crashed freighter's hold is where W7's Sunstone Edge schematic rolls (`freighterEdgeChance`,
`freighterEdgePity`).

## 3. Hubs

`hub/saltgate` (63x24x63, 3 buried; Milestone 6: now 89x44x89, 1 buried) is Ghessar's port, walled, of sand-scoured
prefab and old freighter hulls: the main gate north; the town in the north half (the cantina with the counting-house
behind it, eight rooms with Dask upstairs; the market street with the vendor fence and Varra; Vessba's audience hall
east); the four-bay pad behind its own gate in the south half between the medbay and the hangar (the shipwright and
the spacer); six enforcer posts on the walls. `hub/holdfast` (57x26x57, 3 buried; Milestone 6: now 83x48x83, 1 buried)
is Clan Hask's camp on a deck on stilts above the mire's edge, ringed by a clan-iron wall with the gate (7) in its
north side and a stair up from the mud: Dorrun's longhouse and the forge by the fire yard, the medbay, the spacer, the
barracks and the stores round the four-bay pad, six warrior posts, and lightning rods on every roof. Both are placed
by `PlanetHubs` with the M4 searches (ring stands, tree-tolerant stands, the raised platform as the last resort;
Milestone 6 fits a hub to the ring stand with the least spread of ground before the platform), and both are
`planet_hub` pieces with sidecar anchors.

## 4. Sites

Runtime sites (`PlanetSites`, ring searches around the hub, which Milestone 6 put after far placement; a site with a
biome of its own has its ring point moved to the nearest point of that biome and keeps to it, with a logged
last-resort clearing anywhere near):

| Site | Planet, biome | Pieces | Notes |
|---|---|---|---|
| `VERSE_STONE_1..3` (per player) | Tyrvann: stormwood, mire, basalt | `site/verse_stone` (15x10x15, 2 buried; Milestone 6: now 23x21x23) | the monolith on old flagstones; the stone is given its owner, index and guards' spot (6) |
| `HYMNAL` (one per server) | Tyrvann, basalt | `site/hymnal_arch` (15x31x15, 17 buried) and `site/hymnal` (37x28x79), both re-dressed at their size in Milestone 6 | the halls are stamped under the arch at `Hymnal.HALLS_OFFSET`, sunk further where the ridge is thin, joined by a walled ladder shaft; its position is the arch's `STAIR_MOUTH` |
| `DEBTOR_CAMP` (per player) | Ghessar, canyons | `site/debtor_camp` (23x10x23, 2 buried; Milestone 6: now 35x17x35) | the debtors' camp of "A Debt in the Canyons" (W4) |
| `DETENTION_POST` (per player) | Varkhast | `site/detention_post` (31x16x31, 2 buried; Milestone 6: now 49x28x49) | the Imperial post of 3.4: cells, the warden's office, the lever; W5 spawns its people |

The Hymnal's halls run the processional (three flights and landings with their squads and sentinels, the stormback
guard, two storm-caller posts), the Heart (a domed round hall 31 across: the dais, the lectern, four rods, two pylons,
four resonators) and the undercroft under it (the keystone and the Cantor's ring). The storm-caller posts are group
spawners of `storm_callers`, which `Hymnal.init` registers (one W8 Hymnsworn storm-caller each). Cosmetic bolts
(`setVisualOnly`) strike the arch's keystone every `hymnalArchInterval` ticks while a player is within 128 blocks.

Worldgen structures (single-piece jigsaw starts with their structure sets and tags, found only through their tags,
`StructureLocator.structureTag`; Milestone 6: the sets keep 256 blocks clear of (0, 0), where the hub stands):

| Structure | Biomes | Spacing, separation | Template |
|---|---|---|---|
| `crashed_freighter` | dunes | 28, 10 | 33x14x17, 4 buried (Milestone 6: now 70x26x31, 5 buried): an old freighter half-buried in the dunes, a torn breach over a sand spill into the hold (its chest and the cargo crate), the contract crews' spots |
| `kajidic_post` | canyons | 32, 12 | 21x14x21, 1 buried (Milestone 6: now 43x27x45, 3 buried): a fortified kajidic waystation, a walled sandstone yard with corner turrets, a two-storey tower and a trade stall; W8's cartel enforcers hold it |
| `hymn_shrine` | stormwood, mire | 30, 10 | 17x10x17, 1 buried (Milestone 6: now 33x26x33, 2 buried): eight carved stones round a basalt cairn crowned with stormglass, a Hymnsworn camp |
| `mire_grave` | mire | 34, 12 | 13x9x13, 3 buried (Milestone 6: now 25x13x25, 2 buried): a fallen warrior's cairn sunk in the mud, a blade in its head, the chest in a shallow pit |

## 5. Weather (3.5)

`PlanetWeather` (server) and `mixin.planet.PlanetWeatherCycleMixin`:

- **Ghessar** holds rain and thunder at 0, **Tyrvann** at 1: `advanceWeatherCycle` is cancelled there with both
  levels set, so no start or stop packet comes from those levels, and every 20 ticks (and on the tick the overworld's
  rain starts or stops) `RAIN_LEVEL_CHANGE` and `THUNDER_LEVEL_CHANGE` at the held level are resent to the players in
  each. The overworld's stop packet reaches every level and sets the client's rain to 1 for its fade; the resend in the
  same tick puts it back to 0 on Ghessar. Selvaren and Varkhast keep the M4 behaviour (ease toward the shared state;
  only the overworld advances the timers). Lightning on Tyrvann never spawns skeleton-horse traps.
- **Sandstorms** run on Ghessar's own cycle: clear spells of `sandstormIntervalDays` days and storms of
  `sandstormTicks` ticks, each spell seeded from the world seed and its start tick (deterministic, so the test can
  replay it). The state (`kind`, `untilTick`) is the server-global attachment `oldrepublic:planet_weather` (saved:
  a storm survives a restart) and is sent as the `planet_weather` payload on join, respawn, level change and change.
  A storm rising or passing plays the `sandstorm` effect and an overlay line to everyone in Ghessar.
  `/oldrepublic planet sandstorm start [ticks]` and `stop` force it (tests and review).
- **Ridge lightning** (`RidgeLightning`): every `ridgeIntervalTicks` a player spends in the basalt ridges above
  `ridgeMinY` under open sky, a real bolt strikes `ridgeDistance` blocks from them; a stormback within
  `ridgeStormbackRange` draws it instead (W8 charges it); never within `ridgeHubExclusion` of the holdfast. It is
  weather: it strikes on Peaceful too.
- **Stormglass**: real lightning (not cosmetic) on exposed basalt grows a cluster half the time
  (`stormglassChance`); the hook is `mixin.planet.LightningStrikeMixin` at `LightningBolt#tick`'s
  `powerLightningRod` call, skipping visual-only bolts.

## 6. Verse stones (3.3.2)

- Each player has three, one per Tyrvann biome. A stone is awake until silenced. While its owner is near, its
  Hymnsworn guards rise once (`verseGuards`, 3, 4 and 5; W8's `hymnsworn` group at the `SPAWNER` anchor).
- **The slowing hum**: while a guard lives, every `humSlowInterval` ticks the air thumps outward and players within
  `humSlowRange` (not creative, not spectators) are slowed.
- **The channel**: the owner (or a party member within 32 blocks of the online owner) holds use on the stone within
  6 blocks; a use at least every 10 ticks keeps it. `silenceTicks` (100) of channel silence the stone. Damage,
  leaving the range, a live guard or letting go breaks it. A Push, Pull or Lift on the stone (the `force_reactive`
  tag) cuts `silenceForceCut` (40) ticks, once per channel. On Peaceful a use silences it at once.
- **Silence**: the stone goes dark (`awake=false`), the `verse_stone` effect plays, and `QuestHooks.onVerseSilenced`
  is called for the owner; when the owner is away the hook is owed and paid when they are next online near it.

## 7. The holdfast gate and the Hymnal's doors

- **The gate** (`HoldfastGateBlock`): a player at Neutral clan standing or higher opens the whole gate (every
  connected plate); it stays open while anyone stands in it and shuts 100 ticks after it was last occupied. Below
  Neutral it refuses (overlay at most once a second). From the inside it always opens; creative players always pass.
  Open is block state and the shutting is a scheduled tick, so a restart keeps both.
- **The doors** (`HymnalDoors`): sheets of sealed plating at the halls' `HEART_DOOR` and `UNDERCROFT_DOOR`. The
  Heart's opens from `THE_HYMNAL` on; the undercroft's by the Long Hymn's rule (W8's `LongHymn.mayOpenUndercroft`);
  either for creative players and always from inside. A use lifts the sheet out whole; it reseals 100 ticks later once
  the doorway is empty. Neither the fusion cutter nor a Push opens plating inside the Hymnal.

## 8. Effects, phase by phase (15)

Every effect goes through `client.fx.Vfx` (budgets scale with the preset and `Vfx.count`), and through
`network.EffectEvents` in multiplayer. Nothing draws a ring, sigil, halo, outline or glowing rune: bent air, dust,
grit, motes, sparks and light only.

| Effect | Phase | Look | Sound |
|---|---|---|---|
| `sandstorm` (toPlayer, variant 0 start, 1 end) | rising | the fog's far end pulls in to `sandstormFogEnd` (24) and its start to 0 over 200 ticks, tinted sand by the light; `sand_gust` streaks start upwind of the camera, 60 alive at full strength at Standard | `sandstorm.wind` fades in over 100 ticks |
| | blowing | the streaks flutter along the wind (a slow seeded veer), only under open sky | the wind loop, positional to the camera |
| | sheltered | under a roof the storm's strength eases to 35% over 40 ticks (fog and streaks) | the wind ducks to 45% |
| | passing | the fog eases back over 200 ticks; no new streaks | the wind fades out over 100 ticks; it stops on leaving Ghessar, death, disconnect |
| `verse_stone` 0 hum | awake | 2 `verse_mote` a second drift off the monolith (budgeted) | `verse_stone.hum`, a positional loop within 24 blocks while awake |
| | pulse | one `air_ripple` swelling from 1.2 to 7 blocks around the stone (the air bends outward once), 6 motes thrown off | `verse_stone.pulse` |
| `verse_stone` 1, 2 channel | start and progress | 2 `verse_mote` a tick stream from the stone to the channeller's hand; for the channeller a dot ring round the crosshair fills with the progress (HUD, not in the world) | `verse_stone.channel` loop, released 20 ticks after the last progress |
| `verse_stone` 4 Force answers | the cut | a brighter pulse of 10 stream motes | `verse_stone.channel` pitched up |
| `verse_stone` 3 broken | | the stream stops and its motes scatter | the loop stops |
| `verse_stone` 5 silenced | aftermath | the faint glow in the fissures drains (the block's silent model); 30 motes fall as grit over 20 ticks, half motes, half falling dust of the stone | `verse_stone.silence`; the hum stops |
| `stormglass` 0 | strike | 6 `lightning_spark` crawl over the struck basalt's top face over 10 ticks (crystal motes instead with flashing off) | `stormglass.form` |
| | growth | 4 block-break particles of the cluster pushing up | (the same sound's chime) |
| `site_door` 0, 1 the gate | shut, open | the plates slide; 16 block dust particles of the gate fall over 20 ticks along it | `holdfast.gate` (pitched down when shutting) |
| `site_door` 2, 3 the doors | open, seal | stone grinds apart; 16 falling dust of the plating over 20 ticks across the doorway | `hymnal.door` (pitched down when sealing) |
| storm flash (client) | each bolt | with flashing or storm flashes off, the sky flash is replaced by a low, steady brightening | thunder stays vanilla |
| mire fog (client) | in `tyrvann_mire` | the fog closes to 4 to 48 blocks, a cold green-grey, eased over 60 ticks | the mire bed |
| block ambience | idle | sunstone ore: a faint heat shimmer (`air_ripple`) above it by day; stormglass: a spark now and then | none |

The beds (3.6) are the M4 emitter's: one streamed loop per biome, hub and the Hymnal, cross-faded, ducked under cover
and to 30% while W4's verse vision plays. Shake: none of these shake the camera.

## 9. Sounds: the final sound of each event

All 19 are placeholders made by `tools/assets/sounds/world_m5.py`; the briefs for the final versions are in
`docs/assets/planets_m5.md`. In short: the hum is the stone singing to itself on an open fifth; the pulse the air
thumping outward with the chord through it; the channel a rising, beating resonance drawn toward the player; the
silence the chord sinking and closing with grit settling; the wind a heavy, whistling sand-laden gale; the stormglass a
static snap and glass pushing up; the gate heavy plates on runners; the door a stone slab grinding aside. Music:
Ghessar bright, open and hot; Tyrvann dark, old and storm-bound.

## 10. Balance (`planets` section, M5 keys)

`sandstorms`, `sandstormIntervalDays` {3, 5}, `sandstormTicks` {3000, 6000}, `sandstormFogEnd` 24,
`scourSandstormMultiplier` 2 (W8 reads it), `ridgeLightning`, `ridgeIntervalTicks` {600, 1200}, `ridgeMinY` 110,
`ridgeDistance` {4, 10}, `ridgeStormbackRange` 24, `ridgeHubExclusion` 48 (Milestone 6: 72), `freighterEdgeChance`
0.03 and `freighterEdgePity` 30 (W7 reads them), `verseGuards` {3, 4, 5}, `hymnalArchInterval` {400, 800},
`stormglassChance` 0.5, `silenceTicks` 100, `silenceForceCut` 40, `humSlowRange` 8, `humSlowInterval` 60,
`hymnalDepth` 18, plus the M5 ring lists (`verseRings`, `hymnalRings`, `debtorCampRings`, `detentionRings`).

## 11. Tests

`PlanetM5GameTests` (19.1): `ghessarHoldsRainAtZero`, `tyrvannStormAlwaysOn`, `sandstormCycleSeeded`,
`sandstormStateCodecRoundTrip`, `ridgeLightningDistanceSeekingAndHubExclusion`,
`stormglassFormsWhereLightningStrikesBasalt`, `verseStoneChannelAndInterrupt`, `verseStoneForceCutOnce`,
`verseStoneOwnerAndPartyOnly`, `verseHumSlowsWhileGuarded`, `silencedStoneCallsHook`, `hymnalPlacedOncePerServer`,
`hymnalDepthAndDoorRules`, `holdfastGateOpensPerPlayerFromNeutral`, `peatBurnsAsFuel`,
`everyM5TemplateHasSidecarAnchorsInsideSize`, `newStructuresHaveStructureTags`. The M4 `PlanetGameTests` biome tests
now cover all four planets.

`m5/PlanetClientSteps` (19.2): the hubs (recorded, anchors in their boxes, the gate's plates), the six biomes at noon,
a forced sandstorm seen by the client, the rain check of 3.5 (0 on Ghessar while the overworld rains and 20 ticks after
it clears; 1 on Tyrvann), a ridge strike, a command-placed verse stone awake and silenced, and the Hymnal placed by its
own path (arch and halls). Screenshots 125 to 138; 139 (the twin sun) is cut.

## 12. API status

[V]: the fog environment's `setupFog` signature and `FogData` fields (follow-up digest), `ClientLevel#setSkyFlashTime`,
`LightningBolt#tick`'s `powerLightningRod` call and its `visualOnly` field (dump), HUD element registration (Fabric
source), `ClientboundGameEventPacket` rain and thunder events, `ServerLevel#advanceWeatherCycle`. Probed after the
merge (`tools/api-probe/m5_w3.txt`): `FuelValues#burnDuration` in the peat test, and `RandomizableContainer`'s
unpack passing the opening player as `THIS_ENTITY`. [U]: the target class name `AtmosphericFogEnvironment` (a string
target) and `findClosestBiome3d`'s 26.2 signature (used as in M4), both exercised by the client steps.
