# World feature design: Crystal Caves and the Trial of Reflection

Owner: world worker. Covers Milestone 1 journey steps 5 to 8 (the Calling's server-side search, the Cave,
the Trial of Reflection, the Crystal), the world blocks, the `crystal_cave` structure, the `shadow_echo`
entity and the client cave ambience.

Status legend for APIs: **[V]** verified against the 26.2 probe logs or Fabric 26.2 source,
**[U]** unverified (probe requested in `tools/api-probe/world.txt`), **[K]** known from older versions,
assumed unchanged until the probe confirms.

## 0. Implementation status (Phase 2)

Implemented as designed, with these differences from the plan below (the sections were updated where noted):

- Clusters extend an own `CrystalFormationBlock` (`facing` only, no waterlogging) instead of `AmethystClusterBlock`,
  whose `codec()` return type cannot be narrowed by a subclass.
- No render-layer registration exists or is needed in 26.2 (chunk layer chosen from texture transparency).
- Structure `size` 3 and an empty `spawn_overrides.monster` list (3.2); descent 9x30x32 and heart 45x22x48 (3.3).
- Meditation stone is unbreakable in survival (strength -1) so a cave's trial can never be lost.
- Trial requirements: not completed (`world.trial_complete`), and outside creative: awakened, `force_push`, quest
  stage at least CALLING (entering the cave advances to TRIAL; CALLING is accepted so the order of hooks never blocks).
- Chamber bounds 9.5 blocks horizontally, 6 vertically from the stone (the doorway seal is at about 9 blocks).
- The echo is `noSave`, overrides `checkDespawn` (stays on Peaceful; the trial owns its lifetime), only takes damage
  whose source entity is its owner (or damage that bypasses invulnerability), and discards itself within 10 ticks
  when no session claims it. Its push aim freezes 4 ticks before release so a late side-step dodges.
- Crystal flags: `world.trial_complete`, `world.crystal_pending`, `world.crystal_granted` (one crystal per player,
  ever; forced re-runs give none), `world.crystal_awaiting_pickup`, plus `world.crystal_dim=<dimension>` and counters
  `world.crystal_x/y/z`. `/oldrepublic trial reset` clears all `world.*` flags and counters.
- `caveSearchRadius` is 6 placement cells (each 28 chunks) after reading `ChunkGenerator#findNearestMapStructure`;
  the heart is refined within 192 blocks via the start chunk's `getAllStarts()` (no registry lookup needed).
