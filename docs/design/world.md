# World feature design: Crystal Caves and the Trial of Reflection

Owner: world worker. Covers Milestone 1 journey steps 5 to 8 (the Calling's server-side search, the Cave,
the Trial of Reflection, the Crystal), the world blocks, the `crystal_cave` structure, the `shadow_echo`
entity and the client cave ambience.

Status legend for APIs: **[V]** verified against the 26.2 probe logs or Fabric 26.2 source,
**[U]** unverified (probe requested in `tools/api-probe/world.txt`), **[K]** known from older versions,
assumed unchanged until the probe confirms.

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
| `block/KyberClusterBlock` | Decorative cluster (extends `AmethystClusterBlock` [U ctor/codec]). Light 6. |
| `block/UnstableCrystalClusterBlock` + `block/UnstableClusterBlockEntity` | Hazard (cluster + `EntityBlock`, server ticker). |
| `block/ResonantSealBlock` | Seal wall with `OPEN` boolean property, scheduled close. |
| `block/MeditationStoneBlock` | Altar; interact starts the trial. |
| `block/WorldBlockAmbience` | Common-side hook interface; the client installs the implementation (keeps `Vfx` out of common code). |
| `trial/TrialManager` | Server-side trial sessions (start, tick, fail, complete, crystal grant). |
| `trial/TrialSession` | One running trial (owner UUID, level key, stone pos, echo UUID, phase, timers). |
| `trial/ShadowEchoEntity` | The echo (extends `Monster`). |
| `trial/EchoLungeGoal`, `trial/EchoPushGoal` | Telegraphed attacks. |
| `trial/CrystalGrant` | Builds the attuned `kyber_crystal` stack and the owner-only item entity; watches for pickup. |
| `WorldCommands` | `/oldrepublic locatecave`, `/oldrepublic trial start|stop`. |

Client (`src/client/java/com/oldrepublic/client/world/`):

| Class | Role |
|---|---|
| `WorldClient` | init: particle providers, entity renderer, render layers, effect handlers, ambience ticker. |
| `particle/*` | Providers for the four particle types (sprite-set animated, full-bright glow). |
| `ClusterAmbience` | Implements `WorldBlockAmbience`: animateTick motes/glints through `Vfx`, records cluster positions for the resonance loop. |
| `CaveAmbienceSounds` | `AMBIENT_CAVE` bed and `CRYSTAL_RESONANCE` positional loop as `AbstractTickableSoundInstance`s [V]; random `CRYSTAL_CHIME`. |
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
  "size": 4,
  "spawn_overrides": {},
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
- `spawn_overrides`: the enemy worker registers spawn placements for poachers (confirmed), so use
  `"monster": {"bounding_box": "piece", "spawns": [{"type": "oldrepublic:crystal_poacher", "weight": 1, "minCount": 1, "maxCount": 2}]}`
  (format [V] from pillager_outpost/trial_chambers). Poachers are primarily placed as template entities.

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

**descent** (`9 x 24 x 26`): sloped tunnel.
- Top jigsaw at (4, 23, 4), `orientation: up_north`, name `oldrepublic:descent_top`, target `minecraft:empty`,
  pool `minecraft:empty`, joint `rollable`, final_state `minecraft:air`.
- Vertical shaft 3x3 from y23 to y18 (ladder on one wall, timber shoring `stripped_spruce_log` frames every 4 blocks),
  then a 3-wide, 4-high tunnel sloping 1 down per 1 forward to z=25, y=1 (stone stairs underfoot, rail fragments,
  a dropped lantern, a cart of ore). 2-block rock shell around all air.
- Bottom jigsaw at (4, 1, 25), `orientation: south_up`, name `oldrepublic:descent_bottom`, target
  `oldrepublic:heart_entrance`, pool `oldrepublic:crystal_cave/heart`, joint `aligned`, final_state `minecraft:stone`... no:
  `minecraft:air` (it sits in the tunnel opening).

**heart** (`45 x 20 x 48`): main cavern, side passages, hidden chamber, trial chamber.
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

`searchRadius 50`, `cacheMoveBlocks 48`, `cacheSeconds 30`, `unstableRadius 2.5`, `unstableLingerTicks 10`,
`unstableChargeTicks 24`, `unstableDamage 4`, `unstableCooldownTicks 60`, `sealOpenTicks 200`,
`chamberRadius 8`, `chamberHeight 5`, `trialFormTicks 40`, `trialMaxTicks 6000`, `crystalFormDelayTicks 30`,
`echoHealth 32`, `echoDamage 4`, `echoSpeed 0.31`, `echoLungeCooldownTicks 100`, `echoLungeWindupTicks 14`,
`echoLungeDamage 5`, `echoPushCooldownTicks 160`, `echoPushWindupTicks 16`, `echoPushStrength 1.2`,
`echoPushDamage 2`, `trialXp 150` (granted by the quest on completion? No: quest owns rewards; world grants none).

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