- Readability (CI screenshot review): the echo always uses the violet shadow skin with a baked rim light (a multiply
  tint left the owner's colourful skin reading as a normal player), drawn full-bright, lightly dimmed at rest and lifted
  to full brightness during tells, with eye glints; `shadow_wisp` is full-bright deep violet; the forming column adds rising motes.
- The echo renderer uses its own `EchoRenderState extends HumanoidRenderState` and a `HumanoidModel` baked from
  `ModelLayers.PLAYER`, never `AvatarRenderState`: the dispatcher sends an avatar state to the vanilla player renderer
  picked by its skin (default Steve), which bypassed our texture, tint and render type [API-CHECK].
- Cave visits (`CaveTracker`) count any piece including the camp; the ambience bed only plays in the descent and heart.

## 1. Packages and classes

Common (`src/main/java/com/oldrepublic/world/`):

| Class | Role |
|---|---|
| `WorldFeature` | init: registers blocks, block entities, particles, entity, damage-type key, events, commands. |
| `WorldBlocks` | Registers the five blocks through `ModRegistry.block` (with items) and the block entity type. |
| `WorldParticles` | `crystal_mote`, `crystal_glint`, `crystal_spark`, `shadow_wisp` via `FabricParticleTypes.simple()` [V: Fabric testmod]. |
| `WorldEntities` | `shadow_echo` entity type (`EntityType.Builder` / `FabricEntityType.Builder.createMob` [V]) + attributes (`FabricDefaultAttributeRegistry` [U]). |
| `WorldBalance` | Balance section `world` (all numbers below). |
| `WorldEffects` | Effect id constants + server send helpers (wrapping `EffectEvents`). |
| `CaveLocator` | (existing stub, signatures kept) nearest cave heart + `isInsideCave`. |
| `CaveTracker` | Per-player cave visit state; calls `QuestHooks.onCaveEntered` once per visit; sends the ambience flag. |
| `block/CrystalFormationBlock` | Shared base of the two clusters: `facing` only (no waterlogging), amethyst-like shapes, support check. |
| `block/KyberClusterBlock` | Decorative cluster. Light 6. |
| `block/CrystalVeinedStoneBlock` | Veined stone with display-tick glints. |
| `block/UnstableCrystalClusterBlock` + `block/UnstableClusterBlockEntity` | Hazard (cluster + `EntityBlock`, server ticker). |
| `block/ResonantSealBlock` | Seal wall with `OPEN` boolean property, scheduled close. |
| `block/MeditationStoneBlock` | Altar; interact starts the trial. |
| `block/WorldBlockAmbience` | Common-side hook interface; the client installs the implementation (keeps `Vfx` out of common code). |
| `trial/TrialManager` | Server-side trial sessions (start, tick, fail, complete, crystal grant). |
| `trial/TrialSession` | One running trial (owner UUID, level key, stone pos, echo UUID, phase, timers). |
| `trial/ShadowEchoEntity` | The echo (extends `Monster`). |
| `trial/EchoLungeGoal`, `trial/EchoPushGoal` | Telegraphed attacks. |
| `WorldDamage` | `ResourceKey<DamageType>` of `crystal_discharge`. |
| `trial/CrystalGrant` | Builds the attuned `kyber_crystal` stack and the owner-only item entity; watches for pickup. |
| `WorldCommands` | `/oldrepublic locatecave`, `/oldrepublic trial start|stop`. |

Client (`src/client/java/com/oldrepublic/client/world/`):

| Class | Role |
|---|---|
| `WorldClient` | init: particle providers, entity renderer, effect handlers, ambience listener and ticker, disconnect reset. No render-layer registration (26.2 picks the chunk layer from texture alpha). |
| `WorldParticleProviders` | Providers and particle classes for the four particle types. |
| `WorldAmbience` | Implements `WorldBlockAmbience.Listener`: display-tick motes/glints/sparks through `Vfx`, cluster memory for the resonance loop, cave bed, chimes, trial atmosphere, echo wisps, timed tells. |
| `FadingLoop` | `AbstractTickableSoundInstance` used for the `AMBIENT_CAVE` bed (non-positional) and the `CRYSTAL_RESONANCE` loop (positional, linear attenuation); fades, ducks, stops on release or level change. |
| `WorldEffectHandlers` | Effect events: unstable charge/discharge, seal open/refuse, trial begin/echo form/lunge/push/dissolve/complete, crystal form, cave ambience flag. |
| `ShadowEchoRenderer` | Humanoid renderer with the owner's skin darkened via `getModelTint` [V exists], fallback original texture. |

Mixins: none planned (`oldrepublic-world.mixins.json` stays empty). Pickup is detected by polling (section 6).

## 2. Blocks

| Id | Class | Properties | Behaviour |
|---|---|---|---|
| `kyber_cluster` | `KyberClusterBlock extends AmethystClusterBlock` | `facing`, `waterlogged` (inherited), light 6, `SoundType.AMETHYST_CLUSTER`, strength 1.5, no occlusion, `PushReaction.DESTROY` | Decorative. Drops itself only with Silk Touch (loot table modelled on vanilla `amethyst_cluster`), else 1 to 2 `crystal_veined_stone`? No: drops nothing without silk touch so caves are not farmed for a quest item. animateTick calls the client ambience hook. Not the quest crystal. |
| `crystal_veined_stone` | `Block` | stone-like, strength 1.8, requires tool (pickaxe tag), light 2 | Wall material; occasional glint in animateTick (marks the hidden chamber's breakable wall). Drops itself. |
| `unstable_crystal_cluster` | `UnstableCrystalClusterBlock extends AmethystClusterBlock implements EntityBlock` | `facing`, `waterlogged`, `charging` boolean; light 4, 11 when charging | Hazard (section 5). Drops nothing (unstable crystal shatters); breaking it does not discharge. |
| `resonant_seal` | `ResonantSealBlock` | `open` boolean; unbreakable (strength -1, 3600000), no loot, `PushReaction.BLOCK`; translucent; light 3 | Closed: full collision, shimmering wall. Open: empty collision and empty model (invisible, faint motes). Interact rules in section 4. |
| `meditation_stone` | `MeditationStoneBlock` | unbreakable in survival (strength -1) inside caves? No: strength 50 like obsidian, so creative builders can place/remove; shape 16x10x16 altar | Interact starts the Trial (section 4). animateTick glints when the player's crystal waits on it. |

Block entity: `oldrepublic:unstable_crystal_cluster` via `FabricBlockEntityTypeBuilder.create(factory, block).build()` [V: Fabric
testmod `BlockEntityTypeBuilderTest`] registered in `BuiltInRegistries.BLOCK_ENTITY_TYPE`. Ticker via
`EntityBlock#getTicker(Level, BlockState, BlockEntityType)` [U signature] returning a server-only ticker
(`level.isClientSide()` → null) using `BaseEntityBlock.createTickerHelper`-style type check [V: protected static on
`BaseEntityBlock`; since the cluster extends `AmethystClusterBlock`, the check is reimplemented locally].

Render layers: `kyber_cluster`, `unstable_crystal_cluster` cutout (cross models); `resonant_seal` translucent.
26.2 Fabric API name for this is unverified (`BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.TRANSLUCENT)`) [U].
Alternative if the API moved: models carry `"render_type"`? (not vanilla) - probe decides.

Assets: block models `cross`-based for clusters (vanilla `amethyst_cluster.json` uses `parent: minecraft:block/cross` [V]),
`cube_all` for veined stone and seal, custom altar model (elements) for the meditation stone; blockstates with the six
facing rotations copied from vanilla `amethyst_cluster` blockstate [V], plus `charging` variants (same model, different
texture) and `open` variants (empty model with only a `particle` texture). Item definitions `items/<id>.json`
(`{"model":{"type":"minecraft:model","model":"oldrepublic:block/<id>"}}`; clusters use a generated item model).
Textures generated by `tools/assets/gen_textures_world.py` (16x16), listed in `docs/assets/world.md`.

## 3. Structure `crystal_cave`

### 3.1 Files

```
data/oldrepublic/worldgen/structure/crystal_cave.json
data/oldrepublic/worldgen/structure_set/crystal_caves.json
data/oldrepublic/worldgen/template_pool/crystal_cave/camp.json
data/oldrepublic/worldgen/template_pool/crystal_cave/descent.json
data/oldrepublic/worldgen/template_pool/crystal_cave/heart.json
data/oldrepublic/worldgen/processor_list/crystal_cave_weathering.json
data/oldrepublic/structure/crystal_cave/camp.nbt
data/oldrepublic/structure/crystal_cave/descent.nbt
data/oldrepublic/structure/crystal_cave/heart.nbt
data/oldrepublic/tags/worldgen/structure/crystal_caves.json          (values: ["oldrepublic:crystal_cave"])
data/oldrepublic/tags/worldgen/biome/has_structure/crystal_cave.json
data/oldrepublic/loot_table/chests/crystal_cave_hidden.json
data/oldrepublic/damage_type/crystal_discharge.json
```

The NBT folder name is `structure` (singular) since 1.21 [K; resfind probe on `data/minecraft/structure/` confirms].

### 3.2 Structure JSON (fields verified against vanilla dumps: trail_ruins, ancient_city, trial_chambers, pillager_outpost [V])

```json
{
  "type": "minecraft:jigsaw",
  "biomes": "#oldrepublic:has_structure/crystal_cave",
  "max_distance_from_center": 96,
  "project_start_to_heightmap": "WORLD_SURFACE_WG",
  "size": 3,
  "spawn_overrides": { "monster": { "bounding_box": "piece", "spawns": [] } },
  "start_height": { "absolute": -5 },
  "start_pool": "oldrepublic:crystal_cave/camp",
  "step": "underground_decoration",
  "terrain_adaptation": "none",
  "liquid_settings": "ignore_waterlogging",
  "use_expansion_hack": false
}
```

- `max_distance_from_center`: int form seen in vanilla [V]. Whether the int also caps vertical distance and the
  object form `{horizontal, vertical}` exists is [U] (probe `JigsawStructure$MaxDistance`). The heart lies ~40 blocks
  below the start; 96 covers it either way if the int applies to both axes.
- `terrain_adaptation: "none"` [U enum name; probe `TerrainAdjustment`]. Rationale: beard/bury/encapsulate would
  reshape terrain around the deep underground boxes; instead every template writes its own air and rock shell.
  The camp sits on a 5-layer foundation to hide uneven ground (known issue: slight overhangs on steep slopes).
- `step: underground_decoration` (as ancient_city [V]) so ores do not punch through our rooms.
- `spawn_overrides`: an **empty** monster list per piece. A poacher entry would let poachers respawn endlessly in the
  dark cave and inside the trial chamber during the trial; the empty list also stops zombies and skeletons spawning in
  the dark pieces. Poachers come only from the three camp template entities.

Structure set (format [V] from trail_ruins / ancient_cities):

```json
{ "placement": { "type": "minecraft:random_spread", "salt": 1406822291, "separation": 10, "spacing": 28 },
  "structures": [ { "structure": "oldrepublic:crystal_cave", "weight": 1 } ] }
```

28-chunk grid (448 blocks) with broad biomes: one cave typically 150 to 350 blocks away.

Biome tag (`#oldrepublic:has_structure/crystal_cave`): `#minecraft:is_forest`, `#minecraft:is_taiga`,
`#minecraft:is_savanna`, `#minecraft:is_badlands`, `#minecraft:is_hill`, plus `minecraft:plains`,
`minecraft:sunflower_plains`, `minecraft:meadow`, `minecraft:desert`, `minecraft:snowy_plains`,
`minecraft:stony_peaks`? No peaks (surface too steep). Nested-tag syntax `"#minecraft:is_forest"` in `values` [U; probe
has_structure/mineshaft]. No oceans, rivers, beaches, swamps.

Template pools (format [V] from trail_ruins/tower.json; `fallback: "minecraft:empty"`):

```json
{ "elements": [ { "element": { "element_type": "minecraft:single_pool_element",
      "location": "oldrepublic:crystal_cave/heart", "processors": "oldrepublic:crystal_cave_weathering",
      "projection": "rigid" }, "weight": 1 } ],
  "fallback": "minecraft:empty" }
```

Camp and descent use `"processors": {"processors": []}` (inline empty list form [V] from pillager base_plates)
or `minecraft:empty` processor list [U existence].

Processor list `crystal_cave_weathering` (rule processor; format [V] for `rule`, `rules`, `input_predicate`,
`location_predicate`, `output_state` from trail_ruins processor; `random_block_match` field names [U] via
`mossify_10_percent`): 12% of `minecraft:stone` → `minecraft:tuff`, 6% → `minecraft:andesite`,
4% of `minecraft:stone` → `oldrepublic:crystal_veined_stone`.

### 3.3 Pieces, connectors and dimensions

Three rigid pieces in a linear chain, so the critical rooms cannot be dropped by jigsaw overlap failures:
the main cavern, the unstable passage, the hidden chamber and the trial chamber are all in one piece (`heart`).
Variety comes from the random rotation of each piece, the weathering processor and Python-seeded noise in the
generator (two variants of descent and heart can be added later by changing the generator seed).

Coordinates are template-local (x east, y up, z south).

**camp** (`25 x 16 x 25`): crystal-poacher dig camp.
- y0..y4: foundation (stone core, dirt, coarse dirt top at y4 with gravel and path patches).
- y5..y15: air inside a 21x21 footprint (clears hills locally), camp props:
  two A-frame tents (wool + oak fence poles, `brown_wool`/`light_gray_wool`), crate stacks (barrels, `oak_planks`,
  `spruce_slab`), a cold campfire (`campfire[lit=false]`), a tool rack (`iron_bars`, `chain`, `lantern`),
  a dig site: rubble mounds (`cobblestone`, `gravel`), a broken `scaffolding` frame, a `chipped_anvil`,
  a sluice of `cauldron` and `hopper`, a pile of discarded `amethyst_shard`-looking `kyber_cluster` offcuts (2 blocks).
- Cave mouth: centre 5x5 pit with a stone ramp (stairs) spiralling down from y4 to y0; opening at the pit floor.
- Jigsaw at (12, 0, 12), `orientation: down_south`, name `oldrepublic:camp_bottom`, target
  `oldrepublic:descent_top`, pool `oldrepublic:crystal_cave/descent`, joint `rollable`, final_state `minecraft:air`.
- Entities: 3 `oldrepublic:crystal_poacher` (nbt `{id, PersistenceRequired:1b}`), one at the tents, one at the
  dig site, one by the mouth.
- The template's horizontal centre is the cave mouth, so `project_start_to_heightmap` samples ground at the mouth.

**descent** (`9 x 30 x 32`): sloped tunnel.
- Top jigsaw at (4, 29, 4), `orientation: up_north`, name `oldrepublic:descent_top`, target `minecraft:empty`,
  pool `minecraft:empty`, joint `rollable`, final_state `minecraft:air`.
- Vertical shaft 3x3 from y23 to y18 (ladder on one wall, timber shoring `stripped_spruce_log` frames every 4 blocks),
  then a 3-wide, 4-high tunnel sloping 1 down per 1 forward to z=25, y=1 (stone stairs underfoot, rail fragments,
  a dropped lantern, a cart of ore). 2-block rock shell around all air.
- Bottom jigsaw at (4, 1, 31), `orientation: south_up`, name `oldrepublic:descent_bottom`, target
  `oldrepublic:heart_entrance`, pool `oldrepublic:crystal_cave/heart`, joint `aligned`, final_state `minecraft:stone`... no:
  `minecraft:air` (it sits in the tunnel opening).

**heart** (`45 x 22 x 48`): main cavern, side passages, hidden chamber, trial chamber.
- Entrance jigsaw at (22, 1, 0), `orientation: north_up`, name `oldrepublic:heart_entrance`, target
  `minecraft:empty`, pool `minecraft:empty`, joint `aligned`, final_state `minecraft:air`.
- Entrance tunnel z0..z8 (3 wide) opening into the **main cavern**: an irregular ellipsoid about 27 x 12 x 20
  centred near (22, 6, 18), floor with rubble and stalagmites (`pointed_dripstone`), crystal formations:
  `kyber_cluster` (all six facings, 40 to 60 placements, denser in 3 "large formations" of 8+ clusters for the
  resonance loop), patches of `crystal_veined_stone`, `calcite` and `tuff` (geode-like banding),
  `smooth_basalt` seams. The broken cutting rig: a gantry of `iron_bars` and `chain` holding a `chipped_anvil`
  over a half-cut formation, a `grindstone`, scattered `scaffolding`, a toppled `lantern`, a `barrel` of
  cut offcuts. An ancient carving wall: `chiseled_stone_bricks`, `chiseled_tuff`, `chiseled_tuff_bricks`
  in a vertical pattern, weathered `cracked_stone_bricks`.
- **Unstable passage** (east): 3-wide, 3-high passage from the cavern's east side, 12 long, with one
  `unstable_crystal_cluster` in a wall niche at mid-length (facing into the passage, centred so the 2.5-block
  radius covers the walkway) and a second one on the ceiling near the end. It dead-ends in a 3x3 wall of
  `crystal_veined_stone` (breakable, glints) behind which the **hidden chamber** (7 x 5 x 7) lies:
  a skeleton-free poacher stash (a `chest` facing the entrance with nbt
  `{id:"minecraft:chest", LootTable:"oldrepublic:chests/crystal_cave_hidden"}` [U key name], a bedroll of
  `brown_carpet`, a `lantern`, an old `kyber_cluster` grown through a crate).
- **Trial chamber** (south): a 15 x 9 x 15 rounded chamber at z31..z45, entered through a 3-wide, 3-high doorway
  filled by 9 `resonant_seal[open=false]` blocks at z30. Walls of `polished_deepslate`, `deepslate_tiles`,
  `chiseled_deepslate` pillars, `chiseled_stone_bricks` relief bands; floor of `polished_tuff` rings;
  12 `kyber_cluster` around the rim; `meditation_stone` at the centre (22, 1, 38). No other light source than
  clusters (dim, dramatic). Chamber radius for the trial (section 4): 8 blocks horizontally, 5 vertically from the stone.
- Rock shell: every air cell in the piece gets solid rock within 2 blocks unless another air/decor cell occupies it;
  cells outside that shell are omitted (structure void), preserving natural terrain.

Generator: `tools/assets/gen_structures.py` (numpy + gzip, deterministic seed) builds each piece as a 3D array of
palette indices, then writes the NBT.

### 3.4 Structure NBT format (gzipped, big-endian Java NBT)

Root compound (unnamed `""`), keys (constants `SIZE_TAG`, `PALETTE_TAG`, `PALETTE_LIST_TAG`, `BLOCKS_TAG`,
`BLOCK_TAG_POS`, `BLOCK_TAG_STATE`, `BLOCK_TAG_NBT`, `ENTITIES_TAG`, `ENTITY_TAG_POS`, `ENTITY_TAG_BLOCKPOS`,
`ENTITY_TAG_NBT` exist on `StructureTemplate` [V]; their string values [K], confirmed by `body:` probes of save/load):

```
DataVersion: Int 4903                      (26.2 world_version [V from version.json])
size: List<Int>[3]                         (x, y, z)
palette: List<Compound>{ Name: String, Properties?: Compound<String,String> }
blocks: List<Compound>{ pos: List<Int>[3], state: Int (palette index), nbt?: Compound }
entities: List<Compound>{ pos: List<Double>[3], blockPos: List<Int>[3], nbt: Compound (with "id") }
```

Jigsaw block: `Name: minecraft:jigsaw`, `Properties: {orientation: "down_south"}` (FrontAndTop names, [K]);
block nbt `{id: "minecraft:jigsaw", name, target, pool, joint ("rollable"|"aligned"), final_state,
placement_priority: Int 0, selection_priority: Int 0}` [U; probe `JigsawBlockEntity#saveAdditional/loadAdditional`].
Blocks absent from `blocks` are structure void (left untouched).

Whether template entities run `finalizeSpawn` [U; probe `StructureTemplate#placeEntities` and
`SinglePoolElement#getSettings`]; the enemy worker was asked to make poachers armed regardless.

### 3.5 Hidden chest loot (`loot_table/chests/crystal_cave_hidden.json`, format [V] from pillager loot table dump)

Pool 1 (guaranteed, rolls 1): one of `oldrepublic:pommel_balanced` (weight 3), `oldrepublic:grip_wrapped` (2),
`oldrepublic:emitter_guardian` (1). Pool 2 (rolls 2..4): iron nuggets (credits placeholder, 3..9), `amethyst_shard`
(1..3), bread, torches, `oldrepublic:crystal_veined_stone` (1..2). Items looked up by id only (saber worker owns them).
`"type": "minecraft:chest"`.

### 3.6 Damage type `oldrepublic:crystal_discharge`

`{"exhaustion": 0.1, "message_id": "oldrepublic.crystal_discharge", "scaling": "never"}` (format [U]; probe
`damage_type/magic.json`), plus tag membership `#minecraft:no_knockback`? No: discharge knocks back slightly, so no tag.
Server: `level.damageSources().source(ResourceKey<DamageType>)` [V]. Lang `death.attack.oldrepublic.crystal_discharge`.

## 4. Seal and Trial of Reflection

### 4.1 Resonant seal

- `useWithoutItem` [V signature from Fabric testmod: `(BlockState, Level, BlockPos, Player, BlockHitResult)` →
  `InteractionResult`]. Server side:
  - Eligible = `ForceData.isAwakened()` [V] and `hasAbility("force_push")` [V; ability id `force_push` per the force
    worker's contract] (creative players always eligible).
  - Eligible: flood-fill connected seal blocks (max 27), set `open=true` with `UPDATE_ALL`, schedule a tick on each
    for `sealOpenTicks` (default 200) via `level.scheduleTick(pos, block, delay)` [U name]; effect event
    `oldrepublic:seal_open` (dust falling, deep resonant thrum using `CRYSTAL_RESONANCE` one-shot pitched down, glint
    sweep).
  - Not eligible: overlay message (`sendOverlayMessage` [V]) "The stone hums, but does not answer." with a second
    line hint (`not awakened` → "...you feel nothing", no push → "It waits for a will that can move it."),
    effect event `oldrepublic:seal_refuse` (`FORCE_FAIL` dull fizz + a few motes bounce off). Per-player cooldown 1 s.
  - `tick` (scheduled): if an entity box intersects the doorway, reschedule 20 ticks; else close (`open=false`).
- Collision: `getCollisionShape` returns `Shapes.empty()` when open, full block when closed [U override name/args].
  Note: open is global for the moment it is open (vanilla collision is not per player); others can follow through.

### 4.2 Starting the trial (meditation stone `useWithoutItem`)

Requirements (server): survival or creative player, not already in a trial; `isAwakened()`; `hasAbility("force_push")`;
`questStage >= TRIAL` (by ordinal [V]) and flag `world.trial_complete` absent [V `ForceData.flags`];
no other trial running at this stone. Refusal → overlay text + `seal_refuse` effect.

Session (`TrialSession`, in-memory, keyed by player UUID, also indexed by stone pos):
- phases: `FORMING` (40 ticks: chamber dims, echo materialises) → `FIGHT` → `RESOLVED`.
- On start: effect `oldrepublic:trial_begin` to players around the stone (client: fades ambience motes in the chamber,
  stops nearby resonance loop for the duration, plays `TRIAL_BEGIN`; applies a brief vignette/darkening by reducing
  mote particles and spawning drifting `shadow_wisp` near the walls - no screen effect needed);
  `QuestHooks.onTrialStarted(player)` [V].
- After FORMING: spawn `ShadowEchoEntity` 4 blocks in front of the stone opposite the player, set owner UUID, persistent
  flag off; effect `oldrepublic:trial_echo_form` (wisp column gathering, `TRIAL_ECHO_FORM`).
- Each tick (server tick event, `ServerTickEvents.END_SERVER_TICK` [V]):
  - owner offline (disconnect event), dead, in another level, or farther than `chamberRadius` (8 h / 5 v) from the
    stone → **fail**; timeout `trialMaxTicks` (6000) → fail.
  - echo missing (killed by void, despawned) while not victorious → fail.
- **Fail**: discard echo (`discard()` [K]) with a wisp-dissolve event, `QuestHooks.onTrialFailed`, overlay
  "The echo fades. The stone waits." Quest stays at TRIAL so the player can retry. Nothing is lost.
- **Victory** (echo dies, `die(DamageSource)` override → `TrialManager.onEchoDefeated`): effect
  `oldrepublic:trial_echo_dissolve` at the echo; flags set and committed immediately (`world.trial_complete`,
  `world.crystal_pending`); `QuestHooks.onTrialCompleted`; after 30 ticks the crystal forms (effect
  `oldrepublic:trial_complete` at completion and `oldrepublic:crystal_form` at the stone when the item appears).
  If the session is torn down during those 30 ticks (logout, server stop path), the crystal is spawned immediately.

### 4.3 The crystal

- Stack: `BuiltInRegistries.ITEM.getValue(OldRepublicMod.id("kyber_crystal"))` [U method name on `DefaultedRegistry`];
  if it resolves to air, log an error and abort the grant but keep `crystal_pending` so a later login retries.
- Component: `stack.set(ModComponents.CRYSTAL_ATTUNEMENT, new CrystalAttunement(uuid, name, color))` [V record].
  Colour: `NATURAL_COLORS[Math.floorMod(uuid.hashCode(), n)]` with palette
  blue `0x3B7BFF`, green `0x3CE06A`, cyan `0x45D6E8`, yellow `0xF2D04A`, violet `0x9A5CF0`, pale white `0xE8F0FF`.
- Item entity: `new ItemEntity(level, x, y, z, stack, 0, 0, 0)` [V] at the stone top centre; `setTarget(ownerUuid)` [V
  exists; semantics "only target may pick up" U, probe `playerTouch`], `setUnlimitedLifetime()` [V],
  `setPickUpDelay(30)` [V], `setNoGravity(true)` [K Entity], `setInvulnerable(true)` [K].
  Saved with the chunk like any item, so relogging never duplicates it; only one is spawned per completion because
  `crystal_pending` is cleared in the same server tick the entity is added.
- Pickup detection: every 20 ticks, for online players with flag `world.crystal_awaiting_pickup`, scan the inventory
  (`Inventory` [V]) for a `kyber_crystal` whose attunement owner equals the player → `QuestHooks.onCrystalObtained`
  once, remove the flag. Robust to any pickup path, no mixin.
- Glint: meditation stone animateTick (client) shows `crystal_glint` when an item entity is resting on it.

### 4.4 Shadow Echo (`oldrepublic:shadow_echo`, `MobCategory.MONSTER`, 0.6 x 1.8)

- Attributes: max health 32, attack damage 4, movement speed 0.31, follow range 24, armor 2,
  knockback resistance 0.6 (push resistance comes from the `#oldrepublic:push_resistant` tag; force worker applies x0.4).
- Synced data [V `SynchedEntityData`/`EntityDataSerializers`]: owner UUID (`OPTIONAL_UUID` or an entity reference
  serializer [U]), `action` byte (0 idle, 1 lunge windup, 2 lunging, 3 push windup).
- Goals: `FloatGoal`, `EchoPushGoal` (priority 1), `EchoLungeGoal` (2), `MeleeAttackGoal(this, 1.15, true)` (3);
  target selector: only its owner (custom target set on spawn, `setTarget` [K]); never targets others.
- Lunge: when 4 to 8 blocks from the owner, cooldown 5 s: 14-tick windup (crouch pose, action=1; effect
  `oldrepublic:trial_echo_lunge` with `TRIAL_ECHO_LUNGE` and wisp trail), then a dash `setDeltaMovement` toward the
  locked direction (0.9 b/t horizontal), hit on contact for 5 damage once.
- Mirrored push: when within 5 blocks with line of sight, cooldown 8 s: 16-tick windup (arm draw, action=3, effect
  `oldrepublic:trial_echo_push` variant 0 = charge: dark-tinted ripples gathering), then release (variant 1): if the
  owner is inside a 50 degree cone within 6 blocks, knockback 1.2 (`LivingEntity#knockback(double,double,double)` [K])
  plus 2 damage, and `hurtMarked = true` so the server sends velocity [U field name]. Never hidden: both tells are
  0.7 s or longer. Client arm pose for the push windup reuses the force worker's render-state key
  `com.oldrepublic.client.force.PushPose.KEY` (lead-confirmed contract).
- Not saved: every 20 ticks the echo checks `TrialManager.isLiveEcho(this)`; if not, `discard()`. So no leftover after a
  crash or restart. `removeWhenFarAway` → false. No loot table, xp reward 0.
- Death: `die` → victory hook; immediate dissolve (removal after 2 ticks instead of the 20-tick tilt) [U `tickDeath`
  override].
- Sounds: hurt/death sounds reuse `TRIAL_ECHO_FORM` pitched? No: silent hurt plus wisp burst; ambient none.

Renderer [U for skin API]: `HumanoidMobRenderer<ShadowEchoEntity, EchoRenderState, PlayerModel>` (wide or slim layer
from `ModelLayers.PLAYER`/`PLAYER_SLIM` [V constants]). `getTextureLocation`: if the owner is a loaded
`AbstractClientPlayer`, its skin texture (`getSkin().body().texturePath()` [U]); otherwise
`oldrepublic:textures/entity/shadow_echo.png` (original generated dark humanoid). Darkening:
`getModelTint` [V exists] returns ARGB `0xD0262230` (dark, slightly translucent if the render type supports it;
otherwise opaque dark). Client tick spawns sparse `shadow_wisp` from the echo body (budgeted by `Vfx`).

## 5. Unstable crystal cluster hazard

Block entity state machine (server ticker; transient, not saved):

| State | Rule |
|---|---|
| IDLE | Every 4 ticks: find non-spectator, non-creative players within `radius` (2.5) of the block centre. Count linger ticks; reset if none. When linger ≥ `lingerTicks` (10) → CHARGING. |
| CHARGING | Set `charging=true` (light 11). Send `oldrepublic:crystal_unstable_charge` (pos, dir = facing, intensity = 1.2 s) to players around. After `chargeTicks` (24) → discharge. |
| DISCHARGE | All `LivingEntity` within radius take `damage` (4) from `crystal_discharge` source; send `oldrepublic:crystal_discharge` (crack + `crystal_spark` burst + flash if allowed + small camera shake within 4 blocks). Set `charging=false`, cooldown `cooldownTicks` (60). |
| COOLDOWN | Ignore players until elapsed → IDLE. |

The tell is never hidden; leaving during the charge still discharges (the player escapes by distance).
Discharge is not triggered by breaking the block.

## 6. Cave detection and locating

`CaveLocator.findNearestCave(player)`:
- Tag key `TagKey.create(Registries.STRUCTURE, id("crystal_caves"))` [V `TagKey`, `Registries`].
- Cache per player: `{levelKey, queryPos, result (heart or start pos or null), gameTime}`. Return cached when same
  level, player moved < 48 blocks since the query and age < 30 s; otherwise enqueue a refresh and return the stale value.
- Refresh queue processed on `ServerTickEvents.END_SERVER_TICK`, at most one search per tick server-wide:
  `serverLevel.findNearestMapStructure(tag, pos, searchRadius, false)` [V signature `(TagKey<Structure>, BlockPos, int,
  boolean)`]. `searchRadius` in placement cells / chunks [U semantics; probe `ChunkGenerator#findNearestMapStructure`
  body]; default 50 chunks via balance.
- Heart refinement: when the found start is within 160 blocks, load the start chunk at `ChunkStatus.STRUCTURE_STARTS`
  [U], get its `StructureStart` (`structureManager().getStartForStructure(SectionPos, Structure, ChunkAccess)` [U]) and
  pick the piece whose element is `oldrepublic:crystal_cave/heart`; the heart pos is that piece's bounding box
  centre offset to the meditation stone (rotation-aware via the piece's rotation [U]); fallback: the box centre.
- Result is the start (camp) until refined; the calling direction is equally valid at long range.

`CaveLocator.isInsideCave(player)`: `serverLevel.structureManager().getStructureWithPieceAt(player.blockPosition(), tag)`
[U exact overload] `.isValid()` [U].

`CaveTracker` (every 10 ticks per player): computes `isInsideCave`; on false→true calls
`QuestHooks.onCaveEntered(player)` [V] once per visit, and sends `oldrepublic:cave_ambience` (variant 1) to that player;
on true→false (with 60-tick hysteresis) sends variant 0. State cleared on disconnect; on join/respawn/dimension change
the first evaluation re-sends the current flag.

## 7. Client ambience

- animateTick of `kyber_cluster` / `unstable_crystal_cluster` / `crystal_veined_stone` delegates to
  `WorldBlockAmbience.INSTANCE` (common interface, no-op by default, client sets it). Client implementation:
  `Vfx.inRange`, then 1-in-N chance (N from preset: Reduced 1/12, Standard 1/6, High 1/4, Cinematic 1/3) to spawn one
  `crystal_mote` drifting upward slowly, rarer `crystal_glint` on a face (Cinematic preset adds extra glints),
  counts through `Vfx.count`. It records cluster positions in a small ring buffer (`ClusterAmbience.recent`, 64 entries,
  timestamp).
- Resonance loop: every 20 client ticks, group recent positions (< 3 s old) by 6-block cells; the densest cell with
  ≥ 6 clusters within 12 blocks of the camera is the "large formation". A `CrystalResonanceSound`
  (`AbstractTickableSoundInstance` [V], looping, `Attenuation.LINEAR` [U enum name], range 16 via sounds.json
  `attenuation_distance`) plays at its centroid; it fades out and `stop()`s when the formation is no longer reported,
  the level changes, or the player disconnects. At most one resonance loop at a time.
- Cave bed: `oldrepublic:cave_ambience` flag on → start `CaveBedSound` (non-positional `relative`, volume fades in over
  40 ticks); flag off, level null, disconnect (`ClientPlayConnectionEvents.DISCONNECT` [V]) or dimension change → fade
  out and stop. Random `CRYSTAL_CHIME` one-shots at a random recent cluster (every 8 to 20 s while inside the cave).
- During a trial (between `trial_begin` and complete/fail events at that stone) motes within 10 blocks of the stone are
  suppressed and the resonance loop ducks to 30%.
- Effect handlers build all hazard/trial visuals listed in `docs/VFX_SFX_SPEC.md` (Crystal caves, Trial of Reflection),
  each budgeted with `Vfx.count`, flashes skipped when `Vfx.flashing()` is false (replaced by a steady glow particle),
  shake via `CameraShake.add(intensity * Vfx.shakeScale())`.

Effect ids (world-owned): `oldrepublic:crystal_unstable_charge`, `crystal_discharge`, `seal_open`, `seal_refuse`,
`trial_begin`, `trial_echo_form`, `trial_echo_lunge`, `trial_echo_push`, `trial_echo_dissolve`, `trial_complete`,
`crystal_form`, `cave_ambience`.

Particle ids (world-owned, simple types): `oldrepublic:crystal_mote`, `crystal_glint`, `crystal_spark`, `shadow_wisp`
(textures 8x8 frames generated, `particles/<id>.json` like vanilla `end_rod.json` [V]). Other features may use them
by id; the saber worker was told about `crystal_glint`.

## 8. Commands

- `/oldrepublic locatecave`: forces a synchronous search for the caller, prints coordinates and distance
  (clickable teleport suggestion via `ClickEvent` [U, optional]).
- `/oldrepublic trial start`: force-starts a trial at the nearest meditation stone within 16 blocks, or at the
  player's position if none (requirements bypassed). `/oldrepublic trial stop`: fails the caller's trial.

## 9. Balance section `world`

See `WorldBalance` (section `world`): `caveSearchRadius 6`, `caveCacheMoveBlocks 48`, `caveCacheSeconds 30`,
`caveHeartRefineBlocks 192`, `unstableRadius 2.5`, `unstableLingerTicks 10`, `unstableChargeTicks 24`,
`unstableDamage 4`, `unstableCooldownTicks 60`, `sealOpenTicks 200`, `chamberRadius 9.5`, `chamberHeight 6`,
`trialFormTicks 40`, `trialMaxTicks 6000`, `crystalFormDelayTicks 30`, `echoHealth 32`, `echoDamage 4`,
`echoSpeed 0.31`, `echoArmor 2`, `echoLungeCooldownTicks 100`, `echoLungeWindupTicks 14`, `echoLungeDamage 5`,
`echoLungeSpeed 0.9`, `echoPushCooldownTicks 160`, `echoPushWindupTicks 16`, `echoPushRange 6`,
`echoPushConeDegrees 50`, `echoPushStrength 1.2`, `echoPushDamage 2`. The quest owns all trial rewards (XP,
Resonance); the world feature grants none.

## 10. Save/load, death, dimension change, relog, multiplayer

- Trial sessions are in memory only; a logout, death, dimension change or leaving the chamber fails the trial cleanly
  and discards the echo; the echo self-discards if no session claims it (restart/crash safety).
- Completion state lives in `ForceData.flags` (persistent attachment owned by the foundation): `world.trial_complete`,
  `world.crystal_pending` (crystal not yet spawned), `world.crystal_awaiting_pickup`. On join, `crystal_pending`
  triggers spawning the crystal at the recorded stone (pos stored in `ForceData.counters` as
  `world.crystal_x/y/z` and a level-key flag) if that chunk is loaded, else on next approach.
- The crystal item entity persists with the chunk; owner-only pickup via `setTarget`.
- Two players can run trials at different stones; one stone hosts one trial at a time.
- All visuals go through effect events to tracking players; the echo is a normal tracked entity.

## 11. Changes needed in files owned by others

- force worker: already lists `oldrepublic:shadow_echo` (optional entry) in `#oldrepublic:push_resistant` (lead confirmed).
- enemy worker: confirmed poachers loaded from `{id, PersistenceRequired:1b}` equip their rifle on the first tick
  without `finalizeSpawn`; SpawnPlacements registered (ON_GROUND, MOTION_BLOCKING_NO_LEAVES, monster rules, darkness),
  so a `spawn_overrides.monster` entry with `bounding_box: piece` is valid (spawns in the dark cave pieces only).
- saber worker: confirmed world owns `crystal_glint` (and the other crystal particles); saber looks it up by id.
- lead: nothing in foundation files so far.

---

# Milestone 3: sites, puzzles, outposts and shrines

Owner: W1. Part A built the puzzle kit (`world.puzzle`), the resources and decor, the effects, the art and the sounds;
section 21 is its text. Part B built the sites, structures, outposts, shrines, cairns, spawners, the locator lookups,
presence and ambience, and the commands. Spec sections 3, 10.6, 10.8, 11.1, 13.3, 15.1, 16 and 17. The status legend of
the M1 part applies; `API-CHECK` marks in the code name every call not found in the 26.2 dumps.

## 12. M3 implementation status

Implemented to the frozen contracts of spec 15.1. These choices differ from the plan or need noting:

- **Placing a worldgen structure.** `/oldrepublic site place <structure>` does not run vanilla `place structure`. It
  runs the same code path, `Structure#generate` for the chunk of the position in any biome and then
  `StructureStart#placeInChunk` per chunk, and then registers the start in its chunk and a reference in every chunk of
  its box, as worldgen does. Vanilla's command leaves both out, and then `getStructureWithPieceAt`, the locator, the
  site tracker and the Calling would never see the placed site. A jigsaw start sits at its chunk's corner on the
  surface.
- **Start height.** The structures use `start_height` 1 − buried with `project_start_to_heightmap` `WORLD_SURFACE_WG`,
  so the feet layer lands on the first free block (API-CHECK: vanilla's projected-start offset).
- **Pools.**
  - The niche pools fall back to `minecraft:empty`, like every pool.
  - The `wreck_field/underground` pool of the plan was dropped, because no piece needed it.
  - The two scars are `terrain_matching` single elements attached straight to the hull's ends.
- **Weathering.** Ruin stone gets 15% cracked, then 0.235 of the rest mossy (20% overall).
- **Wayfarer shrine.** It uses the ruin skin with tuff.
- **`STAIR_MOUTH`.** In the courtyard it is the middle cell of the veiled wall. At the gate it is the feet cell of the
  top step.
- **Chests.** They carry `LootTable` only, no `LootTableSeed`, so vanilla seeds them per chest.
- **Banners.** The outpost banners are written with a `patterns` list (API-CHECK: 26.2 banner block entity NBT).
- **Mast lamp.** It is a sea lantern 10 above and 4 behind the Republic terminal. The client adds a red flare 8 ticks
  in every 40, and none with flashing effects off.
- **Sense dip.** The site ambience uses its own listener for the local player's Sense gesture. `FadingLoop` gained an
  instant `dip(factor)`, and the M1 cave beds take the same dip.
- **Review fixes.**
  - The bounty terminal is W4's `TrooperPost` and also binds through `Troopers.bindTerminal` (section 16.3).
  - Outposts and Echo Shrines are overworld only: `Outposts.ensureNear` does nothing in another dimension,
    `EchoShrines.place(owner, kind)` returns null there, and `outpost ensure` reports the refusal.
  - An Echo Shrine's guard is the owner's encounter (section 15).
  - Sense decides `relic_lore` and `sense_seeker` the way W3's Sense does (`ForcePowers.hasNode`), and a Seeker's
    reveal lasts `veilRevealTicks` + `force_sense.seekerBonusTicks` on the veiled stone too.
  - A cairn that sees its online owner past LISTENING lists them in the expired owners (section 17).
  - `EchoShrines` reads the shrine rings without replacing W2's cached `chapter_three` section (section 17).
- **Puzzle kit (part A, section 21).**
  - A Push or a lit-saber hit opens only the REVEALED stones connected to the hit block. Veiled stones that Sense never
    showed stay closed.
  - A powered `puzzle_seal` opens its whole connected group of seal cells (at most `veilGroupMax`), so a two-high seal
    opens as one door whichever cell the wiring reaches.
  - Each resettable block has a public `idleStep(level, pos, playerNear, b)` seam for the tests.
    `PuzzleRules.resetInterval` stretches the 200-tick step when a configured reset would need more than 6 steps.
  - A latched pedestal `ready` clears on reset only once its power is gone.
  - The three M3 crafting items keep the lead's placeholder art from `gen_textures_m3items.py`.

## 13. The site layer

### 13.1 Anchors and sidecars (`SiteAnchors`)

Anchors are named cells, not blocks (spec 3). Every template's generator writes a sidecar
`data/<ns>/site_anchors/<template path>.json` (`{"ANCHOR": [[x, y, z], ...]}`, template-local, in order), and the real
block sits in the anchor cell (section 14). Sidecars load on server start and on `/reload`.

- `SiteAnchors.of(template, anchor)` gives the local cells.
- `find(piece, anchor)` and `all(piece, anchor)` give world cells. `toWorld` rotates about `BlockPos.ZERO` with no
  mirror, then adds the piece's origin: what both jigsaw pieces and `RuntimeSites` placements do.
- `templates()` lists the templates that have a sidecar.

### 13.2 Site records (`WorldSites`)

The global attachment `oldrepublic:world_sites` is saved with the world, server only and never synced. It holds:

- every runtime placement, as a `SiteRecord` (kind, dimension, origin, rotation, template, owner, placed day);
- the owners whose echo cairns have expired.

All writes go through a detached `Data`: `snapshot`, change, `commit`. The rules and tests use the same `Data` type
and never touch the server's value. The lists are capped at 512 records and 64 expired owners. The oldest record that is not an
outpost goes first, because outposts carry garrisons and count toward the runtime cap. The `CODEC` round-trips exactly (the `worldSitesCodecRoundTrip` test).

### 13.3 The structure locator (`StructureLocator`, `LocatorQueue`)

**Searches.** `find(player, kind, radius, exclude, callback)` queues a search, and the callback runs on the server
thread once the search has run.

- One server-wide `LocatorQueue` checks at most `locatorCellsPerTick` placement cells per tick across all searches.
- Results are cached per player, kind and filter for `locatorCacheTicks`, or until the player moves
  `locatorCacheMove` blocks. `cached(player, kind)` reads that cache without queueing.

**The worldgen lookup.** A search's cells are the regions of the kind's random-spread placement, in rings around the
player's region, capped at `locatorMaxRings`.

- A cell's check reads the region's potential start chunk at `STRUCTURE_STARTS` and keeps the starts of the kind's
  structure tag.
- It also returns the runtime records of the kind whose origin lies in that region, so worldgen and runtime sites
  compete ring by ring.
- Once a ring holds a result, the rest of the ring is checked and the nearest result wins.
- A kind with no placement (runtime kinds) gets one cell holding every record of the kind in the dimension.

**Synchronous reads.** They never queue and never generate a chunk:

- `pieceAt(level, pos)`: the piece of a worldgen start (through the kinds' tags) or of a runtime record that contains
  `pos`;
- `piecesNear(level, pos, radius)`: every piece within `radius`, nearest first, from the loaded chunks' starts and the
  records;
- `sitePieces(anyPiece)`: the whole site;
- `anchor(anyPiece, anchor)`: the first cell of an anchor anywhere in the site.

## 14. Structures and templates

### 14.1 The 21 templates

`tools/assets/gen_structures_m3.py` generates the templates and sidecars (the table is in `docs/assets/world.md`). Every
anchor cell holds its real block:

- `site_spawner` with its group;
- chests with their `chests/*` table;
- the pedestals with kind and readiness;
- the keeper with its `urns` offsets, the urns, the vents and the counterweights;
- the terminal;
- veiled blocks;
- air at feet cells.

The generator refuses to write a template whose puzzle redstone does not work (a simulation of the vanilla rules), or
a structure whose jigsaw assembly is wrong (missing pieces, intersecting boxes, misaligned openings) or whose resource
counts break spec 3.2.

### 14.2 The five structures

| Structure | Start (buried) | Pieces | `size` | `max_distance_from_center` | Biomes |
|---|---|---|---|---|---|
| `jedi_ruin` (Watchers' Rest) | `courtyard` (4) | courtyard → stair → listening hall → pilgrim niche (behind the veiled panel, east) and anchor cache (west) | 5 | 64 | forests, taigas, meadow, cherry grove |
| `sith_tomb` | `gate` (3) | gate → path → dig camp; gate → processional → rubble room (west), pilgrim niche (behind veiled stone, east) → burial chamber | 4 | 80 | desert, badlands, savanna plateau |
| `wreck_field` | `hull` (4) | hull → two scars (one per end), path → salvage camp | 3 | 64 | plains, savanna, snowy plains, windswept hills |
| `republic_outpost` | `outpost/republic_post` (1) | the post alone | 1 | 32 | plains, meadow, forests, taiga, savanna |
| `imperial_outpost` | `outpost/imperial_post` (1) | the post alone | 1 | 32 | desert, badlands, savanna plateau, dark forest, snowy plains, taiga |

**Structure sets.**

- `ruins` spreads the three ruins at spacing 32 and separation 12, kept 6 chunks from the crystal caves.
- `outposts` spreads both outposts at spacing 40 and separation 16, kept 6 chunks from the ruins.

Every structure has an empty monster `spawn_overrides`: people come from the spawners. Jigsaw targets match one element
per connector, so every placement has the same pieces.

### 14.3 The adapted pool element (`AdaptedPoolElement`)

`oldrepublic:adapted_pool_element` is a single pool element that adapts the terrain after placing its piece, with the
numbers in the pool JSON:

- `support_depth`: foundation columns are filled down to solid ground;
- `clear_from` and `clear_inset`: the overhang above the piece is removed;
- `yard_stand` and `ramp_rings`: the outer rings step down toward low ground.

It shares the code of M1's `CampPoolElement` through the five-argument `adaptTerrain` and its `Adaptation` record.
`crystal_cave` keeps its own element, so saved starts stay readable. The surface starts and camps use it; the
underground pieces are plain rigid elements.

### 14.4 Runtime placement (`RuntimeSites`, `PlacementRules`)

A stand is a feet position on the ground. A runtime template is centred on its stand with its layer `buried` at the
stand's height, entrance on its local north side, rotation pivot `BlockPos.ZERO`.

- `placeSurface` searches rings around an origin through M2's `MeetingSites` rules: footprint, clearance, slope,
  forbidden areas (spawn protection) and other players' sites. It turns the entrance toward the origin, adapts the
  terrain, stamps the template and records it.
- `placeAt` is for commands and client tests: no search, no checks except loaded chunks.
- Nothing is ever placed into, and no check reads, an unloaded chunk.

## 15. Site spawners (`site_spawner`, `SpawnerGroups`)

**Block and data.** The block is invisible and has no item. Its block entity holds:

- `group` and `count` (0 means the group's default);
- `bound`;
- `replace_with` (air by default);
- `done`;
- `owner` (optional): set on an Echo Shrine's `GUARD` spawner by `EchoShrines.place`.

**Firing.** Every 20 ticks, once the frozen loading rule holds (the chunk's entities are loaded) and a player is within
`spawnerRange` (32), it:

1. sets `done`;
2. spawns its group through the owning feature's API (one fresh `spawnSquad` or `spawnCrew` per spawner, or
   sentinels);
3. marks the members persistent;
4. turns its cell into `replace_with`.

A spawner found `done` only finishes its replacement, so it never spawns twice.

**Owned spawners.** A spawner with an `owner` is that player's encounter (3.6, 12.3). It ignores everyone else and fires
the first time its owner, online and in the same level, is within `story.encounterSpawnDistance` (48, the M2 rule). Its
group spawns owned by them (`SpawnerGroups.spawn(..., owner)`), so kills by anyone count for the owner. A shrine placed
by `/oldrepublic site place echo_shrine_<kind>` has no owner, and its guard is a shared spawner.

**Groups.**

| Group | What it spawns | Where |
|---|---|---|
| `dig_team` | an Imperial squad: 2 riflemen and a grenadier | the tomb's dig camp |
| `salvage_crew` | a Republic squad: 2 riflemen and a field medic | the wreck's salvage camp |
| `diggers` | 2 or 3 of Vasch's crystal poachers, seeded by position | the ruin courtyard and the processional |
| `scavengers` | crystal poachers, 1 by default | the wreck hull; 2 at the Wayfarer shrine |
| `watcher` | one Watcher sentinel | the Listening Hall and the Jedi shrine |
| `tomb` | one Tomb sentinel | the tomb and the Sith shrine |

## 16. Outposts

### 16.1 Decide and ensure (`Outposts`)

`decide(query, balance)` is the pure rule of 3.5:

1. a worldgen outpost within `outpostWorldgenRadius` (640) gives WORLDGEN;
2. else a runtime one within `outpostReuseRadius` (512) gives REUSE;
3. else, with `runtimeOutposts` off or `maxRuntimeOutposts` (8) reached, CAP_NEAREST;
4. else PLACE.

Distances are horizontal and inclusive.

`ensureNear(player)` locates the nearest worldgen outpost of each army through the queue, builds the query from
`WorldSites`, and acts on the decision. Chapter Three sites are overworld only (6.9, 12.2), so it does nothing for a
player in another dimension, and `outpost ensure` says so. `nearestTerminal(player, army, radius)` reads the records and the locator cache
only.

### 16.2 Runtime outposts (`OutpostPlacement`)

The outpost is placed through `RuntimeSites.placeSurface` with these rules:

- on the rings `outpostRings` (112, 128, 144), with the fallback rings 80 and 96;
- a 23-block footprint of natural ground with a 27-block clearance and a slope of at most 3;
- at least `outpostSeparation` (128) from any other runtime outpost, and outside spawn protection;
- buried 1.

With `story.placeStructures` off, only the terminal is placed and the site is still recorded.

### 16.3 The bounty terminal, garrison and binding

**The terminal.** Using a terminal sends `terminal_use` (the screen glows in the army's colour; nothing flashes) and
opens W2's board for the army.

**Its block entity.** It stores:

- the officer's UUID;
- four garrison UUIDs;
- `refill_at`;
- `failed[5]` (consecutive empty checks per slot).

**The 200-tick check.**

1. It waits for the loading rule.
2. It counts the members in its leash box that are bound to it, discarding stale copies and adopting unlisted members.
3. `GarrisonRules.step` decides the rest:
   - a slot that never had a member fills at once;
   - a filled slot counts as missing only after `terminalChecksBeforeRespawn` (2) empty checks in a row;
   - a missing officer is replaced at once;
   - one missing trooper is replaced per `garrisonRefillTicks` (12000), and only while no player is within
     `garrisonRefillClearRange` (48).

**Binding.** A bound member (`TerminalBinding`) carries the entity tag `oldrepublic.terminal:<x>,<y>,<z>:<slot>`. It is
persistent and is leashed with `setHomeTo(terminal, garrisonLeash)`. Each spawned member is also bound through W4's
`Troopers.bindTerminal(member, terminal, slot, garrisonLeash)` (slot 0 the officer, 1 to 4 the garrison), so the
trooper saves `Terminal` (12.1) and `Troopers.terminal` and `terminalSlot` answer for it. The tag stays: the tests'
stand-ins and `TerminalBinding.onEntityLoad` use it.

**The stale-copy rule.** A member that loads while its terminal is loaded, and finds its slot listing another UUID,
discards itself. A trooper replaced while its chunk was unloaded therefore never returns as a second copy. The terminal
block entity implements W4's `TrooperPost` (`memberAt(slot)`), so W4's troopers run the same check on their own
periodic post check.

## 17. Echo Shrines and cairns

**Echo Shrines.** A shrine is the template `site/echo_shrine_<kind>` (15×14×15, buried 9), centred on its stand with
its entrance toward the owner.

- `EchoShrines.place(owner, kind)` reads W2's `chapter_three.shrineRings` and `shrineFallback` by name, and searches
  with these rules: footprint 13, clearance 17, slope 3, spawn protection, and `story.siteMinSeparation` from other
  players' sites. It records the shrine with its owner, makes the `GUARD` spawner the owner's (section 15), and returns
  the pedestal (null when there is no spot yet, or when the owner is not in the overworld; the caller retries).
- W1 merges before W2, so it cannot name `ChapterThreeBalance`. It asks `BalanceConfig` for the section as `Object`,
  which matches whatever instance is cached, and copies the two lists out through their JSON form. Asking with a class
  of its own would replace W2's cached instance and lose any change made to it.
- `findSpot(owner, kind, rings, fallback, rules)` is the search alone, as a test seam.
- Inside, a counterweight opens the seal and readies the pedestal of the kind, and a guard spawner holds the chamber.

**Echo cairns.**

- `EchoCairns.place` puts veiled cairns or satchels on natural, dry, sturdy ground in loaded chunks, owned and indexed.
- `reveal` is called by W2's Sense provider. It works for the owner only, for `cairnRevealTicks`.
- Using a revealed cairn calls `QuestHooks.onCairnTouched` and removes it. Anyone else is told it is not theirs.
- `expire(owner)` lists the owner in `WorldSites`. Each cairn checks lazily on its own tick and removes itself once its
  owner is listed, or is online and past LISTENING. Nothing ever writes into an unloaded chunk.
- A cairn that removes itself because its online owner is past LISTENING also calls `expire(owner)`, once (when the
  owner is not listed yet), so the owner's other cairns go even while the owner is offline. W2 is asked to call
  `expire` when the chapter leaves LISTENING, which covers an owner none of whose cairns ticked while they were online.

## 18. Presence and ambience

**Server (`SiteTracker`).** Every `presenceCheckTicks` per player, staggered by entity id, the tracker finds the piece
the player stands in through `pieceAt`. While the player stays inside the last piece's box, nothing is looked up.

- When the piece changes, it sends `site_presence` (kind ordinal and template, or none) and calls
  `QuestHooks.onSiteEntered`.
- Spectators and dead players count as in no site.
- In a processional, it kicks the four vents, which worldgen never starts.

**Client (`SiteClient`, `SiteAmbience`).** The scene comes from the piece:

| Scene | Pieces | Bed | One-shot | Particles |
|---|---|---|---|---|
| ruin surface | the courtyard | ruin | chimes | light motes in sun shafts (open sky, cover 2 blocks off, brightness 12 or more) and falling leaves under the canopy |
| ruin underground | the ruin's other pieces, the Jedi and Wayfarer shrines | ruin | chimes | lit dust falling from the ceiling |
| tomb surface | gate, path, dig camp | tomb | whispers | none |
| tomb underground | processional, chamber, niche, rubble room, the Sith shrine | tomb | whispers | tomb dust, always from cracked stone |
| wreck | every wreck piece | wreck | creaks | smoke wisps over scorched plating |
| Republic outpost | the post | outpost | none | the mast lamp's flare |
| Imperial outpost | the post | outpost | none | none |
| Reliquary | its pieces | Reliquary | none | glints |

- **Beds.** They cross-fade over 60 ticks through `FadingLoop`.
- **One-shots.** They play at a random point 4 to 18 blocks away, at the mod sound volume.
- **Particles.** They are capped at 40 live, thinned by the preset, and only spawn near the player.
- **Mast lamp.** It is found by an incremental scan for the Republic terminal (radius 24, a few slices per tick). The
  lamp is a sea lantern 10 above and 4 behind the terminal.
- **Sense dip.** For 20 ticks after the local player's own Sense gesture, the site beds, the one-shots and M1's cave
  beds play 30% quieter, eased in over 3 ticks and out over 6 (spec 10.1).
- **Reset.** Everything stops on a level change and on disconnect.

## 19. Commands (all `/oldrepublic`, permission 2)

**`site place <site> <pos>`.** The site is one of:

- `jedi_ruin`, `sith_tomb`, `wreck_field`, `republic_outpost` or `imperial_outpost`: the structure, placed and
  registered (section 12);
- `echo_shrine_jedi`, `echo_shrine_sith`, `echo_shrine_wayfarer`, `republic_post` or `imperial_post`: W1's template
  through `RuntimeSites.placeAt` (shrines buried 9, outposts 1);
- any site template id, for W2's and W5's templates (`boss/reliquary_surface` buried 9).

The other commands:

- `site locate <player> <kind>`: the nearest site of a kind through the queue, answered when the search has run (radius
  1024).
- `outpost ensure <player>`: `Outposts.ensureNear`, reporting each army's decision and terminal.

## 20. Balance section `sites` (`SiteBalance`)

| Group | Keys (defaults) |
|---|---|
| Puzzles | `veilRevealTicks` 160, `veilGroupMax` 27, `puzzleResetTicks` 1200, `puzzleResetRange` 24, `puzzleTickInterval` 200, `counterweightTicks` 600, `counterweightWarnTicks` 40, `anchorStrainTicks` 10, `anchorOpenTicks` 600, `rubbleTicks` 20 |
| Urns | `urnSenseRange` 12, `urnStepTicks` 20, `urnRetryTicks` 100, `urnWrongDamage` 2, `urnWrongRadius` 3, `sentinelWakeRadius` 16 |
| Vents | `ventMinTicks` 60, `ventMaxTicks` 100, `ventTellTicks` 15, `ventActiveRange` 8, `ventDamage` 4, `ventSlownessTicks` 60 |
| Cairns and spawners | `cairnRevealTicks` 600, `spawnerRange` 32 |
| Outposts | `outpostWorldgenRadius` 640, `outpostReuseRadius` 512, `outpostRings` 112/128/144, `outpostFallback` 80/96, `outpostSeparation` 128, `maxRuntimeOutposts` 8, `runtimeOutposts` true |
| Garrison | `garrisonRefillTicks` 12000, `garrisonRefillClearRange` 48, `garrisonLeash` 12, `terminalChecksBeforeRespawn` 2 |
| Locator and presence | `locatorCacheTicks` 600, `locatorCacheMove` 48, `locatorCellsPerTick` 4, `locatorMaxRings` 4, `presenceCheckTicks` 20 |

The shrine rings live in W2's `chapter_three` section (`shrineRings` 96/128, `shrineFallback` 48/80).

## 21. The puzzle kit (`world.puzzle`, part A)

Spec 3.3, 10.6, 13.3 and 16.

### Shape of the code

- `PuzzleFeature.init()` (called by `WorldFeature`): loads `PuzzleBlocks` (25 blocks, 2 block entity types) and
  `PuzzlePois` (6 POI types), registers the Force handlers (`PuzzleHandlers`) and the Sense provider
  (`PuzzleSense`).
- `PuzzleStates` holds the frozen properties and enums: `skin` ruin/tomb/wreck, `veil` veiled/revealed/open, `kind`
  jedi/sith/wayfarer, `phase` idle/tell/puff, `age` 0–6, `strain` 0–2, `index` 0–3, the booleans `sunk`,
  `extended`, `solved`, `cleared`, `open`, `ready`, `always_ready`, and `facing` (horizontal). The enums are saved
  by name, so they are append-only.
- `PuzzleRules` holds the timers as pure steps with no world access: reset by idle age, the counterweight, anchor
  and vent timelines, the rubble channel and the urn order. The tests drive them with `new SiteBalance()`.
- `PuzzleWorld` (package-private) holds the shared world helpers: players near, cell occupancy, connected groups
  (6-neighbour flood fill capped at `veilGroupMax`) and the idle reset tick.
- `PuzzleEffects` holds the effect ids and variants. `PuzzleEvents.solved` sends `puzzle_solved` to every listed
  non-spectator player within `puzzleResetRange`. It also calls `QuestHooks.onPuzzleSolved` with
  `StructureLocator.pieceAt`. That call is skipped when the block is in no site, but the chord still plays.
- `PuzzleDisplay` is the common hook for client display ticks (the same pattern as `WorldBlockAmbience`). The client
  installs `PuzzleDisplayClient`.
- Client: `PuzzleClient.init()` (called by `WorldClient`) sets up `PuzzleEffectHandlers` (12 looks and sounds) and the
  display listener. The M1 `seal_open` handler sends variant `SEAL_PUZZLE` (1) to `PuzzleEffectHandlers.puzzleSealOpen`
  and keeps variant 0 exactly as before.

### Blocks

Every puzzle block is unbreakable (strength -1, explosion resistance 3,600,000). The decor and resource blocks break
normally and drop through their loot tables.

| Block | States | Driven by | Behaviour |
|---|---|---|---|
| `veiled_stone` | `skin`, `veil`, `age` | Sense (provider); Push or a lit-saber hit | VEILED looks exactly like its skin's stone. A Sense cast reveals the connected group (at most 27) for `veilRevealTicks`, plus `force_sense.seekerBonusTicks` (80) for a Seeker; a second cast refreshes it. The level keeps one scheduled tick per block, so a refresh is stored in `age` (in 40-tick steps, at most 240 ticks) and runs when the pending tick comes. While REVEALED, a Push on the aim or a hit with a usable lit saber opens the revealed members connected to the hit block (not VEILED ones Sense never showed). OPEN has no shape and no model. It resets to VEILED after `puzzleResetTicks` with nobody near and never while an entity stands in a cell of the group. |
| `counterweight_stone` | `skin`, `sunk`, `facing`, `age` | Push | Sinks for `counterweightTicks` and outputs redstone 15 from its back face only (into `pos.relative(facing.getOpposite())`, where the site hides its wiring). The `counterweight` warn plays `counterweightWarnTicks` before it rises by itself. A Push on a sunk weight does not refresh the timer. |
| `step_stone` | `extended`, `facing` | redstone | Powered, it is a top-slab ledge with collision. Unpowered, it is a 2 px plate against its wall with no collision. One `step_stone` event per stone that changes. |
| `echo_urn` | `facing`, `index` (decor) | Push | Reports to the keeper found through the `urn_keeper` POI within 16 whose stored offsets contain this urn. |
| `urn_keeper` (BE) | `facing`, `solved` | Sense within `urnSenseRange`; urn pushes | Stores 4 urn offsets in the template frame (rotated by `facing`), the progress, and its playback times. The order is a permutation seeded from its position. On Sense it plays the order: each urn echoes in turn, `urnStepTicks` apart. It can replay after `urnRetryTicks`. The right order solves it: redstone 15 on every side, latched. A wrong push resets the progress, drops `urn_dust` (`urnWrongDamage` within `urnWrongRadius` of the wrong urn), and calls `Sentinels.wakeNear(level, pos, sentinelWakeRadius, pusher)`, which never wakes a sentinel that sleeps for the pusher's side. |
| `pull_anchor` | `strain`, `age` (always 0) | Pull | Armed → strains for `anchorStrainTicks` → releases: redstone 15 on every side for `anchorOpenTicks` → rearms. |
| `collapsed_rubble` | `cleared`, `age` | Lift (every tick of the channel) | Counts the Lift dispatches. A gap over 4 ticks starts the count over. After `rubbleTicks` it clears: no shape, no model. It re-forms on reset, never on an occupant. |
| `puzzle_seal` | `open`, `skin`, `age` | redstone | Power opens the connected seal cells (at most 27) and latches them open. They close on reset only while unpowered, and never on an occupant. |
| `holocron_pedestal` (BE) | `kind`, `ready`, `always_ready` | redstone; use | It becomes READY once it is powered (latched), or at once with `always_ready`. Using a ready pedestal hands over to `HolocronService.usePedestal` (W3). Using one that is not ready shows the overlay "The stone is silent. Something here is still closed." It never wakes sentinels. The BE keeps `fixed_id` for W2's story pedestal. A latched `ready` clears on reset once the power is gone. |
| `tomb_vent` | `phase` | its own tick while a player is within `ventActiveRange` | IDLE waits `ventMinTicks`..`ventMaxTicks`. TELL lasts `ventTellTicks` and is a Danger Sense tell (`tomb_vent` variant 0). PUFF deals `ventDamage` of `tomb_miasma` to every living thing in the 1x2x1 column above (inflated by 0.5) and applies Slowness I for `ventSlownessTicks` through `ControlEffects.diminished(..., "slow")`, so Iron Discipline applies. A tell already shown always ends in its puff. With nobody near it rests. Resting vents tick randomly, and `TombVentBlock.kick` (used by the site tracker) starts them. |
| `lumen_moss`, `funerary_urn`, `wreck_salvage` | — | breaking | Lumen moss: a carpet with light 4 → 1–2 `lumen_thread`. Funerary urn → 1–2 `grave_silk` and a bone 10% of the time. Wreck salvage → 1–2 `durasteel_plate`. |
| 12 decor blocks | — | — | `ruin_stone` (+ carved, cracked, mossy), `tomb_stone` (+ carved, cracked), `wreck_plating` (+ scorched), `wreck_conduit` (light 2, sparks), `republic_panel`, `imperial_panel`. |

### Resettable timing

Each resettable block (veiled stone, rubble, seal, keeper, pedestal) keeps exactly one scheduled tick every
`puzzleTickInterval` (200). A player within `puzzleResetRange` (24) sets `age` back to 0. Otherwise `age` grows until
`age * interval >= puzzleResetTicks` (1200), and the block resets. The interval stretches if a configured reset would
need more than 6 steps, so a reset always happens. A reset that finds an entity in an invisible cell retries after 20
ticks. A block never turns solid around anyone. Each block's `idleStep(level, pos, playerNear, b)` is that step, a
public seam for the tests.

### Force and Sense

- The `force_reactive` block tag lists the five Force blocks. `PuzzleHandlers` registers with
  `ForceBlockInteractions`:
  - `veiled_stone` (PUSH);
  - `counterweight_stone` (PUSH);
  - `echo_urn` (PUSH);
  - `pull_anchor` (PULL);
  - `collapsed_rubble` (LIFT).
- Each handler answers only its own act and returns true when the block took it.
- W3 must dispatch LIFT on every server tick of the Lift channel while the rubble stays on the aim (a request in
  the report).
- `PuzzleSense` finds its blocks through POI lookups only, never a block scan:
  - veiled groups within the block range are revealed, one `BLOCK` point per group;
  - unsolved keepers within `urnSenseRange` play their order, one `URN` point per urn;
  - with `relic_lore`: `ANCHOR` points on raised counterweights, armed anchors, and the urns of keepers out of
    hearing;
  - with `sense_seeker`: `CACHE` points on pedestals and on the loot anchors of site pieces within
    `force_sense.glintRange`, through `StructureLocator.piecesNear`.
  - The caster owns a node as W3's Sense decides it: `ForcePowers.hasNode` (an active skill node, or the id in the
    ability set).

### POI types

`veiled_stone`, `echo_urn`, `urn_keeper`, `holocron_pedestal`, `counterweight_stone`, `pull_anchor`. They are
registered with `PoiHelper.register(id, 0, 1, block)`, and their ids are the block ids.

### Effects and sounds

The server sends one event per moment with `EffectEvents.around` (`puzzle_solved` uses `toPlayer`).

- The ids are: `veil_reveal`, `veil_open`, `counterweight`, `step_stone`, `urn_echo`, `urn_wrong`, `anchor`,
  `rubble_crumble`, `seal_open` (variant 1), `puzzle_solved`, `pedestal_ready` and `tomb_vent`.
- The client builds each look from dust, grit, chunks of the skin's stone and warm motes. There are no ground circles,
  glyphs, halos or outlines, and nothing flashes. The vent tell spends `Vfx.tellCount`.
- Looks that run on are kept in a short list ticked by the client. They are: the urn plume, the dust curtain, the
  counterweight trickle, the anchor's rust, the rubble shudder, the seal crumble, the pedestal motes, the vent tell and
  the vent puff. Each ends at its length, at its block's next state, or on a level change or disconnect.
- The urn notes are a minor tetrachord: the client pitches `puzzle.urn_echo` to D, E, F and G by step, with no random
  variation.
- The phase-by-phase treatment is in section 24; the look per event and every sound brief are in
  `docs/assets/world.md` (M3).

### Tests (`WorldM3GameTests`, part A)

- `veilFloodFillStopsAt27`, `veilRevealThenOpenByPush` and `puzzleResetAfterIdleAge`.
- `counterweightPowersStepStonesBackFaceOnly` and `stepStoneCollisionOnlyWhenPowered`.
- `urnOrderSeededByPosition`, `urnKeeperFoundThroughPoi` and `wrongUrnDustAndWakeSkipsOwnSide`. The last one works
  against both the Phase 0 sentinel stub and W5's real sentinels.
- `pullAnchorStrainThenRelease` and `rubbleClearsOnlyByLift`.
- `sealLatchesAndNeverClosesOnOccupant` and `ventTellThenPuffDamageAndSlowKind`.
- `puzzleBlocksUnbreakableInSurvival` and `pedestalReadyLatches`.
- `blockEntityNbtRoundTrips`: the keeper and pedestal, plus part B's terminal, cairn and spawner helpers.

Damage checks use pigs, because mock players are creative and spawn-invulnerable. Every test uses the lead's fixtures,
`maxTicks` ≤ 400 and seeded randomness, and none writes `WorldSites`.

## 22. Tests (part B) and the client steps

The part B server tests are in `WorldM3GameTests`. All use the lead's fixtures, `maxTicks` 400 or less, seeded
randomness and `M3TestPlayers.listed` for proximity. None writes `WorldSites`, the locator queue or the day time; the
rules run on detached values.

| Test | What it checks |
|---|---|
| `everyFrozenTemplateHasSidecarAnchorsInsideSize` | Each of the 21 templates loads with its frozen size. Its sidecar names exactly its frozen anchors, once each, inside the size. Each anchor cell holds what the anchor promises: the right pedestal kind, a loot chest, the right army's terminal, a vent, the keeper and urns, a counterweight, a veiled block, the right spawner group, or two air cells. Templates are loaded, never placed. |
| `outpostDecideMatrix` | The 3.5 matrix on detached values. Worldgen within 640 wins (inclusive), then reuse within 512, then a new outpost unless runtime outposts are off or 8 exist, which gives the nearest. Factions without outposts are refused. |
| `worldSitesCodecRoundTrip` | The codec keeps every record field and the expired owners, in order. The same placement replaces its record, a copy is independent, the expired owners keep the newest 64, and the record cap drops the oldest non-outpost first. |
| `terminalWaitsForLoadedEntities` | The loading rule: nothing is counted or spawned until the entities load. Then a never-filled terminal fills all five slots, and loaded bound members count as present. |
| `terminalRespawnsAfterTwoFailedChecks` | One empty check only counts. On the second, the officer is replaced at once on its spot, bound to slot 0. A garrison trooper waits for the refill with nobody within 48 blocks. A member seen again resets its count. |
| `staleOfficerDiscardsItself` | A member whose slot lists someone else discards itself on load and in the terminal's check. The listed member, an unlisted member of an empty slot, unbound entities and members bound to no terminal stay. |
| `siteSpawnerSpawnsOnceWithFreshSquadEach` | A spawner fires once, turns into its replace-with block and never fires again. Two spawners make two squads (different `Squad` ids). A third fires through its own check once its chunk is loaded and a listed player is near. A fourth, owned spawner ignores that player until it is theirs, then spawns its scavenger owned by them. Everything the spawners made is discarded at the end, even what a block-entity tick spawned first. |
| `cairnRemovesItselfWhenOwnerExpired` | The lazy expiry on a detached `Data`. The cairn stays for an online owner before the first echo, and for an owner listed nowhere. It removes itself once the owner is expired. A cairn without an owner never expires. |
| `echoShrinePlacementWithTestRings` | `EchoShrines.findSpot` with ring 4 in the arena. The spot lies along the owner's look, the entrance turns to the owner, and the pedestal is 8 below on the far side. Empty rings fall back; steep or crowded rules find nothing. |

Part B also has three NBT round-trip helpers, `roundTripTerminal`, `roundTripCairn` and `roundTripSpawner`, called
from part A's `blockEntityNbtRoundTrips`.

**Client steps (`m3/WorldClientSteps`, screenshots 10–19).**

- **Where the sites go.**
  - The five structures go through `/oldrepublic site place <structure>`, and the Jedi shrine through
    `/oldrepublic site place echo_shrine_jedi`.
  - Each site has its own spot on a ring 320 blocks or more from the start, 60 degrees apart, on dry ground when one of
    six candidates is dry.
  - Each area is force-loaded first.
- **The worldgen step.** It checks every placed site through `piecesNear` and `sitePieces`:
  - exactly the expected template pieces, both pilgrim niches included;
  - a pedestal in every `PEDESTAL*` cell;
  - no stray terrain in any anchor cell.
- **The screenshots.** They are taken in creative mode, flying, first person:
  - 10, the courtyard;
  - 19, the stair wall before and after `VeiledStoneBlock.reveal`;
  - 11, the hall's ledges after `CounterweightStoneBlock.sink` on all three counterweights;
  - 12, the gate;
  - 13, a processional vent in TELL, which must come within 1,200 ticks with the player beside it;
  - 14, the burial chamber;
  - 15 to 18, the hull, both outposts and the shrine.
- **Afterwards.** The game mode, position, force-loading and camera are put back.

## 23. Requests to other owners (M3)

- **W4.** W1 implements `TrooperPost` and calls `Troopers.bindTerminal` (done; section 16.3).
- **W2.**
  - `ChapterThreeBalance` keeps `int[] shrineRings` and `int[] shrineFallback` under those names; W1 reads them by
    section name.
  - The Calling and the toasts use `site.oldrepublic.<SiteKind id>` from `world.json`.
  - Entering a site calls `QuestHooks.onSiteEntered` from the site tracker.
  - Call `EchoCairns.expire(p)` when the player's chapter leaves LISTENING (section 17).
- **W3.**
  - Dispatch `ForceAct.LIFT` to the aimed `collapsed_rubble` on every server tick of the Lift channel (part A). W3's
    Lift does this; the rubble counts its own 20 ticks.
- **Lead.**
  - `events_m3.json` subtitles: `puzzle.urn_echo` "Urn echoes" → "An urn sings" (spec 3.4) and `ambient.tomb_whisper`
    "Something whispers" → "Whispers in the stone" (spec 3.7).
  - Run the W1 API probes (`tools/api-probe/world_m3.txt`, proposed in the W1 report) and the seed survey now that
    the structures exist.
  - Register `WorldM3GameTests` in the gametest `fabric.mod.json`.
  - W5's `ReliquarySites` (line 124) reads `chapter_three` through a private class, which replaces W2's cached
    `ChapterThreeBalance` the same way (section 17). After W2 merges, both W1 and W5 can read
    `ChapterThreeService.balance()` directly.

## 24. Effects phase by phase (spec 13, for `VFX_SFX_SPEC`)

Every event below is one `EffectEventPayload` (spec 13.3), built on the client by `PuzzleEffectHandlers` or
`SiteClient`. Particle counts are at Standard and go through `Vfx.count`, except the vent tell, which spends
`Vfx.tellCount` so no preset thins it. Every sound goes through the mod sound volume. Nothing is a ground circle, glyph,
halo or outline, and nothing flashes, so flashing off changes nothing here except the mast lamp. A "lingering" look is
drawn by the client for a few ticks after its event, and every lingering look ends at its length, at its block's next
state, or on a level change or disconnect. "—" means the event has no such phase.

| Event | Anticipation | Activation | Travel | Impact | Aftermath |
|---|---|---|---|---|---|
| `veil_reveal` | The caster's Sense inhale and dust streams (W3). | `puzzle.veil_reveal`, a soft granular hiss (0.8). | Up to 36 `veil_dust` grains (6 + 2 per stone) seep from the mortar lines of the group's open faces and drift down. | — | While REVEALED (160 ticks; 240 for a Seeker), each stone's display tick seeps a grain now and then. It ends when the stone goes back to VEILED or opens. |
| `veil_open` | The revealed stone's seep shows it can be opened. | A Push or a lit-saber hit: `puzzle.veil_crumble`, louder for bigger groups (0.7 + 0.03 per stone). | Up to 40 chunks of the skin's stone fall in toward the group's centre. | A dust fall from the tops (up to 30) and a small `tomb_dust` puff out of the open faces (up to 16). | The particles settle. The group closes again silently on reset (1,200 ticks with nobody within 24). |
| `counterweight` sink (0) | — | A Push: `puzzle.counterweight_sink`, a small shake within 4 blocks (0.08). | — | 14 dust puffs squeezed out of the side seams and 8 grit chips tossed up. | The weight stays sunk for 600 ticks with no particles. |
| `counterweight` warn (1) | This is the tell for the rise and the retracting ledges, 40 ticks ahead. | `puzzle.counterweight_warn`, a stuttering scrape. | — | — | Lingering: a grit trickle into the alcove until the stone rises (at most 60 ticks). |
| `counterweight` rise (2) | The warn, 40 ticks earlier. | `puzzle.counterweight_rise`. | — | 14 dust puffs slide off the top edges, with 4 grit chips. | The particles settle. |
| `step_stone` | The counterweight's sink (extend) or warn (retract). | `puzzle.step_extend` or `puzzle.step_retract` (0.9). | — | 10 grit chips and 5 dust puffs spill along the ledge's outer edge (extend) or the foot of the plate (retract). | — |
| `urn_echo` | Sense within 12 of the keeper; the steps come 20 ticks apart. | `puzzle.urn_echo` pitched D, E, F or G by step, with no random pitch. | A plume of fine dust (4 grains) and 2 warm `light_mote`s rise from the urn mouth. | — | Lingering: the plume keeps rising for 24 ticks. |
| `urn_wrong` | — (the wrong push is the cause; the sentinels' own wake tell follows, W5). | `puzzle.urn_wrong`, a rumble and rattle, with a shake within the damage radius + 2 (0.15). | A 3×3 curtain falls from the first ceiling block above the urn: 18 dust and 10 grit. | 2 `urn_dust` damage within 3 blocks of the urn (server). | Lingering: 16 ticks of trickle from the same ceiling. |
| `anchor` strain (0) | This is the tell: the ring shivers for `anchorStrainTicks` (10) before it gives. | A Pull: `puzzle.anchor_strain`, a tightening creak, and 6 rust flakes at once. | — | — | Lingering: rust flakes and dust shed from the ring faces until it releases (at most 40 ticks). |
| `anchor` release (1) | The strain. | `puzzle.anchor_release`, an iron clank. | — | A grit puff of 10 and 6 dust out of the ring faces. | The anchor powers for 600 ticks, then rearms silently. |
| `rubble_crumble` shudder | This is the tell: when a Lift channel starts on the heap, the crumble plays quiet and low (0.45, pitch 0.7) and 4 chips fall. | — | — | — | Lingering: grit slides off the heap for the channel length (at most 60 ticks) and stops when the heap clears. |
| `rubble_crumble` collapse | The shudder, over the 20 Lift ticks. | `puzzle.rubble_crumble` at full volume, a shake within 5 blocks (0.12). | 12 pebbles roll outward. | A 24-puff dust burst and 14 chunks of the rubble. | The dust settles. The heap re-forms silently on reset, never on an occupant. |
| `seal_open` (variant 1) | The source's own event (a counterweight sink, the keeper's chord, an anchor release). | `puzzle.seal_open`; a dust and grit burst from the seams of every seal cell (up to 36 dust, 10 grit). | — | Lingering: from tick 4 to tick 20, the face crumbles away in chunks of the skin's stone. | The seal stays open (latched) and closes silently on reset, never on an occupant. |
| `puzzle_solved` | — | `puzzle.solved`, a soft resolving chord, to the solvers within 24 only, with no position and no pitch variation. | — | — | — |
| `pedestal_ready` | The seal opening in front of it, or nothing for an `always_ready` pedestal. | `puzzle.pedestal_ready`, pitched by kind; 6 warm motes along the shaft's seams. | — | — | Lingering: 40 ticks of slow motes rising from the cradle, with a dust mote tinted by kind (gold, rust, brass) every 6 ticks. While READY, the display tick keeps a mote rising now and then. |
| `tomb_vent` tell (0) | This is the tell (a Danger Sense tell): `vent.hiss` (0.9), grit thrown up through the grate on every tick of the 15-tick tell (2 per tick through `Vfx.tellCount`), and a thin miasma wisp every 4 ticks. | — | — | — | — |
| `tomb_vent` puff (1) | The tell. | `vent.puff`, a dull whump. | — | A grey-green miasma cloud 1.5 wide and 2 high (14 puffs, topped up for 8 ticks) and 6 grit chips; 4 `tomb_miasma` damage and Slowness I for 60 ticks (server). | The cloud lingers about 30 ticks and thins. A resting vent breathes a thin dust wisp now and then (display tick). |
| `terminal_use` | — | `terminal.use` (0.8; the Imperial terminal 6% lower). | — | The screen brightens: a soft, still patch of light motes over the upper screen face, pale blue-white (Republic) or ember orange (Imperial). | It holds steady for 30 ticks and thins over the last 8. Nothing blinks, with flashing on or off. |

**Site ambience (`SiteAmbience`, spec 3.7).** The beds have no tells; their phases are entry, presence and exit.

| Phase | What happens |
|---|---|
| Entry | `site_presence` names the piece. The bed of the scene fades in over 60 ticks through `FadingLoop`: `ambient.jedi_ruin` (0.55), `ambient.sith_tomb` (0.6), `ambient.wreck` (0.55), `ambient.outpost` (0.4) or `ambient.reliquary` (0.6). Moving between scenes cross-fades. |
| Presence | One-shots play at a random point 4 to 18 blocks away: `ambient.ruin_chime` every 15 to 40 s, `ambient.tomb_whisper` every 8 to 20 s, `ambient.wreck_creak` every 10 to 30 s. Particles near the player, at most 40 live per site and thinned by the preset: light motes in sun shafts and falling leaves in the courtyard, lit dust in the halls, tomb dust from cracked stone, smoke wisps over scorched plating, glints in the Reliquary. The Republic mast lamp adds a red flare 8 ticks in every 40, and none with flashing off (the sea lantern stays as a steady lamp). For 20 ticks after the local player's own Sense gesture, the beds, the one-shots and M1's cave beds play 30% quieter, eased in over 3 ticks and out over 6. |
| Exit | Leaving the site or dying fades the bed out over 60 ticks. A dimension change stops it at once with the level, and a disconnect kills it. One-shots and particles stop with the scene. |
