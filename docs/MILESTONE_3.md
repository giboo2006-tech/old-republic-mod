# Milestone 3: Exploration (design spec)

This is the working spec for Milestone 3. It turns the third roadmap entry of `DESIGN_BRIEF.md` section 17 into concrete, testable features that build on what Milestones 1 and 2 built:

- More structures and environments.
- Holocrons.
- Faction missions.
- Additional enemies.
- First major boss.

Gabriel also queued these items for Milestone 3:

- Trooper mobs: **Republic troopers** and **Sith (Imperial) troopers**.
- Wearable era armour: **Republic trooper armour**, **Sith trooper armour**, **Jedi robes** and **Sith robes**.

The brief places armour in Milestone 4 ("Additional weapons and armour"). Gabriel moved these four sets into Milestone 3, and `DECISIONS.md` records that decision. The rest of the Milestone 4 armour work (further sets, upgrades and mods) stays in Milestone 4. Gabriel also kept the Chapter Two pacing unchanged: the Trial of the Blade still needs level 6, and Chapter Two XP is not changed.

All numbers here are defaults. As in Milestones 1 and 2, the live values go into `config/oldrepublic/balance.json` through `BalanceConfig` sections; section 14 lists every new key. Where this spec and the brief disagree, the brief wins and the difference goes into `DECISIONS.md`.

Contents:

1. Scope, cut order, and what is left for later
2. Player journey
3. Structures and environments
4. Holocrons
5. Factions, reputation, missions and ranks
6. Chapter Three: "The Ones That Sing"
7. Enemies
8. The first major boss: Orren Vasch, the Collector
9. Era armour
10. Abilities, skills and other systems
11. Controls and HUD
12. Persistence, multiplayer, dedicated server and config
13. Effects and sounds
14. Balance
15. Architecture and work split
16. Shared identifiers
17. Test plan
18. Lore notes for `DECISIONS.md`
19. Open questions

---

## 1. Scope

### In Milestone 3

| Area | What ships |
|---|---|
| Story | **Chapter Three, "The Ones That Sing".** A dream of singing crystals opens the chapter. Each path then has its own opening that teaches **Force Sense**: the Jedi lesson "Listen", the Sith lesson "The Hunt", or **Courier's Fall** for Independents (the wreck of Vasch's own courier). The player follows the trail of **Orren Vasch, the Collector**, the man who paid the poachers for crystals in Chapter Two's Ledger. Four **leads**, gathered from exploration, holocrons and missions, reveal his **Reliquary**; Vasch makes himself felt twice on the way (6.4). After the fight the player makes two choices, his fate and then his hoard's, and the chapter ends with the rank 3 ceremony. Chapter Two's spared characters return in small cameos (5.4). |
| Structures | Three worldgen structures, each with a puzzle and a garrison: the **Jedi Ruin "Watchers' Rest"** (forests), the **Sith Tomb** (deserts and badlands) and the **Wreck Field** (plains, around a crashed pre-Treaty frigate). Two outposts, the **Republic Forward Post** and the **Imperial Listening Post**, come from worldgen. Where none is near a player, one is placed at runtime; these runtime outposts are shared and recorded in a world-level attachment. Per-player runtime sites: Courier's Fall, Echo Shrines (the old-world fallback, one per needed kind), six mission camps and the Reliquary. Garrisons appear from `site_spawner` blocks the first time a player comes near. |
| Environments | Each structure family has its own ambience bed, weathering, particles and harvestable resource: `lumen_moss` in Jedi ruins gives `lumen_thread`; `funerary_urn` in Sith tombs gives `grave_silk`; `wreck_salvage` in wrecks gives `durasteel_plate`. Sith tombs also have vent hazards. |
| Puzzles | A kit of Force-reactive blocks that change state and drive vanilla redstone hidden in the walls. No block ever moves. Every primary route needs only Push and Sense. Pull, Force Jump and Lift open optional caches. |
| Holocrons | 12 world holocrons (4 Jedi, 4 Sith, 4 Wayfarer) and 3 Archive holocrons from the Collector's hoard. Each pedestal gives each player **at most one** holocron: their next unattuned one of its kind. Collecting a kind therefore means finding four chambers. A gatekeeper is projected from the holocron's opened seams and speaks. An opposed-side holocron offers **Seal** or **Break** against **Listen**. The player can return a holocron for standing or carry it for Force regen. Also: collection milestones, a skill point every second holocron, and echoes with shards once a kind is complete. |
| Factions and missions | The **Republic** becomes the fourth faction. Troopers react to reputation with four attitudes: Hostile, Wary, Neutral and Allied. Each army gives two story missions, and each one is a lead. **Bounty terminals** offer five repeatable contract types with a daily soft cap. Faction work earns **commendations**, which buy trooper armour. A HUD mission tracker shows the current mission. |
| Ranks | Rank 3: Jedi **Knight** and Sith **Lord**. The Collector fight is the rank 3 trial (brief section 5: ranks need missions and trials); the ceremony follows once the other requirements are met. Independents earn the **Pathfinder** title. |
| Enemies | **Republic troopers** (Rifleman, Field Medic, Sergeant) and **Imperial troopers** (Rifleman, Grenadier, Sergeant) share one entity class, plus non-combat officers. Cold-peace behaviour: rival patrols halt, bark and walk on. The **Ruin Sentinel** is an ancient construct with two variants, Watcher and Tomb. Vasch's paid diggers (crystal poachers) work every ruin and tomb. |
| Boss | **Orren Vasch, the Collector**, a fallen Jedi Archivist, fought in three phases (The Archivist, The Singing Wall, Unkept) in his Reliquary. He scales with the number of players. Each path has its own ending, with a custody, execution or release scene. After the first clear he has a daily rematch, **Echo of the Collector**, and an **Unchained** mode. |
| Armour | Four wearable sets of four pieces each: **Republic trooper armour**, **Sith trooper armour**, **Jedi robes** and **Sith robes**. Each piece has traits, and a full 4/4 set adds a set bonus. |
| Abilities | **Force Sense** is universal and has its own key (H). **Telekinetic Lift** is the Independent signature power (the brief lists Lift as Universal; `DECISIONS.md` records the change), with its own role in the boss fight. A fifth skill tab, **Exploration and Survival**. Holocron nodes and rank 3 nodes. A new pommel, a new emitter and a cosmetic singing crystal. |
| Controls and HUD | The Sense key, a mission tracker, a boss bar, trooper barks, and two journal tabs: Holocrons and Missions. |

### Left for later milestones (on purpose)

- **M4 (Broader Content):**
  - redemption and corruption questlines that change path;
  - inhabited Jedi enclave and Sith academy hubs;
  - vendors and reputation shops;
  - armour beyond these four sets, and armour upgrades and mods;
  - player blasters and vibroweapons;
  - crafting specialisation;
  - planet-themed dimensions and transport ships;
  - the remaining powers from the brief: Speed, Stasis, Barrier, Protect, Valor, mind influence, Choke, Drain, Fear, Rage and Crush;
  - more saber forms and double-bladed sabers.
- **M5 (Advanced Features):**
  - ranks above 3 (Master, Darth);
  - endgame encounters and raid-size bosses;
  - further faction stories, such as Mandalorians and Hutt cartels;
  - flight and large-scale battles.
- **Not in M3, by design:**
  - voice acting (barks are subtitled lines with a radio click);
  - companions;
  - PvP faction warfare;
  - rebuilding of griefed structures (puzzles reset, broken decor does not);
  - quest party sharing.

### Cut order (if the milestone runs long)

Nothing from the roadmap or from Gabriel's queue is on this list. The lead cuts from the top and records each cut in `DECISIONS.md` and `KNOWN_ISSUES.md`.

1. Uniform disguise (stretch goal, 9.5).
2. Lift branches `lift_suspend` and `lift_hurl` (Lift itself stays).
3. The natural patrol spawner (7.6). Troopers stay at outposts and in missions.
4. The rematch and Unchained mode (8.9).
5. Robe custom models (9.4). The plain equipment layers ship instead.
6. The Wreck Field (3.2). Pilgrim's niches in ruins and tombs, and the Wayfarer Echo Shrine, still hold Wayfarer holocrons.
7. The Independent bot run (17.3). The Independent path is then checked by hand.

---

## 2. Player journey

1. **Quiet again.** Chapter Two is complete: the player is a Padawan, an Apprentice or an Independent who finished "No Master". Saves from M2 that have reached that point enter Chapter Three on their next join.
   - After one in-game day, or the next sleep, at a safe moment in the overworld, the **"Ones That Sing" dream** plays (6 s, subtitled). A crystal hums inside stone. Two banners snap in a wind, one blue-white and one charcoal and crimson. A tired, careful voice says: *"Neither of them should have you."*
   - Journal entry `the_ones_that_sing` and codex entry `two_flags` follow: under the Treaty, both the Republic and the Empire have soldiers on this world.
   - Republic standing is seeded, and outposts are found or placed (3.5).
2. **Learning to listen.**
   - **Jedi:** Master Saelen has traced the Ledger's credits to a theft from the Watch's own Archive: *"An Archivist named Orren Vasch walked out of our Archive the year the Treaty was signed. He took the Archive with him."* Lesson **"Listen"** grants Force Sense and the Jedi hood and robe. She has veiled 3 cairns within 24 blocks of camp.
   - **Sith:** Overseer Sorn says Imperial Intelligence bought names from the poachers, and the player's name was one of them. The man who bought the crystals also robbed a tomb the Empire calls its own. Lesson **"The Hunt"** grants Force Sense, the Sith hood and robe, and 3 veiled cairns.
   - **Independent:** while the journal is held, a slanting trail of falling embers leads to **Courier's Fall**, a crashed courier shuttle 64 to 96 blocks away. It was **Vasch's own courier**: its hold carries his payments to the poachers and a sealed note inviting "a reader of no Order" to his Archive. Two scavenging poachers are there. Inside sits the **Wayfarer's Compass** holocron. Its gatekeeper, **Tessaly Arn**, a pilgrim of no Order, teaches Force Sense. The player then finds 3 veiled caches.
   - Everyone gets the same small "aha" moment: the world hides things, and a breath of the Force shows them.
3. **The first echo.** The Force points to the nearest structure of the player's own kind:
   - a Jedi Ruin (Jedi);
   - a Sith Tomb (Sith);
   - a Wayfarer pedestal, in a Wreck Field or a pilgrim's niche (Independent).

   If there is none within 768 blocks, an **Echo Shrine** is placed nearby instead. Every ruin and tomb has a fight on the way in: Vasch's paid diggers (crystal poachers) work the site, and sentinels stand dormant (they sleep for players of their own side, 7.7). The Imperial dig team at a tomb and the Republic salvage crew at a wreck react to the player's standing: a salute, a warning or a fight (5.2). The player opens the hidden stair with Sense and Push, and solves the chamber with Push.

   At the pedestal the gatekeeper is projected from the holocron's opened seams: **Master Aveth Morrane**, **Lord Hadrek Zarvos** or **Pell of the Long Road**. Independents learn **Telekinetic Lift** from Pell. Every gatekeeper says the next pedestal along was emptied by *"a careful man with an Archivist's hands"*. **The Collector's Trail** opens with Leads 1/4, or 2/4 if the Ledger was read.
4. **Two flags.** The player visits the outposts.
   - The Republic Forward Post has off-white panels, navy banners, sandbags and a comms mast.
   - The Imperial Listening Post has blackstone, crimson trim and a sensor spire.
   - Each has an officer, a bounty terminal and a garrison.

   Under the cold peace, rival patrols halt, stare and walk on. A Jedi is saluted by Republic troopers and warned off by Imperial ones; a Sith gets the reverse; an Independent is watched by both. Each army offers two story missions. Contracts pay XP, reputation and commendations, and most players craft their first trooper piece or finish a robe set here.
5. **The hunt widens.** More holocrons are optional leads. Each pedestal answers a player only once, so every further holocron means a new chamber, and Sense pulses point toward the nearest chamber that still has one for them: wandering becomes a hunt. Vasch notices: at 2 leads the next gatekeeper carries a message he left in its holocron (*"You are reading my shelves."*), and at 3 leads a crew of his paid poachers ambushes the player. The level climbs from about 7 to 9.
6. **Convergence.** At 4 leads and level 9, every holocron the player carries hums toward one point: the **Reliquary**, 96 to 144 blocks away. It is a collapsed watchtower over a sinkhole, with a spiral descent and an antechamber holding a bed and a sealed door. The journal says plainly that the recommended level is 10.
7. **The Collector.** Vasch closes his book: *"Give them back? To whom?"* An Independent also hears his offer: a place at his side, outside every Order.
   - **The Archivist:** a guarded duellist who hurls masonry.
   - **The Singing Wall:** four crystal pylons sing a ward around him. The player silences them with the saber, Push, Pull, Lift, Saber Throw, Lightning, or a sentinel's lance parried into the crystal. When the last one falls he staggers off the dais, **Unsung**, and takes extra damage.
   - **Unkept:** he grips a stolen Sith holocron. The Surge forces players into cover, and he is Spent afterwards.

   At 10% he kneels. The player chooses by path: **Spare** (Jedi: "You'll answer to the Watch"; Sith: "Go, before I change my mind"), **Execute**, **Bind** (Sith), or for an Independent **"Go. Never come back."** or **Bring him in** to either army. Each ending has its own short scene. Behind his dais the hoard opens, and the player chooses again: **Return**, **Keep** or **Unmake** the three Archive holocrons.
8. **Aftermath and rank.** The player reports to the mentor; Independents report to an officer of their choice. The Collector fight was the rank trial. When the other requirements are met (level 11, trust 80, Honoured with their own faction, alignment ±15, 2 own-kind holocrons, 3 own-side contracts), the mentor holds the **Knighting at the Watch** or **the Naming**. Independents name themselves **Pathfinder** at Courier's Fall, with Tessaly Arn's echo as witness.
9. **The ongoing loop:**
   - up to 3 full-pay contracts per in-game day;
   - the remaining holocrons, then echoes and shards;
   - armour crafting;
   - the daily Echo of the Collector;
   - patrol standoffs;
   - mentor sessions, as in M2.

A solo player who explores at a normal pace reaches the boss in about 5 to 7 hours. Section 14 starts with the XP budget behind these level marks.

---

## 3. Structures and environments

Ownership:

- **W1** owns the worldgen structures, the outposts, the Echo Shrines and the puzzle kit.
- **W2** owns Courier's Fall and the mission camps.
- **W5** owns the Reliquary.

Every template is generated by Python, and each generator imports `Template`, `hash01` and `value_noise` from `tools/assets/gen_structures.py` and uses its `DATA_VERSION`:

- `gen_structures_m3.py` (W1);
- `gen_structures_story_m3.py` (W2, new file; `gen_structures_story.py` from M2 is not edited);
- `gen_structures_reliquary.py` (W5).

`gen_structures.py` itself belongs to the lead and changes only by request.

**Anchors are not blocks.** Worldgen jigsaw placement always skips structure blocks (`SinglePoolElement#getSettings` adds `BlockIgnoreProcessor.STRUCTURE_BLOCK`), so a DATA-mode marker would leave whatever terrain was there. Instead, each generator writes the real block in every anchor cell (air, floor, pedestal, spawner) and emits a sidecar file, `data/oldrepublic/site_anchors/<template path>.json`, that maps anchor names to template-local positions. `SiteAnchors` loads the sidecars through a server data reload listener. Phase 0 freezes the template ids, sizes and anchor names (15.1); the sidecar is the source of the positions.

**Entities are not template entities.** No template carries an entity list. Garrisons, diggers, scavengers and sentinels come from `site_spawner` blocks (3.3) in the anchor cells, so every squad gets its own fresh squad id and every spawn goes through `Troopers.*`, `Sentinels.*` or `Poachers.spawnCrew` (15.1).

**Art rule for every site:** no floor circles, sigils or glowing runes. Atmosphere comes from light shafts, dust, overgrowth, scorch marks, weathering and sound.

### 3.1 Site overview

| Site | `SiteKind` | Placement | Size (x × y × z) | Owner | Purpose |
|---|---|---|---|---|---|
| Watchers' Rest (Jedi Ruin) | `JEDI_RUIN` | worldgen | courtyard 31×14×31, stair, hall 25×14×25 | W1 | Jedi pedestal; Wayfarer pedestal in the pilgrim's niche; lumen moss |
| Sith Tomb | `SITH_TOMB` | worldgen | gate 17×12×17, dig camp, processional, chamber 27×14×27 | W1 | Sith pedestal; pilgrim's niche; grave silk; vents |
| Wreck Field | `WRECK_FIELD` | worldgen | hull 41×18×19, salvage camp, scars | W1 | Wayfarer pedestal; durasteel |
| Republic Forward Post | `REPUBLIC_OUTPOST` | worldgen, plus a shared runtime fallback | 23×12×23 | W1 | officer, bounty terminal, garrison |
| Imperial Listening Post | `IMPERIAL_OUTPOST` | worldgen, plus a shared runtime fallback | 23×14×23 | W1 | officer, bounty terminal, garrison |
| Echo Shrine (one template per kind: Jedi, Sith, Wayfarer) | `ECHO_SHRINE` | runtime, per player | 15×14×15, placed whole with its lowest 9 layers buried | W1 | first-echo fallback, and the rank 3 / Pathfinder guarantee (4.3) |
| Courier's Fall | `COURIERS_FALL` | runtime, per player | 15×9×11 | W2 | Independent opening |
| Mission camps (6) | `MISSION_SITE` | runtime, per player | 11×7×11 to 17×6×9 | W2 | story missions and contracts |
| The Reliquary | `RELIQUARY` | runtime, per player | surface 17×16×17; hall 31×18×31 | W5 | boss arena and hoard |

### 3.2 Worldgen structures

All three follow the `crystal_cave` recipe: a `structure` file, template pools, processor lists, a biome tag and a structure tag. Three things differ:

- **Structure sets.** Vanilla `random_spread` placement takes a single optional `exclusion_zone`, so a list of exclusions does not decode and the world would not load. The three ruin-type structures therefore share **one** structure set, `oldrepublic:ruins`: a weighted `structures` list (`jedi_ruin` 1, `sith_tomb` 1, `wreck_field` 1), spacing 32, separation 12, salt 734211, and one `exclusion_zone {other_set: oldrepublic:crystal_caves, chunk_count: 6}`. When the picked structure fails its biome check, vanilla tries the next entry of the set, so each structure still fills its own biomes, and the three can never overlap. The per-kind structure tags (`jedi_ruins`, `sith_tombs`, `wreck_fields`) stay, for `StructureLocator`.
- **Start pieces.** `CampPoolElement` is not reused: its yard height and clearing heights are hard-wired to the crystal camp and would erase the top of the courtyard and the hull, and changing its codec would break saved `crystal_cave` starts. W1 adds `oldrepublic:adapted_pool_element`, a `SinglePoolElement` subclass whose codec adds `yard_stand`, `clear_from`, `clear_inset`, `support_depth` and `ramp_rings` and reuses the `CampPoolElement` static helpers with those values. The courtyard, the gate and the hull use it with `clear_from` = template height.
- **Surface side pieces** (`dig_camp`, `salvage_camp`, `scar`) attach through a short `terrain_matching` connector piece (`<structure>/path`, the village street pattern), so each rigid side piece takes its height from the ground rather than from its parent. They also use `adapted_pool_element` with `support_depth` 3 so small gaps under them are filled.

| | Watchers' Rest `jedi_ruin` | Sith Tomb `sith_tomb` | Wreck Field `wreck_field` |
|---|---|---|---|
| Inspiration | Tython-like forest ruins of a Watch enclave | Korriban-like desert tombs | a pre-Treaty battlefield around a crashed frigate |
| Biome tag `has_structure/<id>` | forest, birch_forest, old_growth_birch_forest, flower_forest, taiga, old_growth_pine_taiga, old_growth_spruce_taiga, meadow, cherry_grove | desert, badlands, eroded_badlands, wooded_badlands, savanna_plateau | plains, sunflower_plains, savanna, snowy_plains, windswept_hills |
| Structure set | `oldrepublic:ruins` (shared) | `oldrepublic:ruins` (shared) | `oldrepublic:ruins` (shared) |
| Pieces (frozen ids and sizes) | `jedi_ruin/courtyard` 31×14×31 (start, adapted) → `jedi_ruin/stair` 7×12×9 → `jedi_ruin/listening_hall` 25×14×25; side pieces `jedi_ruin/pilgrim_niche` 7×6×7 (**mandatory**) and `jedi_ruin/anchor_cache` 7×6×7 | `sith_tomb/gate` 17×12×17 (start, adapted, bottom 3 layers buried); side piece `sith_tomb/dig_camp` 19×8×19 at the surface, through `sith_tomb/path`, at least 12 blocks from the stair mouth → `sith_tomb/processional` 9×14×27 (descending) → `sith_tomb/burial_chamber` 27×14×27; side pieces `sith_tomb/pilgrim_niche` 7×6×7 (**mandatory**) and `sith_tomb/rubble_room` 9×7×9 | `wreck_field/hull` 41×18×19 (start, adapted, tilted frigate section, bottom 4 layers buried, contains the cargo hold); `wreck_field/salvage_camp` 15×8×15 through `wreck_field/path`; `wreck_field/scar` 15×6×15 (2 or 3 craters) |
| Surface | broken colonnade, a sun-shafted meditation garden, a robed statue with its face worn away, half a dome | half-sunk obelisk gateway, wind-cut steps, Imperial dig tents and crates | scorched furrows, torn hull plates, gun emplacement stubs, Republic salvage tents |
| Underground and puzzle | The stair behind veiled stone; the **Listening Hall** (3.4) | The **processional** with vents; the **burial chamber** urn puzzle (3.4) | The **cargo hold** behind veiled plating (3.4) |
| Garrison (from `site_spawner` blocks, spawned once, never respawned) | 2 Watcher sentinels; 2 or 3 of Vasch's diggers (crystal poachers) in the courtyard | 3 Tomb sentinels; 2 or 3 of Vasch's diggers in the processional; an Imperial dig team (2 riflemen, 1 grenadier) at the dig camp | Republic salvage crew (2 riflemen, 1 field medic) at the salvage camp; 2 crystal poachers scavenging inside the hull |
| Pedestals | Jedi (hall gallery); Wayfarer (pilgrim's niche) | Sith (inner sanctum); Wayfarer (pilgrim's niche) | Wayfarer (cargo hold) |
| Resource | 10 to 14 `lumen_moss` patches → `lumen_thread` | 8 to 10 `funerary_urn` on the main route → `grave_silk` | 6 to 8 `wreck_salvage` piles → `durasteel_plate` |
| Loot chests | `chests/jedi_ruin` | `chests/sith_tomb` | `chests/wreck_field` |
| Spawn override | `monster: []` within the piece boxes | same | same |

**Mandatory niches.** The pilgrim's niche joins its parent through a single-element pool with no empty fallback, so every ruin and tomb has a Wayfarer pedestal (6.3 depends on it). A worldgen client test checks it (17.2).

**Garrisons** come from `site_spawner` blocks (3.3). The block ticks only in loaded, entity-ticking chunks; the first time a player is within 32 blocks and the chunk's entities are loaded, it spawns its group (a fresh squad id per group), marks them persistent, and turns into the block recorded in its data (usually air). It never spawns again, so nobody can farm a site. Groups: `dig_team`, `salvage_crew`, `diggers`, `scavengers`, `watcher`, `tomb`.

**Loot.** No chest ever holds commendations, armour or holocrons.

| Table | Contents |
|---|---|
| `chests/jedi_ruin` | `lumen_thread` 1–3, paper 2–5, book 1 (40%), emerald 1–2 (25%), `durasteel_plate` 1–2 (30%), golden apple (5%) |
| `chests/sith_tomb` | `grave_silk` 1–3, gold ingot 1–4, bone 2–4, `durasteel_plate` 1–2 (30%), emerald 1–2 (25%) |
| `chests/wreck_field` | `durasteel_plate` 2–5, iron ingot 2–6, redstone 2–6, copper ingot 2–4 |
| `chests/force_cache` (optional side caches) | `durasteel_plate` 2–3, `lumen_thread` or `grave_silk` 2, experience bottle 2–4, emerald 2–4 |

### 3.3 Puzzle kit (W1, `world.puzzle`)

Rules for every puzzle block:

- Puzzles work by **state changes and vanilla redstone**. No block moves, nothing uses `FallingBlockEntity`, and no power changes ordinary terrain.
- Puzzle blocks are unbreakable in survival: destroy time -1, explosion resistance 3,600,000. Decor blocks can be broken.
- A Force power reaches a puzzle block only on the caster's aim ray, within the power's range and with line of sight (`ForceBlockInteractions.findOnAim`, 10.6).
- The resettable blocks below reset after 1,200 ticks with no player within 24 blocks (`sites.puzzleResetTicks`, `sites.puzzleResetRange`). They do this with their own scheduled tick every 200 ticks and an `age` property, so no block entity scans are needed. They schedule that tick when their state changes, so blocks placed by worldgen (which never runs `onPlace`) are fine. Every visitor solves the puzzle for themselves, and holocron rewards are per player anyway.
- **Lookups use POI types, never block scans.** W1 registers POI types with Fabric `PoiHelper.register` for `veiled_stone`, `echo_urn`, `urn_keeper`, `echo_cairn`, `holocron_pedestal`, `counterweight_stone`, `pull_anchor` and `bounty_terminal`. POIs are indexed per chunk section for blocks placed by worldgen, runtime placement and game tests alike, so `PoiManager#findAll` (API-CHECK, 15.1 probe) works everywhere a site can exist.
- Blocks that turn invisible (`veiled_stone` OPEN, `echo_cairn` VEILED, `collapsed_rubble` cleared, `puzzle_seal` open) return `Shapes.empty()` from `getShape`, `getCollisionShape` and `getOcclusionShape` in that state, and render shape INVISIBLE, so neighbours do not cull their faces and no see-through holes appear.
- Every M3 structure and puzzle block is in `#oldrepublic:built_blocks` (15.1), so runtime placement never stamps over a site.

| Block | States | Reacts to | Effect | Reset |
|---|---|---|---|---|
| `veiled_stone` | `skin` RUIN / TOMB / WRECK; `veil` VEILED / REVEALED / OPEN; `age` 0–6 | Sense (W1's `SenseTargets` provider, POI within 16); then a Push hit or a lit-saber hit while REVEALED | VEILED looks exactly like the stone of its skin. REVEALED lasts 160 ticks, and fine dust seeps from its seams. Opening it turns the connected group (flood fill, at most 27 blocks) to OPEN: no collision, no model, no occlusion, with a dust fall. | OPEN goes back to VEILED |
| `counterweight_stone` | `sunk` false / true; `facing` (horizontal); `skin` RUIN / TOMB / WRECK; `age` | Push hit | Sinks with a grinding thud and outputs redstone 15 from its back face only (`getSignal` / `getDirectSignal` on `facing.getOpposite()`) for 600 ticks, calling `updateNeighborsAt` on each change. A grind warning plays 40 ticks before it rises. | rises by itself |
| `step_stone` | `extended`; `facing` | redstone power | Sits in the ledge's own cell, in front of the wall. Powered: a top-slab ledge with collision. Unpowered: a thin plate against the wall with no collision. (A collision shape outside its own cell would not be checked reliably.) | follows power |
| `echo_urn` | `facing`; `index` 0–3 (decor only) | Push hit | Reports the push to its keeper: the `urn_keeper` POI within 16 whose stored urn offsets contain this urn. No anchor and no scan are needed, so it works in runtime and test placements too. | |
| `urn_keeper` (block entity) | `facing`; `solved` | Sense within 12 (W1 provider); urn pushes | Stores its 4 urn offsets in the template frame, rotated by `facing`, plus an order of 4 seeded from its position and the progress so far. On Sense it plays the sequence: each urn echoes in turn, 20 ticks apart. The correct push order outputs redstone 15 and latches. A wrong push resets the progress, wakes the sentinels within 16 (`Sentinels.wakeNear`, which never wakes a sentinel that sleeps for this player's side, 7.7) and drops a dust fall (`urn_dust`, 2 damage within 3 blocks of the wrong urn). The sequence can be heard again after 100 ticks. | `solved` clears |
| `pull_anchor` | `strain` 0 / 1 / 2; `age` | Pull hit | Strains for 10 ticks (1), then releases (2) and outputs redstone 15 for 600 ticks | rearms |
| `collapsed_rubble` | `cleared`; `age` | Lift (`ForceAct.LIFT` on the aim) | Shudders for 20 ticks of channel, then clears: no collision, no model, with a debris burst | re-forms |
| `puzzle_seal` | `open`; `skin` | redstone power | A sealed wall face. When powered it opens (no collision) with a dust burst, and **latches** open. | closes on reset, but only when no entity is inside its space |
| `holocron_pedestal` (block entity) | `kind` JEDI / SITH / WAYFARER; `ready`; `always_ready` | use | READY latches the first time it is powered, or is always on where `always_ready` is set. Using it calls `HolocronService.usePedestal` (W3). When not ready: "The stone is silent. Something here is still closed." | `ready` clears |
| `echo_cairn` (block entity: owner, index) | `style` CAIRN / CACHE; `veil` VEILED / REVEALED | the owner's Sense within 24 (W2 provider calls `EchoCairns.reveal`); the owner's use while REVEALED | VEILED has no model, no collision and no occlusion. REVEALED lasts 600 ticks and shows a small stacked-stone cairn or a half-buried satchel. Using it calls `QuestHooks.onCairnTouched`, and the block turns to dust. | removed once touched. Removal after the chapter passes LISTENING is lazy: the block entity checks its owner on its own tick (owner online and past LISTENING, or listed in the expired-owner set kept in `WorldSites`) and removes itself; nothing writes into unloaded chunks. |
| `tomb_vent` | `phase` IDLE / TELL / PUFF | its own scheduled tick every 60 to 100 ticks, while a player is within 8 | TELL lasts 15 ticks: a hiss, and grit rises through the grate. Then PUFF: 4 damage (`tomb_miasma`) and Slowness I for 60 ticks (through `ControlEffects.diminished(..., "slow")`, so Iron Discipline applies) to living entities in the 1×2×1 column above, inflated by 0.5. Danger Sense warns of it. **Starting:** worldgen never calls `onPlace`, so the first tick is kicked two ways: `SiteTracker` schedules the `VENT_1`–`VENT_4` cells of a processional the player stands in (if no tick is pending), and IDLE vents are randomly ticking, so any vent (runtime, test or `/place`) starts on its own within about a minute. | loops |
| `bounty_terminal` (block entity) | `faction` REPUBLIC / IMPERIAL; `facing` | use; a server tick every 200 ticks | Opens the mission board (`Missions.openBoard`, W2). Anchors its outpost: it stores the officer's UUID, up to 4 garrison UUIDs and the last refill time, and spawns them through `Troopers` (W4). **Loading rule (frozen):** before deciding anything is missing it requires `areEntitiesLoaded` for its own chunk and the 8 around it and `isPositionEntityTicking(pos)`; it counts members with `getEntities` in its leash box (bound to this terminal), not only by UUID; and it respawns only after two refill checks in a row have found the slot empty. A trooper or officer that loads while its terminal lists a different UUID for its slot discards itself (the M2 "stale copy removes itself" rule). | |
| `site_spawner` (block entity: group, bound, replace-with state) | none; invisible, no collision, unbreakable | a block-entity tick every 20 ticks | The spawn point for worldgen and shrine garrisons (3.2). When a player is within 32 blocks and the chunk's entities are loaded, it spawns its group (a fresh squad id), then turns into its replace-with block. Never spawns twice. | |

**Resource and decor blocks:**

- `lumen_moss`: a carpet with light level 4 and faint drifting motes. Breaking it drops 1–2 `lumen_thread`.
- `funerary_urn`: breaking it drops 1–2 `grave_silk`, plus a bone 10% of the time.
- `wreck_salvage`: breaking it drops 1–2 `durasteel_plate`.
- Decor blocks: `ruin_stone`, `ruin_stone_carved`, `ruin_stone_cracked`, `ruin_stone_mossy`, `tomb_stone`, `tomb_stone_carved`, `tomb_stone_cracked`, `wreck_plating`, `wreck_plating_scorched`, `wreck_conduit` (sparks), `republic_panel`, `imperial_panel`.
- None of these regrow.

`#oldrepublic:force_reactive` contains `veiled_stone`, `counterweight_stone`, `echo_urn`, `pull_anchor` and `collapsed_rubble`.

### 3.4 Puzzle layouts

**Watchers' Rest:**

1. **Stair.** The stair mouth hides behind a 3×3 `veiled_stone` wall (skin RUIN) under the half-dome. Sense reveals it; then a Push or a saber hit opens it.
2. **Listening Hall.** The Jedi pedestal (`always_ready`) stands on a gallery 7 blocks up the north wall.
   - Three `counterweight_stone`s sit in floor alcoves on the west, east and south sides.
   - Each Push sinks one for 600 ticks. Through redstone hidden in the walls, each powers 2 `step_stone`s on the north wall.
   - With all three sunk, six ledges lead up in 1-block steps to the gallery.
   - Optional skip: a full-charge Force Jump from the top of the first flight reaches the gallery.
   - When the ledges retract, the fall is at most 7 blocks, onto moss.
3. **Pilgrim's niche.** A veiled panel in the east wall leads to the Wayfarer pedestal (`always_ready`).
4. **Anchor cache (optional).** A `pull_anchor` above a `puzzle_seal` in the west wall guards `chests/force_cache`.
5. **Sentinels.** 2 Watcher sentinels stand in alcoves flanking the gallery (7.7). They wake on approach within 6 blocks, never on a touch of the pedestal; a Light player (≥ +15) passes them while they bow.
6. **Diggers.** 2 or 3 of Vasch's paid diggers (crystal poachers) work the courtyard, so every visitor has a fight on the way in.

**Sith Tomb:**

1. **Gate.** The obelisk gate and wind-cut steps. The Imperial dig team works the camp at least 12 blocks away.
2. **Processional.** 4 `tomb_vent`s in the floor, 1 Tomb sentinel, and funerary urns along the walls.
3. **Burial chamber.**
   - An `urn_keeper` sits behind the sarcophagus dais, with 4 `echo_urn`s on plinths around the room.
   - 2 Tomb sentinels stand dormant.
   - The Sith pedestal stands in a 7×6×7 inner sanctum behind a `puzzle_seal`, which the keeper powers.
   - Sense within 12 of the keeper plays the order. The player pushes the urns in that order.
   - Positional subtitles ("An urn sings") keep the puzzle readable without sound direction.
4. **Pilgrim's niche.** Veiled tomb stone off the processional hides the Wayfarer pedestal.
5. **Rubble room (optional).** `collapsed_rubble` (Lift only) closes a side room holding `chests/force_cache` and 2 funerary urns.

**Wreck Field:**

1. **Hatch.** Veiled plating (skin WRECK) hides the cargo hold hatch.
2. **Cargo hold.** A cargo counterweight (`counterweight_stone` with `skin` WRECK) is pushed. It powers the hatch `puzzle_seal` in front of the Wayfarer pedestal compartment.
3. **Optional caches:**
   - a torn deck 5 blocks up (Force Jump) with `chests/wreck_field`;
   - a `pull_anchor` on a jammed locker seal with `chests/force_cache`.
4. **Surface guard.** The `SURFACE_GUARD` cell beside the hull is where `secure_site` contracts spawn their scavengers (5.5).

**Echo Shrine** (`site/echo_shrine_jedi`, `site/echo_shrine_sith`, `site/echo_shrine_wayfarer`; 15×14×15; one template per kind, placed whole by `RuntimeSites.placeSurface` with `buried` = 9):

- On the surface, a 5×5 broken arch over a 2×2 veiled hatch (veiled stone, found by Sense through its POI).
- A stair leads to a 9×6×9 chamber whose floor is 8 below the surface.
- Inside: one `counterweight_stone` powers the `puzzle_seal` in front of the pedestal of the shrine's kind.
- A `site_spawner` at the `GUARD` anchor holds the guard: 1 Watcher sentinel (Jedi shrine), 1 Tomb sentinel (Sith shrine) or 2 scavenging poachers (Wayfarer shrine).

### 3.5 Outposts (W1, `world.outpost`)

**Worldgen:**

| Structure | Biome tag | Set |
|---|---|---|
| `republic_outpost` (single piece `outpost/republic_post`, 23×12×23, `adapted_pool_element`) | plains, sunflower_plains, meadow, forest, birch_forest, taiga, savanna | `oldrepublic:outposts` (weight 1) |
| `imperial_outpost` (`outpost/imperial_post`, 23×14×23, `adapted_pool_element`) | desert, badlands, wooded_badlands, savanna_plateau, windswept_savanna, dark_forest, snowy_plains, taiga | `oldrepublic:outposts` (weight 1) |

Both outposts share the structure set `oldrepublic:outposts`: spacing 40, separation 16, salt 734214, one `exclusion_zone {other_set: oldrepublic:ruins, chunk_count: 6}`. Being in one set already keeps the two outposts apart.

**Runtime fallback.** `Outposts.ensureNear(player)` runs when Chapter Three enters SUMMONS, again on every join at SUMMONS or later, and at most once per 1,200 ticks while the journal is held (from W2's journal-hold tick; no extra payload). For each army it asks the pure rule `Outposts.decide(OutpostQuery)` (a test seam, 15.1), which returns WORLDGEN, REUSE, PLACE or CAP_NEAREST:

1. If `StructureLocator.find` returns a worldgen outpost within 640 blocks (`sites.outpostWorldgenRadius`), that outpost is used.
2. Otherwise, if `WorldSites.nearest` returns a runtime outpost within 512 blocks (`sites.outpostReuseRadius`), it is reused.
3. Otherwise a new outpost is placed with `RuntimeSites.placeSurface`:
   - rings at 112, 128 and 144 blocks from the player, with 16 angles each, in loaded chunks only; fallback rings at 80 and 96. (A default dedicated server keeps about 160 blocks loaded around a player, so rings beyond 144 would almost never pass. Placement never calls `getChunk` synchronously.)
   - footprint 23, clearance 27, slope at most 3;
   - at least 128 blocks from any other outpost, natural ground only, no `#built_blocks` and no worldgen structure piece in the clearance (3.6), and outside spawn protection (`story.spawnProtectionRadius`).

   The placement is recorded in `WorldSites`.
4. There are at most 8 runtime outposts per world (`sites.maxRuntimeOutposts`). At the cap, the nearest existing outpost is used regardless of distance.

With `story.placeStructures=false`, the runtime outpost places only its `bounty_terminal` block and the entities.

**Contents (worldgen and runtime alike):**

- the `bounty_terminal` (the outpost's anchor);
- one officer (7.4): shared, not owned, invulnerable except to `/kill` and the void;
- a garrison of 4 persistent troopers that stay within 12 blocks of the terminal:
  - Republic: 2 riflemen, 1 field medic, 1 sergeant;
  - Imperial: 2 riflemen, 1 grenadier, 1 sergeant;
- a requisition rack (decor);
- a medbay (Republic) or a holding cell (Imperial), used by mission scenes.

The terminal spawns the officer and garrison the first time its loading rule passes (3.3). It replaces a missing officer at once (after the two-check rule) and one missing garrison trooper every 12,000 ticks, the latter only while no player is within 48 blocks.

**Look:**

- **Republic Forward Post:** off-white `republic_panel` and smooth stone, navy banners with an original chevron-and-star emblem (banner pattern `oldrepublic:republic_chevron`, W1), sandbag walls, a comms mast with a slow blinking lamp (a steady lamp when flashing effects are off).
- **Imperial Listening Post:** blackstone, polished basalt and deepslate tile, crimson trim, an angular sensor spire, a gate with a crimson-lit lintel.

**Officer names** are seeded from the outpost origin (`Math.floorMod(origin.hashCode(), 6)`) and use the frozen lang keys `npc.oldrepublic.officer.<republic|imperial>.<0-5>` (W2's `quest.json`), which W4's `spawnOfficer` sets as the custom name. Dialogue lines never contain the name; the speaker plate reads it from the entity (the dialogue engine's new `"speaker": "entity"` option, W2), and the officer portrait is one per army.

- Republic: Sergeant Joss Arvett, Lieutenant Mara Oddley, Sergeant Tobin Reyce, Captain Edda Volk, Sergeant Hal Brandt, Lieutenant Corin Ashby.
- Imperial: Lieutenant Ilse Varnholt, Captain Dren Malcor, Lieutenant Severin Dask, Major Ottilie Strand, Lieutenant Garrick Lenthe, Captain Henrike Saal.

### 3.6 Per-player runtime sites

All of these use `RuntimeSites`, which the lead builds in Phase 0 as real code. It generalises the M2 pair `MeetingSites.find` and `StoryStructures.place` / `adaptTerrain`, adding rotation, sunken placement and the `SitePiece` record.

Every site is owned by one player and follows the M2 rules, tightened:

- natural ground only, outside spawn protection, terrain adapted;
- **no `#built_blocks` and no worldgen structure piece** anywhere in the clearance box (for sunken parts, the box plus any shell): the piece check uses `StructureManager#getAllStructuresAt` per touched chunk, then the starts' piece boxes intersected with the clearance (the precise check below), not just corner samples;
- adaptation and shells never fill an air or fluid cell that touches a `#built_blocks` block, so a player's room or a neighbouring site is never filled in;
- at least 48 blocks from other players' story and mission sites (the M2 separation list is extended with these sites);
- entities spawn the first time the owner comes within 48 blocks;
- the blocks stay after the mission ends, like M2's raider camp.

All rings are measured from the player and capped at 144 blocks, for the loaded-chunk reason in 3.5.

| Template | Owner | Size | Rings / fallback | Contents |
|---|---|---|---|---|
| `story/couriers_fall` | W2 | 15×9×11 | 64/80/96 from the player; 32/48 | Vasch's crashed courier shuttle with its cockpit torn open, a scorched furrow, the Wayfarer pedestal (`always_ready`) in the cargo bay, a strongbox with his payments and his sealed note (read through the pedestal dialogue), 2 crystal poachers. The 3 caches go 12 to 24 blocks from it. |
| `site/echo_shrine_<kind>` | W1 | 15×14×15 | 96/128 from the player; 48/80 (placed whole, `buried` = 9) | 3.4 |
| `mission/patrol_ambush` (R1) | W2 | 13×7×13 | 96/128/144 from the player; 48/80 | a broken Republic speeder-sled (decor), 3 downed Republic troopers, 3 bound Watcher sentinels, a supply crate |
| `mission/smuggler_cache` (R2) | W2 | 13×8×13 | same | a camouflaged dugout, 4 crystal poachers, the smuggler chief (a `poacher_captain`: Rell Okkar, or Hessa Dray or her lieutenant by the Chapter Two outcome, 5.4), a locked crate holding the `archive_record` |
| `mission/fence_camp` (I1) | W2 | 15×8×15 | same | the fence's tent and campfire, 4 crystal poachers, the fence Dovan Lisk (a `poacher_captain`), the `fence_book` |
| `mission/survey_post` (I2) | W2 | 15×8×15 | same | a wrecked Imperial survey post, a Republic recon squad (2 riflemen, medic, sergeant) tagged `incident`, 1 downed Imperial trooper |
| `mission/raider_camp_small` (`clear_camp`) | W2 | 11×7×11 | same | 3 crystal poachers, or 4 at level ≥ 9 |
| `mission/skirmish_line` (`patrol_intercept`) | W2 | 17×6×9 | same | a sandbag line and crates; a squad of the other army tagged `incident` |
| `boss/reliquary_surface` + `boss/reliquary_hall` | W5 | surface 17×16×17; hall 31×18×31 | 96/128/144 from the player at convergence; 48/80 | 8.2 |

**Reliquary placement** (W5, `ReliquarySites.place`, with the pure check `ReliquarySites.evaluate`, 15.1):

- The surface template sits on the ground: its lowest 9 layers (the spiral descent) are underground and its top 7 (the tower stump) are above it.
- The hall goes directly below, with its top at the stand height minus 9, so the descent opens into the antechamber.
- Extra checks, over the **hall plus its 2-block shell**:
  - no worldgen structure piece intersects it: for each chunk the box touches, `structureManager().getAllStructuresAt(pos)`, then `startsForStructure(...)`, then `piece.getBoundingBox().intersects(box)` (so a mineshaft corridor crossing the interior is caught; corner samples are not enough);
  - it contains no `#built_blocks`;
  - it stays above `minY + 8`;
  - all its chunks are loaded.
- A candidate that fails moves to the next one.
- **Order and pacing:** the shell goes in first (fluid cells replaced with `Block.UPDATE_CLIENTS` only, so no fluid ticks are scheduled), then the hall Y-slab by Y-slab over 2 to 4 ticks, then the heart last, so nothing starts half-built and no single tick places 17,000 blocks. The shell never replaces a cell that touches a `#built_blocks` block.
- If no site passes, placement retries every 1,200 ticks. An operator can force it with `/oldrepublic c3 reliquary <player>`.

### 3.7 Environment treatment and discovery

`SiteTracker` (W1) generalises `CaveTracker`:

- Every 20 ticks per player, it finds the site piece the player stands in, using `StructureLocator.pieceAt` with a per-player cache.
- When that changes, it sends the `site_presence` payload, which the client ambience (`WorldAmbience`) uses.
- It reports presence by calling `QuestHooks.onSiteEntered` whenever the piece changes. W2 owns everything after that: the first-time-per-`SiteKind` state (`MissionData.discovered`), the discovery XP and the `site_discovered` toast event:
  - `JEDI_RUIN`, `SITH_TOMB`, `WRECK_FIELD`: 300 XP;
  - either outpost: 150 XP;
  - other kinds: none.

  A toast names the site ("Discovered: Watchers' Rest").
- It also kicks the tomb vents of a processional the player stands in (3.3).

`WorldAmbience` (W1, client) plays every site bed below, including the Reliquary's; W5 only supplies the `ambient.reliquary` sound and its particles. Each bed stops when the player leaves the site, dies, changes dimension or disconnects.

| Site | Bed (loop) | One-shots | Particles (through `Vfx` budgets) |
|---|---|---|---|
| Watchers' Rest | `ambient.jedi_ruin`: wind through stone and a low, distant choral pad | `ambient.ruin_chime`: soft distant chimes every 15 to 40 s | `light_mote` drifting in sun shafts; `leaf_fall` in the courtyard; ceiling dust in hall light shafts; lumen moss motes |
| Sith Tomb | `ambient.sith_tomb`: a low drone and far-off settling stone | `ambient.tomb_whisper` every 8 to 20 s (subtitle "Whispers in the stone") | `tomb_dust` sifting from ceiling cracks; vents |
| Wreck Field | `ambient.wreck`: hull groans and wind in torn plating | `ambient.wreck_creak`: a metal creak every 10 to 30 s (subtitle "Hull creaks") | `conduit_spark` from `wreck_conduit`; smoke wisps |
| Outposts | `ambient.outpost`: generator hum, distant radio crackle, flag flap | | none |
| Reliquary (sound and particles W5, played by `WorldAmbience`) | `ambient.reliquary`: crystal hum and dripping | | `crystal_glint` (existing M1 particle, reused) |

**Processor lists:**

- `jedi_ruin_weathering`: `ruin_stone` becomes cracked 15% and mossy 20% of the time.
- `sith_tomb_weathering`: `tomb_stone` becomes cracked 20%.
- `wreck_weathering`: `wreck_plating` becomes scorched 30%.

### 3.8 Locating structures

`StructureLocator.find(player, kind, radiusBlocks, exclude, callback)`:

- **The radius is in blocks.** `findNearestMapStructure` counts its radius in placement cells, not blocks (M2's `WorldBalance` notes this), and its `skipKnown` flag is global, not per player. So the locator does not call it. It walks the random-spread placement cells of the kind's structure set itself, nearest first: `cells = ceil(radiusBlocks / (16.0 × spacing)) + 1`, capped at 4 rings; for each cell it takes `RandomSpreadStructurePlacement#getPotentialStructureChunk` and reads that chunk's starts with `getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true).getAllStarts()` (the `CaveLocator.heartOf` way), keeping starts of the kind's structure tag (`jedi_ruins`, `sith_tombs`, `wreck_fields`, `republic_outposts`, `imperial_outposts`, `crystal_caves`).
- It checks at most `sites.locatorCellsPerTick` (4) cells per server tick across all players, from a queue, so a search that finds nothing (the designed Echo Shrine case) costs a few ticks of small work, never a stall.
- It drops any result farther than `radiusBlocks`, and any start for which `exclude` returns true. Callers use `exclude` for per-player filtering, for example "this player already took this structure's holocron" (4.3).
- It turns a start into a `SitePiece` for its start piece; `StructureLocator.anchor(piece, anchor)` searches **every** piece of the same structure start (or the `WorldSites` record), so the `HEART` anchor in a hall is found from the courtyard.
- It caches the result per player, kind and filter for 600 ticks, or until the player moves 48 blocks (the `CaveLocator` pattern), and `cached(p, kind)` reads that cache synchronously.
- It finds runtime sites from `WorldSites` records and from the player's own `StoryData` positions.

Callers never block on a result: they act when the callback runs.

---

## 4. Holocrons

W3 owns this section (`com.oldrepublic.holocron`), apart from the pedestal block (W1) and the hoard plinth (W5).

### 4.1 Items and data

- Items: `jedi_holocron`, `sith_holocron` and `wayfarer_holocron`. Each stacks to 1, has rarity uncommon and cannot be crafted. They carry the component `oldrepublic:holocron` = `HolocronStamp(String id, UUID owner, int serial)`. The serial is the owner's current issue number for that id (`HolocronData.serials`); a copy with an older serial is **stale**: it does not answer, does not count for the carry bonus and cannot be returned (4.3, 4.9).
- Looks (original):
  - **Jedi:** a cube of pale stone with gold seams.
  - **Sith:** a pyramid of black glass with crimson seams.
  - **Wayfarer:** a worn brass sphere with a turning ring.
  - In the hand, the seams have a faint steady emissive layer.
- `holocron_shard`: a sliver of crystal bound in brass. It comes from echoes (4.7).

### 4.2 The holocrons

A player's holocrons of each kind come in the fixed order below, one per pedestal (4.3). The 12 world holocrons count toward the collection. The 3 Archive holocrons come only from the hoard (4.8), do not count, and cannot be returned (their fate was decided at the hoard).

| Order | Id | Kind | Gatekeeper (original) | Teaches | Lore |
|---|---|---|---|---|---|
| 1 | `wayfarers_compass` | Wayfarer | Tessaly Arn, a pilgrim of no Order | Independent: `force_sense` (at Courier's Fall). Others: `relic_lore` granted | the old pilgrim roads between the Orders |
| 2 | `wayfarer_pell` | Wayfarer | Pell of the Long Road | Independent: `force_lift`. Others: `trailwise` rank 1 granted | walking without a master |
| 3 | `wayfarer_osk` | Wayfarer | Navigator Osk Brevan | `quiet_step` granted | a scout who charted this sector before the war |
| 4 | `wayfarer_maelis` | Wayfarer | Cartographer Maelis Dunn | lore only | old maps; where the watchtower over the sinkhole once stood |
| 1 | `jedi_morrane` | Jedi | Master Aveth Morrane, founder of the Watch on this frontier | `watchers_insight` | why the Watch keeps its records |
| 2 | `jedi_sarrow` | Jedi | Ione Sarrow, a healer of the old war | `mending_breath` | the cost of the war |
| 3 | `jedi_ketterin` | Jedi | Knight Ruan Ketterin | lore only | the war, the Treaty, and what the Watch lost |
| 4 | `jedi_vanterre` | Jedi | Archivist Sela Vanterre, Vasch's teacher | lore only | Vasch as a young Archivist: *"He loved the Archive more than the people it was for."* |
| 1 | `sith_zarvos` | Sith | Lord Hadrek Zarvos | `tomb_hunger` | the tombs as storehouses of power |
| 2 | `sith_ysolde` | Sith | Ysolde the Unbowed | `crackling_grasp` | strength as the only law |
| 3 | `sith_thrane` | Sith | Overseer Calix Thrane | lore only | what the Empire wants from the frontier |
| 4 | `sith_torr` | Sith | Ilvane Torr, Keeper of the Sealed Dead | lore only | the Sith sealed these tombs themselves, and someone broke the seals |
| hoard | `archive_watch` | Jedi | Vasch's annotations on the Watch Archive | `archivists_ward` (if kept) | |
| hoard | `archive_codex` | Sith | the codex Vasch took from Lord Zarvos's tomb | `archivists_hunger` (if kept) | |
| hoard | `archive_unbound` | Wayfarer | Vasch's own voice | `archivists_road` (if kept) | |

If the player already owns a node that a holocron would grant, they get +1 SP instead.

### 4.3 Pedestals and finding

- **Who can use them.** Pedestals answer only awakened players whose Chapter Three stage is at least SUMMONS (`ChapterThree.stage(p)`, 15.1). Anyone earlier gets "The stone does not know you yet." This stops a player from breaking the story's sequence. A pedestal also refuses while an awake creature hostile to the player (`Attitudes.toward` HOSTILE, or a squad that is provoked or engaged, 5.2) is within 16 blocks: "Something still stirs here." (the Chapter One meditation rule). Dormant sentinels do not count, and touching a pedestal never wakes anything.
- **One holocron per pedestal per player.** `HolocronData.attuned_at` records, for each world holocron, the pedestal that gave it (dimension, position, the structure start or site record it belongs to, and the id; at most 16 entries). A pedestal never gives the same player a second world holocron, so collecting a kind means finding four chambers.
- **Using a pedestal** calls `HolocronService.usePedestal(player, pos, kind)`. In order:
  1. If this pedestal has not yet given this player a world holocron and the player still has an unattuned world holocron of this kind, they get the next one in the 4.2 order, with the attunement moment (4.4). The `attuned_at` entry is written when the choice is confirmed.
  2. If they have all 4 of this kind and have not yet claimed an echo from this pedestal, they get the echo (4.7).
  3. Otherwise: "The echo has already spoken to you here."
  4. **Sneak-use restores** one lost holocron of this kind, with no rewards, once per 24,000 ticks per player. Eligible ids are attuned, **not** in `returned`, **not** chosen BREAK, and, for the Archive ids, only when the hoard choice was KEEP. The first eligible id in 4.2 order (Archive ids last) is re-issued: `serials[id]` goes up by one and the new item carries it, so every older copy, in a chest, an ender chest or another player's hands, becomes stale (4.1). Restoring never creates a second working copy.
- **Courier's Fall** holds only the Compass: its pedestal block entity carries `fixed_id = wayfarers_compass`, so it never gives Pell or a later Wayfarer holocron; once the Compass is attuned it gives only the echo. An Echo Shrine follows the general rule: one holocron per player.
- **Sharing.** Every player can take one holocron from every pedestal, so a shared world chamber never runs dry for a newcomer, and nobody can take a holocron from another player.
- **Guidance.**
  - Each Sense cast includes the direction and closeness of the nearest structure whose pedestal of a needed kind has not yet given this player a holocron: `StructureLocator.find` with an `exclude` filter built from `attuned_at` (the locator is per player; `findNearestMapStructure`'s `skipKnown` flag is global and is not used). The search covers 256 blocks, or 512 with Seeker's Sight. It sends direction only, never a position. The cast uses the cached result at once and sends a follow-up pulse when a fresh result arrives (10.1).
  - During FIRST_ECHO the journal's Calling (variant `HOLOCRON`) points to the first-echo target continuously.
  - After an Interrogate contract choice (5.5), it points to the nearest unused chamber for 12,000 ticks.
- **The first-echo target** is `StructureLocator.find(kind, 768, unused)`. If nothing is found, W1's `EchoShrines.place(owner, kind)` places an Echo Shrine of that `HolocronKind`, and its position is appended to `Chapter3.shrines`.
- **Old-world guarantee.** In an explored M2 world, new structures appear only in new chunks. So if the chapter is at LEADS or later, the player's holocron count for rank 3 (2 of their own kind) or the Pathfinder (3 of any kind) is still unmet, and no unused pedestal of a needed kind lies within 768 blocks, the next journal hold places one more Echo Shrine of that kind and the Calling points to it. This happens at most once per kind (`chapter_three.extraShrinesPerKind`), so `Chapter3.shrines` holds at most 4 positions (the first echo plus one per kind).

### 4.4 Attunement moment

1. The server sends `holocron_attune` around the pedestal (variant = kind). The holocron rises 0.5 blocks out of its cradle over 30 ticks, its seams split with thin light, and dust in the alcove drifts upward. There is no ring and there are no glyphs. Sounds: `holocron.rise`, then `holocron.open`.
2. 30 ticks later the server sends `holocron_gatekeeper` to the player (variant = gatekeeper index). The figure is **projected from the holocron's opened seams**: a narrow spill of light from the seams widens into a thin, slightly translucent standing figure 2 blocks beyond the pedestal, facing the player, tinted by kind (warm gold, rust crimson or brass), with faint horizontal banding (steady, never flickering, when flashing effects are off). Drifting motes appear only at its edges while it assembles and folds. Only the attuning player sees it. It reuses the M1 Trial of Reflection figure renderer, with `holocron.gatekeeper_form` and the loop `holocron.hum`.
3. The server opens the speakerless dialogue `holocron_<id>` (`Dialogues.open(p, id, null)`): 3 to 5 subtitled lines, the gatekeeper portrait, and the options in 4.5. The first attunement after the player reaches 2 leads also plays Vasch's recorded message as the last line (a `vasch_message` node, once per player, 6.4).
4. When a choice is confirmed, rewards are granted once, guarded by `HolocronData.attuned`. The owner-bound item is given, or dropped for the owner alone if the inventory is full (the `CrystalGrant` pattern). The figure folds back with `holocron.gatekeeper_fade`.
5. If the dialogue closes with no choice (Escape, disconnect, death; W3 listens on the new `Dialogues.ON_CLOSE` event, 15.1), or the player takes damage while it is open (the dialogue is closed for them), nothing is attuned and nothing is consumed. The holocron sinks back, and the player can try again at any time.

**First-attunement rewards** (once per id per player):

- 600 XP (+25% with `relic_lore`);
- Resonance milestone `holocron_<id>`, +120;
- codex entry `holocron_<id>`;
- the teaching from 4.2;
- the lead `holo:<id>`, if Chapter Three is at FIRST_ECHO or later;
- the skill point drip and milestones (4.7);
- a banner, e.g. "Holocron attuned · Master Aveth Morrane".

### 4.5 Opposed-side holocrons (alignment choices)

| Reader | Option | Effect |
|---|---|---|
| Own side, or a Wayfarer holocron | "I will remember." (`HolocronChoice.OWN`) | as 4.4 |
| Jedi path, Sith holocron | **Seal it** (`SEAL`) | light MAJOR +3 (`holocron.<id>.seal`). Lore, XP and Resonance. The node is **not** granted. The item is given sealed and can be returned to Saelen. |
| | **Listen** (`LISTEN`) | dark MAJOR -4 (`holocron.<id>.listen`). Lore, XP, and +80 extra Resonance. The node is granted, and stays **dormant** under the M2 rules while the player does not meet its alignment requirement: "The lesson waits in you." |
| Sith path, Jedi holocron | **Break it open** (`BREAK`) | dark MAJOR -3 (`holocron.<id>.break`), +80 extra Resonance, Sorn trust +10. No node. The pale stone splits along its gold seams and the item is destroyed (it still counts as attuned, and can never be restored). |
| | **Listen** (`LISTEN`) | light MAJOR +4. The node is granted and stays dormant while Dark. |
| Independent, any kind | **Listen** | No shift. The node is granted and follows its alignment rules. |

### 4.6 Return or carry

**Return.** A mentor or officer dialogue option ("I brought you something from the ruins.") calls `HolocronService.returnTo` and consumes one holocron item. Only the player's own, current-serial world holocrons can be returned; Archive holocrons cannot. When several are eligible, the first in 4.2 order (Jedi, then Sith, then Wayfarer rows) is taken, and the dialogue line names it before the player confirms. Each holocron id can be returned once (`HolocronData.returned`), and the knowledge and nodes stay with the player. A returned id can never be restored (4.3).

| Holocron | Returned to | Reward |
|---|---|---|
| Jedi holocrons, and Sith holocrons the player sealed (Jedi path) | Saelen | trust +10, Jedi Order +40 |
| Sith holocrons (Sith path) | Sorn | trust +10, Sith Empire +40 |
| Any world holocron | a Republic officer (Wary or better) | Republic +40, 1 `republic_commendation` |
| Any world holocron | an Imperial officer (Wary or better) | Sith Empire +40, 1 `imperial_commendation` |

Returning to a Wary officer is the "Make amends" route out of Distrusted (5.3).

**Carry.** Each distinct current-serial holocron carried in the main inventory adds +4% Force regen, up to 3 (+12%), through `ForceModifiers`. Stale copies and holocrons in the ender chest do not count. W3's `ForceModifier` computes this live from a per-player inventory hash refreshed every 20 ticks (Fabric has no inventory-change event). Which kinds count:

- Jedi: Jedi and Wayfarer holocrons;
- Sith: Sith and Wayfarer holocrons;
- Independent: every kind.

The Holocrons tab shows the trade-off ("Carried: +8% regen (2/3)"). Returning a holocron trades a small permanent power for standing toward rank 3.

### 4.7 Progression from holocrons

- **Skill point drip:** +1 SP for every second world holocron attuned (the 2nd, 4th and so on up to the 12th): up to 6 SP.
- **Collection milestones** (world holocrons only, each granted once):

  | Attuned | Reward |
  |---|---|
  | 3 | Resonance `holocrons_3`, +150 |
  | 6 | +1 SP |
  | 9 | Resonance `holocrons_9`, +250 |
  | 12 | The title **Keeper of Echoes** (`Identity.recordChoice(p, "title.keeper_of_echoes")`, shown on the Standing tab) and Resonance `holocrons_12`, +400 |

- **Echoes.** Once a player has attuned all 4 world holocrons of a kind, each pedestal of that kind gives them an echo once: 150 XP and 1 `holocron_shard`. A random gatekeeper of that kind speaks a short line, with the effect `holocron_echo`. The pedestals that gave the 4 holocrons count too.
  - Claimed pedestals are kept in a list of at most 64 per player; when it is full the oldest entry is dropped.
- **Shards.** At any officer, "Trade two echo shards" exchanges 2 shards for 1 commendation of the officer's army. This gives no reputation. At a Wary officer, "Make amends" takes 2 shards for +40 standing and no commendation (5.3).

### 4.8 The Archive (hoard choice after the boss)

When the boss is resolved, the `hoard_plinth` (W5) opens. The owner uses it, and so does each co-op helper with pending credit (8.9). It calls `HolocronService.openArchive(player, pos)`, which opens dialogue `archive_hoard` (W3). One decision covers all three Archive holocrons. Every choice gives 300 XP and a journal entry, fires `QuestHooks.onArchiveResolved`, and plays the `archive_choice` effect (13.3). The choice is stored only in `HolocronData.archive`.

- **Return:** the three holocrons dim and settle into a travelling case with a dust exhale and a closing clasp (`holocron.archive_return`).
- **Keep:** their seams warm, and motes drift from them into the player (`holocron.archive_keep`).
- **Unmake:** the three holocrons' seams flare, light drains out of them as dust, the shells crack and slump, and a low resolving sting follows (`holocron.unmake`). No ring, no flash (a steady glow with flashing off).

| Choice | Effect |
|---|---|
| **Return the Archive** | **Jedi:** Jedi Order +150, Republic +60, Saelen trust +20, light MAJOR +5 (`archive.return`). **Sith** ("Surrender it to the Empire"): Sith Empire +150, Sorn trust +20, no shift. **Independent** ("Scatter it back to the ruins"): Frontier +100, Republic +60, light MAJOR +3. All three codex entries; no nodes; no items. |
| **Keep it** | Three owner-bound Archive holocron items. All three `archivists_*` nodes are granted, subject to dormancy. Jedi: dark MAJOR -6 (`archive.keep`, "attachment"). Independent: dark -2. Sith: no shift. The carry bonus applies. |
| **Unmake it** | Resonance milestone `archive_unmade`, +400. Light MAJOR +3 (`archive.unmake`). Jedi Order -50 and Sith Empire -50: both wanted it. Codex `archive_unmade`. |

### 4.9 Holocrons tab, ownership and duplicates

- **Holocrons tab.** `HolocronShelfPanel` (W3) is registered through `JournalTabs` and shows:
  - a 3×4 shelf (rows Jedi, Sith, Wayfarer) and 3 Archive slots below it once the hoard is resolved;
  - lit or dark silhouettes, the gatekeeper's name, what each holocron taught, and its status: Returned, Carried, Sealed or Broken;
  - one hint per kind for unfound slots: "Jedi ruins rest in old forests", "The Empire's dead sleep under sand and red stone", "Pilgrims left their roads where ships fell";
  - the progress ("5/12"), the carry bonus and the shard count.
- **Using a holocron item.** For its owner, a current-serial item replays the gatekeeper's lines (dialogue `holocron_<id>` in replay mode, with only "Close") and never grants anything again. A stale copy: "It is silent. Another copy answers you now." For anyone else: "It does not answer you."
- **Duplicates are harmless.** Every reward is a flag in `HolocronData`, the carry bonus counts distinct current-serial ids, and a restore makes every older copy stale.

---

## 5. Factions, reputation, missions and ranks

### 5.1 The Republic faction

- `Faction.REPUBLIC` (id `republic`) is appended as ordinal 3. Reasons:
  - Republic troopers need a standing of their own, separate from the Jedi Order. The Order is not the army, and keeping them apart keeps alignment separate from faction, as the brief asks.
  - Independents need a non-Force faction to work for.
  - The Ledger's "Republic credits" thread needs somewhere to go.
- **Seed** at Chapter Three SUMMONS, one-shot (`rep:republic:seed`): Jedi path +100, Sith path -100, Independent 0. Imperial troopers read the existing `sith_empire` reputation.
- **Standing tab:** four faction rows (Jedi Order, Sith Empire, Republic, Frontier), a Republic sprite (an original blue-white chevron and star), a titles row (Pathfinder, Keeper of Echoes), and the rank 3 requirements as a checklist.
- `IdentityStyle` gets a REPUBLIC case with accent `0xFF8FB8E8`.

### 5.2 Attitudes and kill reputation

The trooper rule is registered by W4 in `combat.Attitudes`, a Phase 0 contract. Everyone else uses it, through `Attitudes` or `ForcePowers.isHostile` (the lead moves every existing `instanceof Enemy` check onto these in Phase 0, 15.1):

- powers' targeting and area filters;
- kill XP, mastery and quest credit;
- contextual alignment.

| Tier with the trooper's army | Attitude | Behaviour |
|---|---|---|
| Hostile (≤ -500) | `HOSTILE` | Attacks on sight within 24 blocks. |
| Distrusted (-499 to -100) | `WARY` | The leader barks a warning when the player comes within 16 blocks ("That's close enough."); the warning repeats at most every 600 ticks. After a warning, the squad **opens fire** if the player stays within 8 blocks for 120 ticks or lights a saber within 8 blocks; it is then **engaged** with that player. Hitting a squad member instead makes it **provoked** (below). |
| Neutral | `NEUTRAL` | Ignores the player. A "Move along" bark if the player is within 4 blocks with a lit saber. Hitting any squad member makes the squad **provoked**. |
| Trusted or Honoured | `ALLIED` | A salute bark. Attacks whatever damages the player within 16 blocks, including the other army's troopers, which starts an incident (7.5). Its bolts pass through any player it is Allied with (`BlasterBolt.canHitEntity` asks `Attitudes`), so it never hits the player it defends. |

**Who started it.** Each trooper keeps two per-player expiries, saved as game times (`Provoked` and `Engaged`, player UUID → expiry, at most 4 entries each, 1,200 ticks):

- **Provoked:** the player struck first, hitting a trooper that did not count as hostile to them at that moment (an aimed power counts as a hit). The squad fights that player while it lasts. For kill reputation, XP and alignment a provoked squad still counts as **not hostile**: the player chose the fight.
- **Engaged:** the squad fired first: a Wary squad after its warning, a squad attacking the player while defending its own ally, or an incident squad whose mode includes the player. This counts as **hostile**, so self-defence is never punished.

`Attitudes.countsAsHostile(p, e)` is true for the HOSTILE tier, an engaged squad, or an incident squad whose mode includes `p`; never for provoked alone. **Area powers and aim assist** (`Attitudes.shieldedFromArea(p, e)`) skip any trooper that does not count as hostile and that `p` has not provoked: Allied, Neutral and Wary bystanders are never caught by a Push cone, a Lightning chain, a Jump crush or aim assist. A directly aimed power still hits them, and that hit provokes.

**Overrides:**

- Provoked and engaged beat any tier while they last.
- An **incident** squad (spawned by a mission or contract, or tagged by an ally defence) carries an `IncidentMode`: `ARMY` makes it hostile to the other army; `ARMY_AND_OWNER` also makes it engaged with the mission's owner from the start (I2 against an Independent the Republic trusts; `patrol_intercept`). The mode and expiry are saved on each trooper.
- Troopers attack poachers (Allegiance `CREW`) and bound sentinels (`ANCIENT`, and `ARCHIVE` in the Reliquary) on sight. They never attack settlers.
- **Peaceful:** the trooper rule returns at most `NEUTRAL` toward players, and provoked, engaged and incident never apply to players. Troopers still fight poachers and sentinels; patrols do not spawn (7.6).
- The uniform disguise, if it ships, shifts the tier one step (9.5).

**Kill reputation** (W2 applies all of it, including the cap and `KILLED_SOLDIER`, in `QuestService.onTrooperKilled`; W4 only fires the hook with `wasHostile` from the snapshot below):

| Killed trooper | Effect |
|---|---|
| Counted as hostile toward the killer when the killing damage landed (HOSTILE tier, engaged, or incident toward them) | -10 with its army; kill XP is paid |
| Not hostile (Neutral, Wary or Allied, **including a squad the killer provoked**) | -40 with its army and `ContextAct.KILLED_SOLDIER` (MINOR dark -2, inside the M2 minor daily cap); no kill XP |

- The daily cap of 200 per army applies **only to the -10 losses**. The -40 losses are never capped, so killing peaceful soldiers always costs.
- No kill ever **gains** reputation.
- **Kill XP** is paid by the lead's `Progression.onDeath` only when `Attitudes.hostileSnapshot(victim, killer)` is true. The lead's damage hook takes that snapshot (`countsAsHostile` at the moment a player's damage is applied), so the answer reflects the fight, not the corpse. Non-trooper `Enemy` mobs keep the M1 rule through the `Attitudes` fallback.
- Example: a Jedi lights a saber near a Wary Imperial patrol, is shot after the warning, and fights back. Each kill costs -10, moves no alignment and pays XP (the M2 principle that ordinary self-defence never moves alignment). A Jedi who opens fire on a Neutral patrol pays -40 and a dark shift per kill, and earns no XP.

### 5.3 Givers

| Giver | Where | Gate | Offers |
|---|---|---|---|
| Master Saelen / Overseer Sorn | the mentor camp (owned) | own path | Chapter Three lesson and report; one mentor duty per in-game day; holocron return; the rank 3 ceremony |
| Republic officer (one of 6 names, 3.5) | Republic outpost (shared) | anyone can talk; missions need Republic ≥ Neutral | R1, R2; holocron return; shard exchange; "Make amends" at Wary; the Independent report (any tier above Hostile) |
| Imperial officer | Imperial outpost (shared) | anyone can talk; missions need Sith Empire ≥ Neutral | I1, I2; holocron return; shard exchange; "Make amends" at Wary; the Independent report (any tier above Hostile) |
| `bounty_terminal` | each outpost | per issuer: Republic contracts need Republic ≥ Neutral, Imperial ones Sith Empire ≥ Neutral, Frontier ones Frontier ≥ Neutral | 2 offers per in-game day (5.5) |

**Officer dialogues** are `officer_republic.json` and `officer_imperial.json` (W2). The speaker plate takes the officer's name from the entity. The `start` node routes on the reputation tier:

- Hostile: "You've got nerve showing your face here." The only option is to leave.
- Wary: short lines and no work, but three options: **"Make amends"** (return a holocron through `holocron:return`, or hand over 2 echo shards through `shards:amends`: +40 standing each, so any player can climb back to Neutral), **report** (Independent, in AFTERMATH: the report is a story action, not work), and leave.
- Neutral and above, up to 6 options: work, about the patrols (lore), return a holocron, trade shards, report (Independent, in AFTERMATH), leave.

**Mentor dialogues** (`mentor_jedi.json`, `mentor_sith.json`, W2):

- The `complete` node gains one option, `c3` ("About the Collector…"), which leads to a hub node `c3_hub`. This leaves one slot free in `complete`.
- `c3_hub` holds at most 6 options: the lesson, the report, "Any duty for me?", "I brought you a holocron.", "I am ready." (the ceremony, showing a reason if the player is not ready), and "Back."

**Effect of the existing reputation on access:**

- A Jedi starts at Sith Empire -100 (Distrusted), so Imperial missions and contracts stay closed until they make amends at an Imperial officer (one returned holocron or 2 shards lifts them to Neutral). This is meant to be a deliberate step, not the normal route: each path's work is with its own army.
- A Sith is likewise shut out of Republic work by the seed, and can make amends the same way, or earn Republic +40 by sparing the sergeant in I2.
- Independents start Neutral with both armies and can take all four story missions.
- **Nobody is stuck.** The Independent report needs only an officer above Hostile. If both armies are Hostile to an Independent, the journal offers "Send word" in AFTERMATH, which completes the report with Frontier +50 instead of army standing.

### 5.4 Story missions

Each story mission:

- runs once;
- is available from SUMMONS (R2 needs R1 done; I2 needs I1 done);
- is a Chapter Three lead, whatever the outcome;
- pays 1,200 XP plus the reputation listed below;
- pays 2 commendations of the giver's army unless the table says otherwise;
- gives a journal entry, the `mission` banner (`lesson_complete` variant 4) and the sound `mission.complete`.

Choices are made by the owner only and are recorded with `Identity.recordChoice`. **The per-choice values below live in the mission JSON** (`data/oldrepublic/mission/<id>.json`, W2), not in `balance.json`; section 14 lists only the shared mission keys. **For a Jedi**, every Republic reputation gain from Republic work (these missions and Republic contracts) also pays half as much Jedi Order standing (`missions.jediOrderShare` 0.5: the Watch hears of it), so a Jedi's rank faction grows from army work as a Sith's does.

| Id | Giver | Brief | Choices | Extra rewards |
|---|---|---|---|---|
| `r1_silent_patrol` "Silent Patrol" | Republic | A patrol went silent near an old ruin road. At `mission/patrol_ambush`, defeat 3 bound Watcher sentinels, then decide the wounded. | **Tend them:** kneel over each downed trooper for 60 ticks (sneak-use), or channel Force Heal within 4 blocks to revive at once (both through `Troopers.tend`, with the `trooper_tend` effect). Each tended trooper stands and walks home. Tending all 3 gives Republic +100 and `HELPED_OTHER` ×3 (MINOR light). **Take the supply crate and go:** confirmed in dialogue `r1_leave_confirm` ("Leave them?"). 6 durasteel (loot `gameplay/r1_supply`), Republic -50, `CRUELTY` (MINOR dark). | entry `silent_patrol` |
| `r2_credits_from_the_core` "Credits from the Core" | Republic | Republic credit chits stamped with the Watch Archive's vault seal (relief funds the Watch held, stolen with the Archive) lead to a smuggler cache. At `mission/smuggler_cache`, recover the stolen `archive_record`. The chief yields at 25% (M2 yield rules). | **Return** it to the officer: Republic +100, light MAJOR +3 (`mission.r2.return`). **Sell** it to an Imperial officer: Sith Empire +60, Republic -100, and 3 `imperial_commendation` instead of the Republic pair. **Keep** it: codex `vasch_records`, Resonance `vasch_records` +100, Republic -20. | entry `credits_from_the_core` |
| `i1_names` "Names" | Imperial | Intelligence wants the names of everyone who handled the crystals. At `mission/fence_camp`, take the `fence_book`. The fence Dovan Lisk yields at 25% (M2 spare or execute rules). | **Hand over the names:** Sith Empire +120, Frontier -150, dark MAJOR -6. **Burn them:** use the camp's fire (`FIRE` anchor) while holding the book, confirmed in dialogue `i1_burn_confirm`, or choose it at the officer: Sith Empire -60, Frontier +80, light MAJOR +6, no commendations. **A doctored list** (only with flag `ledger_found`): Sith Empire +60, no shift, codex `doctored_list`. | entry `names` |
| `i2_border_incident` "Border Incident" | Imperial | A Republic recon squad crossed the treaty line and hit an Imperial survey post. At `mission/survey_post`, fight the squad (`IncidentMode.ARMY_AND_OWNER`: it fires on the owner whatever their standing, so each kill is a hostile kill, -10 Republic). Sergeant Aldo Brannick yields at 20% and opens dialogue `yield_border`. | **Spare** (he walks home): light MAJOR +4, Republic +40, Sith Empire -20. **Hand him over:** two Imperial troopers (escorts, `Troopers.spawnEscort`) arrive from the post's edge, bind his hands and walk him off; all three despawn out of sight or after 300 ticks; flag `mission.i2.handed_over`. Sith Empire +80, Republic -60, dark MAJOR -4. **Execute:** dark MAJOR -8, Sith Empire +40, Republic -100. Tending the downed Imperial (kneel 60 ticks): Sith Empire +20. | entry `border_incident` |

The Republic missions lean toward "protect" and the Imperial ones toward "dominate", but each has a real option the other way. Neither army pays better.

**Chapter Two cameos** (cheap, flag-driven, using existing entities; each is recorded in the journal as a line on the mission's entry):

- **Brenn spared** (flag `brenn_spared`, `StoryRules.BRENN_SPARED`; it can only be set on the Sith path, in the Trial of Ascension): Brenn Ostrak, now attached to Imperial Intelligence, stands beside the officer the first time the player visits an Imperial outpost (the M2 acolyte entity in `RIVAL` mode, non-combat, `noSave`, spawned for that player only, removed when the player leaves 64 blocks): a terse greeting from one apprentice to another. He is named in the I2 brief as the one who asked for the player. There is no Jedi or Independent variant (they never meet him in Chapter Two).
- **Hessa Dray spared** (`StoryData.captain == CaptainFate.SPARED`): she is the R2 smuggler chief instead of Rell Okkar. She yields at once and hands over the `archive_record` with a line about paying her debts; the choices are unchanged.
- **Hessa Dray executed** (`captain == EXECUTED`): her lieutenant runs the cache instead and fights to the end (no yield). Any other fate (`FLED`, `DIED`, `NONE`): Rell Okkar, as written.
- **Captives freed** (`StoryData.captives == CaptivesFate.FREED`): one of the freed settlers is at the Republic outpost and gives a lead line toward the nearest unused chamber (a 12,000-tick Calling, like Interrogate).

W2 owns `quest.story` and reads these existing `StoryData` fields and flags directly; their meaning and storage do not change.

### 5.5 Contracts (repeatable)

| Contract | Issuer | Objective | Site |
|---|---|---|---|
| `clear_camp` | Frontier (either terminal) | Defeat the crew. | `mission/raider_camp_small` |
| `survey` | Republic or Imperial | Reach a Jedi Ruin, Sith Tomb or Wreck Field this player has not surveyed, within 512 blocks, and cast Sense within 8 blocks of its `HEART` anchor (found with `StructureLocator.anchor`, which searches every piece of the structure). | worldgen (locator); offered only if one exists |
| `secure_site` | Republic or Imperial | Clear the nearest ruin, tomb or wreck within 384 blocks: at a ruin or tomb, destroy 3 reactivated (bound) sentinels; at a wreck, drive off 4 of Vasch's scavengers (crystal poachers). | They spawn at the site's `SURFACE_GUARD` anchor: Watchers at ruins, Tomb sentinels at tombs, poachers at wrecks. Offered only if a site exists. |
| `patrol_intercept` | Republic or Imperial | Stop the other army's patrol that crossed the line: defeat 3 troopers. Their sergeant yields at 25% and opens dialogue `yield_intercept`. | `mission/skirmish_line`, 96 to 144 blocks from the player; the squad is incident-tagged with `ARMY_AND_OWNER` |
| `watch_duty` (Jedi) / `tribute` (Sith) | the mentor | One of `clear_camp`, `survey` or `secure_site`, with mentor framing. | as the base type |

**The `patrol_intercept` yield choice** (dialogue `yield_intercept`):

- **Spare:** no shift.
- **Execute:** `ContextAct.KILLED_SOLDIER`.
- **Interrogate** (`yield:interrogate`, `YieldOutcome.INTERROGATED`): `CRUELTY` (MINOR dark). For the next 12,000 ticks, the journal Calling points to the nearest structure, within 1,024 blocks, with a pedestal that has not yet given the player a holocron. Then the sergeant is released and walks off.

**Offers and limits:**

- Each terminal offers 2 contracts per player per in-game day, seeded by the day index, the player UUID and the terminal position.
- `MissionData` records the board day and the offers already used that day (`used_offers`: terminal position and offer index, at most 16; cleared when the day changes). An accepted, completed or abandoned offer is never offered again that day.
- Mentors offer 1 duty per in-game day.
- A player has one active mission at a time, story or contract.
- Abandoning is free, but that offer is gone for the day.
- **Soft cap:** the first 3 completions per in-game day pay in full. Later ones pay ×0.25 XP and no currency or reputation. There are no real-world timers.
- Every daily rule takes the day as an explicit argument (`level.getDayTime() / 24000` at the call site), so tests can pass a day without touching world time.

**Rewards:**

- XP = min(900, 250 + 40 × level).
- Issuer reputation +25 (Frontier for `clear_camp`). For a Jedi, Republic contracts also pay Jedi Order +12 (`missions.jediOrderShare`, 5.4).
- 1 commendation of the issuing army. Frontier contracts pay the loot table `gameplay/frontier_contract` instead: 3 durasteel, 1 `lumen_thread` or `grave_silk`, and 2 to 4 emeralds.
- Mentor duties pay XP, trust +5 (`missions.dutyTrust`) and own faction +25 (`missions.dutyRep`), with no commendation.
- The first contract completed during LEADS adds the lead `contract`, once.

**Own-side contracts** count toward rank 3. They are kept in `MissionData` per issuer:

- Jedi: Republic contracts and Jedi duties;
- Sith: Imperial contracts and Sith duties;
- Independent: any.

**Variety:** each contract type has 3 briefing variants per issuing army (`mission.oldrepublic.contract.<type>.<issuer>.<n>`).

### 5.6 Commendations

- Items:
  - `republic_commendation`: a blue-white enamel chevron pin;
  - `imperial_commendation`: a crimson and black service bar.
- Sources:
  - 2 per story mission;
  - 1 per contract;
  - 1 per holocron returned to an officer;
  - 1 for every 2 shards exchanged.
- They never come from kills, chests or troopers.
- Commendations are ordinary items that can be traded. Their only use in M3 is crafting trooper armour (9.3).

### 5.7 Tracking and display

- **State:** the attachment `oldrepublic:missions` (`MissionData`, sealed, owner-synced; 12.1).
- **Definitions:** data files in `data/oldrepublic/mission/*.json`. Each lists:
  - id and kind (`story`, `contract` or `duty`);
  - issuer;
  - gate conditions (strings for the existing `DialogueConditions`);
  - objective type;
  - site template, counts and rewards;
  - lead flag, journal ids and text variant count.

  The objective types are Java: `clear`, `recover`, `rescue`, `survey`, `secure`, `intercept`, `yield`. Definitions load on start and on `/reload` and stay on the server.
- **Board screen** (`BoardScreen`, W2). The bounty terminal opens it through the `mission_board` payload. It shows 2 offer cards, each with a title, issuer emblem, objective, rewards, and a "Full pay 2/3 today" note. Buttons: Accept, or Abandon for the active mission.
- **Missions tab** (`MissionsPanel`, registered with `JournalTabs`). It shows:
  - the active mission and its objective;
  - the four story missions (done, available, or locked with the reason);
  - today's full-pay count;
  - own-side contracts toward rank 3.
- **HUD tracker** (`MissionTrackerHud`, W2):
  - placed top right, under the vanilla status effects, at most 160 px wide;
  - shows the mission title, the objective with its counter, a chevron rotated toward the target, and a band: **Far** (more than 160 blocks), **Near** (48 to 160) or **Here** (under 48);
  - the direction and band come from the `tracker_hint` payload every 20 ticks, and coordinates are never shown;
  - hidden while a screen is open, when `showHud` is false, or when `ClientConfig.showMissionTracker` is false.
- **Feedback:**
  - action bar progress ("Sentinels 2/3");
  - `mission.update`;
  - the completion banner (`lesson_complete` variant 4) and `mission.complete`.
- **Guidance** (Phase 0 `Guidance`, providers by W2). While the journal is held, only the highest-priority request is sent, one per interval:
  1. story targets (Courier's Fall, the first echo, the Reliquary): 100;
  2. the active mission: 50;
  3. the interrogation pull: 40;
  4. an unvisited outpost: 20.

  Sense pulses work separately (10.1).

### 5.8 Rank 3

**The trial.** The brief asks that faction ranks need missions and trials as well as levels. In M3 **the Collector fight is the rank 3 trial**: "Chapter Three COMPLETE" requires the CONFRONTATION to have been won (by the player or with co-op credit), and the mentor says so at the ceremony ("You faced him. That was your trial."). The contracts and story missions are the missions. `DECISIONS.md` records this reading (18).

| Rank | Requirements | Ceremony | Rewards |
|---|---|---|---|
| Jedi **Knight** | Chapter Three COMPLETE (the trial); level ≥ 11; Saelen trust ≥ 80; Jedi Order ≥ 500 (Honoured); alignment ≥ +15; ≥ 2 Jedi holocrons attuned; ≥ 3 own-side contracts | **The Knighting at the Watch.** At dusk at the camp the player kneels and Saelen touches her lit blade to each shoulder (`mentor_jedi` node `ceremony`; effect `rank_ceremony` variant 0). | `Identity.promote(p, 3)`, +2 SP, Resonance `second_rank` +700, 1,000 XP, the **Knight's robe** (`jedi_knight_robe`: counts as the robe of the Jedi set, same stats, its own texture and model with a mantle and sash), node `knights_vigil`, banner "Knight of the Watch", entry `knight_of_the_watch` |
| Sith **Lord** | The same, with Sorn's trust, Sith Empire, alignment ≤ -15 and 2 Sith holocrons | **The Naming.** Sorn reads aloud a sealed writ from an unnamed Darth who grants the title, names the player, then breaks a sealed tablet bearing their old name (effect `rank_ceremony` variant 1). An Overseer cannot make a Lord; he carries the word of one who can. | The same, with the **Lord's robe** (`sith_lord_robe`, counts as the Sith robe), node `lords_dominion`, entry `named_lord` |
| Independent **Pathfinder** (a title; the rank does not change) | Chapter Three COMPLETE (the trial); level ≥ 11; 3 holocrons of any kind; Trusted (`chapter_three.pathfinderTier`) with any faction; 3 contracts | When the requirements are met, the journal Calling (STORY) points to Courier's Fall. At its pedestal, Tessaly Arn's figure forms from the Compass as witness and the dialogue `pathfinder` opens: *"No one names you. You name yourself."* (effect `rank_ceremony` variant 2). | +2 SP, Resonance `second_rank` +700, 1,000 XP, node `pathfinders_poise`, title `title.pathfinder`, entry `pathfinder` |

- `Identity.promote` refuses `Path.INDEPENDENT` and leaves the rank unchanged. This is a Phase 0 assertion with a test.
- A failed requirement is named in the dialogue, as in M2.
- The ceremony uses `lesson_complete` variant 7 (`rank3`) and `story.rank_three`.
- Robes: the lesson gives the hood and robe (6.2); trousers and boots are crafted (9.3); rank 3 gives the distinctive chest piece, so the capstone is never a duplicate.

---

## 6. Chapter Three: "The Ones That Sing"

The quest worker (W2) owns this chapter. Its state lives in a new `Chapter3` record inside `StoryData` (`optionalFieldOf("chapter3")`). Other workers read it only through the frozen accessor `quest.story.ChapterThree` (`stage(p)`, `site(p, Site)`, 15.1). `PathStage` stays at `COMPLETE`, so the M2 mentor sessions and dialogue routing keep working. Stages only move forward, and every reward is protected by a one-time flag or by the stage change.

### 6.1 Stages (`ChapterThreeStage`, stored by ordinal, append only)

| Stage | Enter when | Objective (journal) | Leaves when |
|---|---|---|---|
| `NOT_STARTED` | default | | Jedi or Sith `pathStage == COMPLETE`, or flag `no_master` |
| `WAITING` | above, including old saves on join | (none shown) | 24,000 ticks after entering, or waking from ≥ 100 ticks of sleep, at a safe moment (the M2 rule), in the overworld |
| `SUMMONS` | the dream ends; Republic seed; `Outposts.ensureNear`; Independent: Courier's Fall placed | Jedi/Sith: "Speak with Master Saelen" / "Speak with Overseer Sorn". Independent: "Follow the falling light". | Jedi/Sith: the lesson is accepted in dialogue. Independent: `wayfarers_compass` is attuned at Courier's Fall. |
| `LISTENING` | cairns or caches placed | "Find what the Force hides (n/3)" | 3 touched |
| `FIRST_ECHO` | first-echo target chosen (6.3) | "Find the ruin the Force points to" / "…the tomb…" / "…the pilgrim's road…" | the first world holocron of the player's kind is attuned at any pedestal (Independent: a Wayfarer holocron other than the Compass). If that is already true on entry (for example a Sith who found a tomb early), the stage advances at once and the "careful man" line still plays as a journal line. |
| `LEADS` | ledger lead added if earned | "Follow the Collector's trail (n/4)", plus the lead list | leads ≥ 4 **and** level ≥ 9 |
| `RELIQUARY` | `ReliquarySites.place` succeeded | "Find the Reliquary (recommended level 10)" | the owner crosses the hall threshold |
| `CONFRONTATION` | | "Face the Collector" | the boss is resolved by the owner's choice, or by co-op credit (8.9) |
| `AFTERMATH` | | "Decide the Archive's fate", then "Report to …" | the hoard is decided and the report is done |
| `COMPLETE` | | the ongoing loop | |

The **"Ones That Sing" dream** reuses the dream overlay with variant `DREAM_ONES_THAT_SING` (3). It lasts 6 s, with three subtitled lines and the bed sound `story.ones_that_sing`. Taking damage ends it early, and the stage still advances.

### 6.2 The openings

**Jedi: "Listen"**

| Stage | Task | Rewards |
|---|---|---|
| `SUMMONS` | Talk to Master Saelen. She has traced the Ledger's credits to a theft from the Watch's Archive and names Orren Vasch. Accepting grants `force_sense` free and gives `jedi_hood` and `jedi_robe` (dropped for the owner alone if the inventory is full). W1's `EchoCairns.place` puts 3 veiled cairns on natural ground within 24 blocks of camp, at least 8 blocks apart. | entry `listen`, codex `force_sense` |
| `LISTENING` | Use Sense near camp and touch the 3 cairns. Each one plays a line about the Watch's old waystones. | 400 XP, trust +10, Jedi Order +40, entry `what_the_force_hides` |

**Sith: "The Hunt"**

| Stage | Task | Rewards |
|---|---|---|
| `SUMMONS` | Talk to Overseer Sorn. Imperial Intelligence bought the poachers' list of names, and the player's was on it; the buyer of the crystals also robbed a tomb the Empire calls its own. Accepting grants `force_sense` and gives `sith_hood` and `sith_robe`. 3 cairns: "marks left by the Empire's own hunters". | entry `the_hunt`, codex `force_sense` |
| `LISTENING` | Touch the 3 cairns. | 400 XP, trust +10, Sith Empire +40, entry `what_the_force_hides` |

**Independent: "Courier's Fall"**

| Stage | Task | Rewards |
|---|---|---|
| `SUMMONS` | While the journal is held, Calling variant `FALL` (8): ember streaks slant toward the site. Two crystal poachers scavenge the wreck: Vasch's own courier, its strongbox full of his payments to the poachers. At the cargo bay pedestal (`always_ready`, `fixed_id` Compass) the player attunes `wayfarers_compass`, and Tessaly Arn teaches Force Sense. The dialogue ends with Vasch's sealed note, found in the strongbox: an invitation to "a reader of no Order" to come and see his Archive. | the holocron's own rewards (4.4), with no lead; entry `couriers_fall` |
| `LISTENING` | 3 veiled caches (`echo_cairn`, style CACHE) lie 12 to 24 blocks from the fall. Each pays `gameplay/wayfarer_cache`: 2 durasteel, and 1 `lumen_thread` or `grave_silk`. | 400 XP, Frontier +40, entry `what_the_force_hides` |

### 6.3 The first echo

When FIRST_ECHO begins, W2 picks the target:

1. **Jedi:** `StructureLocator.find(JEDI_RUIN, 768, unused)`.
2. **Sith:** `StructureLocator.find(SITH_TOMB, 768, unused)`.
3. **Independent:** the nearest of `WRECK_FIELD`, `JEDI_RUIN` and `SITH_TOMB` within 768 blocks, since all of them hold a Wayfarer pedestal (the niche is mandatory, 3.2).
4. **No result:** `EchoShrines.place(owner, kind)`, at rings 96/128 with fallback 48/80. The position is appended to `Chapter3.shrines`.

The journal Calling (variant `HOLOCRON`, 4) points to the target while the journal is held. Any pedestal of the right kind counts; it need not be the target.

When the stage is left:

- entry `first_echo`;
- codex `the_collectors_trail`;
- the holocron's lead `holo:<id>`;
- the stage moves straight on to LEADS.

### 6.4 Leads

`Chapter3.leads` is a set of at most 16 entries. Four leads are needed (`chapter_three.leadsNeeded`).

| Lead | Source | Journal line |
|---|---|---|
| `ledger` | Chapter Two flag `ledger_found` or `ledger_delivered`, added when LEADS begins | "The Ledger's buyer paid in Republic credit chits stamped with the Watch Archive's vault seal." |
| `holo:<id>` | any world holocron attuned from FIRST_ECHO on | "<Gatekeeper> remembered the careful man." |
| `mission:<id>` | each story mission completed, whatever the outcome | one line per mission |
| `contract` | the first contract completed during LEADS (once) | "A rumour from the boards: someone pays the poachers in Republic chits stamped with an old Watch seal, and asks only for crystals that sing." |

A lead earned before LEADS is kept and counts once LEADS begins.

**Vasch's presence** (two cheap beats, once each, W2):

- **At 2 leads:** the next holocron the player attunes carries a message Vasch left inside it, played as the last line of the gatekeeper dialogue (`vasch_message`): *"You are reading my shelves. Read carefully."*
- **At 3 leads:** a counter-hunt. The next time the player is on the surface 64 blocks or more from any site, a crew of his paid poachers (the `raider_camp_small` crew, 3 or 4 by level) ambushes from 24 to 32 blocks away, out of sight, with a sergeant-style bark ("The Archivist pays double for you."). Owned by the player, spawned through `Poachers.spawnCrew`, despawned if the player moves 128 blocks away. Entry line in the journal.

**Level gate.** If the player has 4 leads but is below level 9, the journal says: "The trail is clear, but you are not ready (level 9)." The Reliquary Calling waits, but the journal never goes quiet: while gated, the Calling points to the nearest unused holocron chamber, or else to the nearest outpost board, and the left page names the fastest XP sources (contracts, unvisited structure types, holocrons).

A typical Jedi reaches 4 leads with the Ledger, the first echo, R1 and R2. A Sith does the same with the Ledger, the first echo, I1 and I2. A player without the Ledger lead uses a second holocron or a contract instead.

### 6.5 Convergence

When RELIQUARY begins, W2 calls `ReliquarySites.place(owner, playerPos, look)` (W5). The returned heart position goes into `Chapter3.reliquary`.

- On failure it retries every 1,200 ticks (3.6).
- If `collector.placeReliquary` is false, an operator uses `/oldrepublic c3 reliquary <player>`.
- The arena is always built from blocks: `story.placeStructures` does not apply, because the fight needs its walls.

While the journal is held, Calling variant `RELIQUARY` (6) sends deep amber glints and a resonant hum that grows as the player gets closer. Every holocron item the player carries glints. Entry `the_reliquary`.

### 6.6 Confrontation, aftermath and report

- CONFRONTATION ends when the owner chooses in the yield dialogue (8.7), or when the player receives co-op credit and picks a stance (8.9). Then AFTERMATH begins.
- AFTERMATH has two steps: the hoard choice (4.8), then the report.

| Path | Report to | Rewards |
|---|---|---|
| Jedi | Saelen (`c3_hub`, "I have a report.") | 600 XP, trust +10, Resonance `chapter_three` +300, entry `report_watch`, banner `chapter3` (`lesson_complete` variant 5) |
| Sith | Sorn | the same, entry `report_overseer` |
| Independent | any officer above Hostile, chosen in dialogue (a story action, also open at Wary); or "Send word" from the journal if both armies are Hostile | 600 XP, that army +50 (Frontier +50 for "Send word"), Resonance `chapter_three` +300, entry `report_officer` |

The mentor's report lines react to the boss outcome and the hoard choice. For a co-op helper they react to the owner's world outcome when it clashes with the helper's own stance ("You let the Empire take him?"). The trust changes are already applied in 8.7, 8.9 and 4.8.

### 6.7 Journal (Chapter Three)

Phase 0 appends these ids to `JournalEntries` (append only) and sets `CHAPTER_THREE_FIRST = THE_ONES_THAT_SING`. `JournalScreen` gains a list of chapter boundaries.

- **Entries:** `the_ones_that_sing`, `two_flags`, `listen`, `the_hunt`, `couriers_fall`, `what_the_force_hides`, `first_echo`, `silent_patrol`, `credits_from_the_core`, `names`, `border_incident`, `the_reliquary`, `the_collector`, `collector_spared`, `collector_executed`, `collector_bound`, `collector_released`, `archive_returned`, `archive_kept`, `archive_unmade`, `report_watch`, `report_overseer`, `report_officer`, `knight_of_the_watch`, `named_lord`, `pathfinder`.
- **Codex:** `two_flags`, `the_treaty_line`, `force_sense`, `telekinetic_lift`, `holocrons`, `holocron_<id>` for each of the 15 holocrons (written by W3), `the_collectors_trail`, `vasch_records`, `doctored_list`, `republic_troopers`, `imperial_troopers`, `ruin_sentinels`, `the_collector`, `the_collector_bound`, `archive_unmade`, `era_armour`.

The Chapter Three left page shows the current objective and, during LEADS, the lead list with a tick for each one found.

**Codex unlocks.** W3 writes and grants `holocron_<id>`; W2 grants every other codex entry, from its own logic or from the hook named:

| Codex id | Unlocks when | Through |
|---|---|---|
| `two_flags` | the dream ends (SUMMONS) | W2 stage change |
| `the_treaty_line` | the first outpost of either army is discovered | `onSiteEntered` |
| `force_sense` | the lesson is accepted, or `wayfarers_compass` is attuned | W2 / `onHolocronAttuned` |
| `telekinetic_lift` | an Independent attunes `wayfarer_pell` | `onHolocronAttuned` |
| `holocrons` | the first world holocron is attuned | `onHolocronAttuned` |
| `the_collectors_trail` | FIRST_ECHO is left | W2 stage change |
| `vasch_records`, `doctored_list` | the R2 Keep and I1 doctored-list choices | W2 mission choice |
| `republic_troopers`, `imperial_troopers` | the first outpost of that army is discovered, or the first trooper of that army is killed or tended | `onSiteEntered`, `onTrooperKilled`, `onTrooperTended` |
| `ruin_sentinels` | the first Jedi Ruin or Sith Tomb is entered, or the first sentinel is destroyed. The entry explains that "bound" sentinels were re-bound by a careful hand to guard someone's trail (foreshadowing Vasch). | `onSiteEntered`, `onConstructKilled` |
| `the_collector` | the owner's first clear | `onBossDefeated` |
| `the_collector_bound` | the Bind ending | `onBossDefeated` |
| `archive_unmade` | the Unmake choice | `onArchiveResolved` |
| `era_armour` | the first era armour piece is equipped | `onArmourEquipped` (new hook; W4 calls it from its equipment-change listener) |

### 6.8 Dialogue conditions and actions (registered by their owners)

| Kind | Id (argument) | Owner | Meaning |
|---|---|---|---|
| condition | `c3:stage` (stage id; met when the stage is at least this) | W2 | |
| condition | `c3:leads` (n) | W2 | |
| condition | `mission:available` (mission id) | W2 | gate met, not done, none active |
| condition | `mission:active` (mission id or `any`) | W2 | |
| condition | `rank3:ready` | W2 | the fail reason names the first unmet requirement |
| condition | `holocron:carrying` (`jedi` / `sith` / `wayfarer` / `any`) | W3 | an unreturned holocron in the inventory |
| condition | `holocron:attuned` (holocron id) | W3 | |
| condition | `shards` (n) | W3 | |
| action | `c3:accept_lesson`, `c3:report`, `c3:send_word` | W2 | |
| action | `c3:stance` (`spare` / `execute` / `bind` / `release`) | W2 | a co-op helper's own stance (8.9) |
| action | `mission:start` (id), `mission:turn_in` (choice) | W2 | |
| action | `rank3:ceremony` | W2 | |
| action | `collector:resolve` (`spare` / `execute` / `bind` / `release` / `bring_in_republic` / `bring_in_imperial`) | W2 | calls `BossArenas.resolve` (W5) |
| action | `yield:interrogate`, `yield:hand_over` | W2 | the `patrol_intercept` and I2 yield choices (`YieldOutcome.INTERROGATED`, `HANDED_OVER`) |
| action | `holocron:return` (`jedi` / `sith` / `republic` / `imperial`) | W3 | |
| action | `holocron:choose` (`own` / `seal` / `listen` / `break`) | W3 | |
| action | `archive:choose` (`return` / `keep` / `unmake`) | W3 | |
| action | `shards:exchange` (`republic` / `imperial`) | W3 | takes 2 shards and gives a commendation through `Commendations.of` (W2) |
| action | `shards:amends` (`republic` / `imperial`) | W3 | takes 2 shards for +40 standing with that army, no commendation (Wary officers) |
| action | `boss:call_echo`, `boss:unchained` | W5 | dialogue `reliquary_heart`: start the rematch or the Unchained mode |

**Dialogues added in M3** (W2 unless noted): `officer_republic`, `officer_imperial`, `c3_collector_meet`, `c3_collector_yield`, `c3_collector_stance`, `c3_send_word`, `pathfinder`, `yield_intercept` (Spare / Execute / Interrogate), `yield_border` (Spare / Hand him over / Execute), `r1_leave_confirm`, `i1_burn_confirm`; `holocron_<id>` and `archive_hoard` (W3); `reliquary_heart` (W5). The M2 `Yieldable` flow picks the dialogue through the new `Yieldable.yieldDialogue()` (15.1); `null` keeps M2's `yield.json`.

### 6.9 Old saves and edge cases

- M2 saves at Chapter Two `COMPLETE` enter `WAITING` on join, with `waitSince` set to the current time.
- Players who never finished Chapter Two wait at `NOT_STARTED`.
- Chapter Three sites are overworld only. The WAITING timer counts in any dimension, but the dream waits for the overworld.
- If a mentor is missing, the M2 rule applies: found by UUID, otherwise respawned at the camp anchor.
- If Courier's Fall, a shrine or the Reliquary cannot be placed, placement retries every 1,200 ticks on the fallback rings. After 3 failures it uses the M2 forward fallback, 12 blocks ahead. The Reliquary has no forward fallback; it keeps retrying and logs a warning.
- The Republic seed, the lesson gifts and every stage reward are one-shot flags.
- **Late join (path change).** M2 lets an Independent join a mentor once, later (`StoryRules.lateJoin`), which restarts that path at LESSON_ONE. From Chapter Three SUMMONS on, the mentor refuses: *"Your road is already chosen. Walk it."* (W2 adds the stage check to `lateJoin`; a `StoryM3GameTests` case covers it). Before SUMMONS (NOT_STARTED or WAITING) the M2 rule applies unchanged, and Chapter Three simply starts later on the new path. This keeps the stage machine free of mid-chapter path changes.
- **Peaceful.** Officers, garrisons, sentinels and mission NPCs are never removed (no trooper or construct type opts into `notInPeaceful`). Troopers never attack players (5.2), dormant sentinels do not wake for players, and patrols do not spawn. Combat objectives complete when the player reaches the site: R1's sentinels and other bound guards crumble on their own when the owner arrives ("The binding has failed."), poacher crews flee, and incident squads stand down and yield at once. The Reliquary refuses to start a fight: Vasch talks, then kneels (CONFRONTATION goes straight to the yield). A `StoryM3GameTests` Peaceful case and a smoke test (officers and Vasch survive a Peaceful tick) cover it.

---

## 7. Enemies

### 7.1 Troopers: shared rules (W4, `enemy.trooper`)

- **Class.** One class, `TrooperEntity extends PathfinderMob implements Aligned, Yieldable` (like `MentorEntity`), registered as two entity types:
  - `republic_trooper`: Allegiance `REPUBLIC`, Faction `REPUBLIC`;
  - `sith_trooper`: Allegiance `EMPIRE`, Faction `SITH_EMPIRE`.

  Troopers are **not** a vanilla `Monster` or `Enemy`: an Allied garrison must not stop the player sleeping, iron golems must not attack officers near villages, and hostility comes from `Attitudes` only (5.2). Sounds use `SoundSource.HOSTILE`. The types do not call `notInPeaceful`, so officers and garrisons survive Peaceful (6.9). `removeWhenFarAway` is false for every trooper; patrols despawn as whole squads instead (7.6). Garrison, mission and worldgen troopers are `setPersistenceRequired()`.

  Size 0.6×1.95. The role is synced (`DATA_ROLE`, the ordinal of `TrooperRole`, append only) and saved as `Role`. The squad UUID is saved as `Squad`; it is always generated fresh at spawn time by `Troopers.spawnSquad` (never read from a template). The incident state is saved as `Incident` (mode ordinal and expiry game time); the provocation and engagement expiries as `Provoked` and `Engaged` (5.2). A spawn reason of STRUCTURE never re-rolls a `Role` that was read.
- **Look (original):**
  - **Republic:** off-white plates over a navy undersuit, a muted gold shoulder stripe, and a rounded helmet with one wide horizontal tinted visor band (no T-visor).
  - **Imperial:** charcoal plates over a black undersuit, crimson piping, and an angular helmet with a vertical nose ridge and two narrow eye slits. Not white, and not skull-like.
  - **Role marks:** Medic, a teal shoulder band and a kolto injector on the hip. Sergeant, gold chevrons on the helmet side. Grenadier, a bandolier.
  - **Officers:** navy (Republic) or grey (Imperial) uniforms with peaked caps, and no armour.
  - Skins are 64×64. W4's new `gen_textures_trooper.py` composites them from the same textures as the armour items (it imports the palette and compositing function from `gen_textures_equipment.py`), so the player and the troopers look alike. There is no mob armour layer.
- **Weapons:** the render-only items `blaster_rifle` (existing since M1, `EnemyItems.BLASTER_RIFLE`, reused) and `blaster_pistol` (M2).
- **Spawn eggs:** `republic_trooper_spawn_egg` and `sith_trooper_spawn_egg` (M1/M2 precedent).
- **Bolts:** `BlasterBolt.Style` is synced:

  | Style | Colour |
  |---|---|
  | `POACHER` | orange "scavenged cells"; M1 poachers switch to it |
  | `REPUBLIC` | blue-white |
  | `IMPERIAL` | crimson |
  | `SENTINEL` | amber-white and heavy |

  Every shooter's charge tell stays 12 ticks, with a distinct whine pitch and timbre per army. The style is synced through `SynchedEntityData`, so the first client frame has the right colour. Only `SENTINEL` bolts are heavy; Republic and Imperial bolts are normal (deflectable, normal guard drain).
- **Friendly fire.** `BlasterBolt.canHitEntity` skips anything `Allegiances.friendly` with the shooter, and any player the shooter is ALLIED with (5.2).
- **Answering deflection.** A rifleman with 2 of its bolts deflected by anyone within 100 ticks holds fire for 40 ticks and repositions 4 to 8 blocks sideways (bark category `flank`).
- **Cover.** Below 50% health, riflemen seek cover through a generalised `PoacherCoverGoal`.
- **Barks.**
  - Shown as one subtitle line on the HUD (`BarkHud`), within 24 blocks.
  - The effect `trooper_bark` is sent around the speaking trooper with its entity id (so the client reads the army and role for the line and the "Republic Sergeant:" tag) and variant = category × 4 + the line number, and plays `trooper.radio` (a radio click).
  - Categories: `spot`, `warn`, `standoff`, `salute`, `hurt`, `reload`, `flank`, `rally`, `grenade`, `reinforce`, `retreat`, `yield`. Each has 3 lines per army.
  - Rate limits: 1 per trooper per 120 ticks, and 1 per squad per 40 ticks.
- **Force reactions.** Troopers are **not** push-resistant. Disarming Pull stops a trooper firing for 120 ticks. The M2 control rules apply.
- **Yielding.** Only incident-tagged Sergeants yield, at 20% in I2 and 25% in `patrol_intercept`, through the M2 `Yieldable` flow; `yieldDialogue()` returns `yield_border` or `yield_intercept` (6.8).
- **Drops** (`killed_by_player` only): `durasteel_plate` 20%. Never armour, and never commendations. Loot tables `entities/republic_trooper` and `entities/sith_trooper`.
- **Kill XP:** the M1 formula, 1.5 × max health capped at 80, and only if the trooper counted as hostile toward the killer when the killing damage landed (5.2).
- **Downed troopers** (`Troopers.spawnDowned`, `Troopers.isDowned`, `Troopers.tend`):
  - a synced boolean `DATA_DOWNED` (vanilla `Pose` has no downed value and a mod cannot add one); the trooper renderer reads it from the render state and lays the model on its side; the server sets `setNoAi(true)`, and `canBeSeenAsEnemy` returns false, so mobs and area powers never target them;
  - stand up when tended (`trooper_tend` effect, `trooper.stand`) and walk home;
  - keep their own bleed-out game time (`BleedAt`, saved) and discard themselves on their tick or on load once it has passed (2,400 ticks after the mission ends without tending them). Nothing reaches into unloaded chunks.
- **Escorts** (`Troopers.spawnEscort`): two `noSave` troopers with no combat goals that walk a prisoner off and despawn out of sight (I2 hand-over, the Collector's custody endings).

### 7.2 Republic roles

| Role | Health / armour | Pattern | Tell | Special | Counterplay |
|---|---|---|---|---|---|
| Rifleman | 24 / 4 | Holds a 10–18 block band and uses cover. Bursts of 3 bolts × 4 damage, 4 ticks apart, 50 ticks between bursts. After 3 bursts the rifle **vents** for 40 ticks and cannot fire. | muzzle glow and `trooper.republic.charge`, 12 ticks; vent: a heat shimmer and steam from the barrel, `trooper.vent` | | deflect or parry; Push breaks cover; punish the vent |
| Field Medic | 20 / 2 | Pistol, 4 damage every 40 ticks; stays behind the squad | kneels over an ally; a pale teal kolto mist | Heals an ally at ≤ 50% health by 8, after a 20-tick channel. Cooldown 160. | any hit of 4 or more, a Push or a Pull interrupts the channel |
| Sergeant | 30 / 6 | Rifle bursts, as the Rifleman | raised fist and bark, 30 ticks | **Rally** (once per 600 ticks, while ≥ 2 of the squad live): allies within 12 blocks gain Resistance I and a 25% shorter burst gap for 200 ticks | a stagger, Push, Pull or the kill during the 30-tick tell cancels it; the squad breaks when he falls |

### 7.3 Imperial roles

| Role | Health / armour | Pattern | Tell | Special | Counterplay |
|---|---|---|---|---|---|
| Rifleman | 24 / 5 | A single strong bolt, 7 damage, cooldown 34. It is a normal bolt (`heavy` = false): a guard deflects it with the normal stamina drain, and a parry returns it. One rifleman per squad is the **flanker**: it circles 90° off the squad's line at 12–16 blocks. | muzzle glow and `trooper.imperial.charge`, 12 ticks | | deflect or parry the slow, hard bolt; watch the flank |
| Grenadier | 26 / 5 | Pistol, 4 damage. A **frag grenade** every 200 ticks at a target 6–14 blocks away. | arm drawn back, a blinking indicator, `grenade.arm`, 20 ticks; once landed, a fuse tell for its 40 ticks: a quickening blink and a rising tick (`grenade_fuse`, `grenade.fuse`; a steady glow with flashing off) | The grenade lands, then bursts 40 ticks later: 8 damage within radius 3 (50% at the edge), knockback 0.5, no terrain damage. | **Push** sends the grenade along the push direction at ×1.2. **Pull** flings it back to land at the thrower's feet. Or move away: the fuse tell shows where it is. |
| Sergeant | 30 / 7 | Rifle, as the Imperial Rifleman | raises a flare pistol, 30 ticks | **Reinforcements** (once per encounter, after any squad member falls): a crimson flare climbs 20 blocks. 200 ticks later 2 Imperial riflemen run in from 24–32 blocks away, from the side away from the player and out of sight. | Disarming Pull, a stagger, a Push or the kill during the tell cancels it |

### 7.4 Officers

- `TrooperRole.OFFICER`: invulnerable except to `/kill` and the void, non-combatant, no weapon drawn. Officers stand at the terminal and open their dialogue on use.
- Spawned by the `bounty_terminal` through `Troopers.spawnOfficer(level, pos, faction, nameSeed)`. Persistent. The custom name is the frozen lang key `npc.oldrepublic.officer.<army>.<nameSeed>`.
- A missing officer (removed by `/kill` or the void) is respawned by its terminal under the loading rule (3.3). A duplicate, meaning an officer whose terminal lists a different UUID, discards itself when it loads.

### 7.5 Squads, cold peace and incidents

- **Squads:**
  - Republic patrol: 2 riflemen, 1 medic and 1 sergeant.
  - Imperial patrol: 2 riflemen, 1 grenadier and 1 sergeant.
  - +1 rifleman when the nearest player is level ≥ 11 (mission templates have a `SQUAD_5` anchor for it).

  Members share targets, spread 3 to 5 blocks apart, and follow the sergeant. The squad id is a fresh UUID from `Troopers.spawnSquad`.
- **Morale:** when the sergeant dies and half the squad is down, the rest fall back 24 blocks for 200 ticks, then return.
- **Cold peace:** Republic and Imperial troopers never target each other unless:
  - either side carries an incident (`IncidentMode.ARMY` or `ARMY_AND_OWNER`; missions and contracts); or
  - a trooper defends an Allied player against the other army. That starts an incident (`ARMY`), and both squads carry it for 1,200 ticks.
- **Standoff:** when two untagged patrols of opposite armies come within 16 blocks:
  1. both halt facing each other at least 12 blocks apart, with rifles lowered;
  2. the leaders bark (category `standoff`: "Treaty line's behind you, Imperial." / "Keep walking, Republic.");
  3. after 200 ticks both walk on and keep at least 12 blocks apart.

  Garrisons do the same when a rival patrol passes.
- The rules are pure functions (`ColdPeace.shouldEngage`, `ColdPeace.standoffStep`, the `Attitudes` trooper rule, the incident and provocation expiry) so they can be unit tested with an explicit game time.

### 7.6 Natural patrols (W4, `PatrolSpawner`)

- **Who:** players at Chapter Three SUMMONS or later (`ChapterThree.stage`), in the overworld, standing on the surface (sky visible). Not in Peaceful: the spawner checks the difficulty itself.
- **Spawning:**
  - every 1,200 ticks per player, a 15% chance of one squad;
  - the squad belongs to the army with the nearer outpost within 384 blocks, or a random army if there is none;
  - it appears 48 to 80 blocks away, out of the player's line of sight, on `#story_ground`, in loaded chunks;
  - it walks a route that passes 16 blocks from the player and continues 96 blocks beyond.
- **Caps:** 1 patrol per player within 128 blocks, and 6 per server. No patrols within 64 blocks of a mission site or the Reliquary.
- **Despawning:** patrol members are not persistent, but `removeWhenFarAway` is false, so vanilla never removes them one at a time mid-route. Every 100 ticks `PatrolSpawner` discards a whole squad when every member is more than `patrols.despawnDistance` (128) from all players, and discards all patrols on server stop.
- Kill XP fatigue applies as normal.

### 7.7 Ruin Sentinel (W5, `enemy.construct`)

`ruin_sentinel` is an ancient security construct with `SentinelVariant { WATCHER, TOMB }`, synced and saved as `Variant`. It is a 2.3-block stone and bronze construct with a chest **lumen**. Its Allegiance is `ANCIENT`, except the sentinels Vasch binds in the Reliquary, which are `ARCHIVE` like him, so their lances never hit him. It extends `Monster` (it is a real hostile construct) and has a spawn egg, `ruin_sentinel_spawn_egg`. On death it crumbles: the lumen gutters out, the plates slump and break into stone and bronze debris and dust (`sentinel_death`, `sentinel.death`).

- **Watcher:** weathered pale stone, robe-like carved plates, a closed face plate with one vertical slit, a pale blue lumen.
- **Tomb:** dark basalt, angular, bronze spikes along the shoulders, a red lumen.

| | Value |
|---|---|
| Health / armour / speed | Watcher 48 / 8 / 0.18; Tomb 60 / 8 / 0.16 |
| Dormant | Stands as a statue in an alcove and only checks for waking (every 10 ticks). It wakes when a player comes within 6 blocks (4 with `quiet_step`), when it is hit, or when the urn puzzle fails (`Sentinels.wakeNear`). Touching a pedestal never wakes it. **Watchers** stay dormant for players at alignment ≥ +15 (the robe and rank 3 threshold) and bow their heads as one passes within 6 blocks. **Tomb** sentinels stay dormant for players at ≤ -15. Both still wake if attacked, and `wakeNear` never wakes a sentinel that is dormant for that player. **Bound** sentinels (mission, contract and boss spawns, `Bound: 1b`) are always hostile: the `ruin_sentinels` codex explains that a careful hand re-bound them to guard someone's trail. In Peaceful, dormant sentinels do not wake for players. A dormant sentinel never stops a player sleeping (`isPreventingPlayerRest` is false while dormant), so a bed near a ruin works. The wake tell: the lumen ignites, stone grinds and dust sheds for 20 ticks. |
| **Slam** (within 3 blocks) | 20-tick tell: the arms rise and dust sheds from them. 10 damage in a 2.5-block arc of 90° in front, knockback 0.6. Afterwards the **core is exposed** for 40 ticks (damage taken ×1.5). Cooldown 60. The impact throws irregular debris and dust puffs along the arc, never a flat ring. |
| **Lance** (6–20 blocks) | 24-tick tell: the lumen brightens and motes are drawn into the chest. Fires one heavy `SENTINEL` bolt: 7 damage, speed 1.6. **Only a parry returns it** (10 damage to the sentinel and a 20-tick stagger; inside the Reliquary a parried lance goes toward the crosshair instead, 8.3). A normal guard blocks it but drains 35 stamina; a deflection without a parry is only a block. Cooldown 80. |
| Resistances | Push knockback ×0.2 (`#push_resistant`, plus its own `ForceReactions` handler). **Lightning ×1.5, and shock lasts twice as long** (overload). Blaster bolts ×0.5. Saber ×0.85. Immune to fall damage, drowning, poison and wither. Lift raises it only to the push-resistant height (1.2). |
| Drops | `durasteel_plate` 40% (1–2); `lumen_thread` (Watcher) or `grave_silk` (Tomb) 20%; kill XP capped at 80. Worldgen garrisons never respawn. |

### 7.8 Changes to existing enemies

- `CrystalPoacher` and `PoacherCaptain` implement `Aligned` (`CREW`) through `PoacherCrew`, and fire `Style.POACHER` bolts.
- `BlasterBolt.canHitEntity` uses `Allegiances.friendly` instead of its `PoacherCrew` check.
- Poachers and troopers are hostile to each other on sight.
- `Poachers.spawnCrew(level, pos, count, owner)` (W4, frozen in 15.1) is the one way other workers spawn poacher crews: site spawners, mission camps, the 3-lead ambush.
- **The Sith Acolyte gains bolt deflection** (W5). Its `DuelistProfile` sets `deflects`, so it can turn blaster bolts while guarding (10.7). Nothing else about it changes.
- **`DuelistProfile` grows** (W5): new fields `deflects` (boolean), `heavyDamage` (float; 0 = same as `damage`) and `parryMinStamina` (float; 0 = no threshold). A compatibility constructor with the M2 field list fills the defaults, so existing profiles compile unchanged.

---

## 8. The first major boss: Orren Vasch, the Collector

### 8.1 Identity and lore

Orren Vasch was an Archivist of the Watch enclave on this frontier, the order whose ruins the player explores. When the Treaty of Coruscant was signed he called it *"a peace bought by surrender"*. He took the enclave's holocrons and walked away.

Since then he has robbed Sith tombs as well. His reasoning: no Order should ever again decide what the Force is for, so he will keep it himself. He pays poachers in Republic credit chits stamped with the Watch Archive's vault seal (relief funds the Watch held, taken with the Archive) for crystals "that sing", which wake the resonance pylons in his Reliquary. The Republic wants its Archive back; the Empire wants its tomb relics and the man. To an Independent he is something else: a man who walked away from every Order, as they did, and who would like company.

He is a **Jedi opponent where the narrative supports it**, as the brief allows. He is tired, articulate and certain, and he drifts darker in the last phase. He and his name are original.

**Entity** (W5, `boss.OrrenVasch`): `orren_vasch` extends `Monster` and implements `SaberDuelist`, `Aligned` (`ARCHIVE`) and `Yieldable`, exactly like the M2 `SithAcolyte`, so the saber, parry and yield code paths need no new branch. It does not opt into `notInPeaceful`: in Peaceful the heart starts no fight and he talks, then kneels (6.9, 8.3). `isPreventingPlayerRest` returns true only while his session is fighting, so the antechamber bed works between attempts and while he kneels yielded. `removeWhenFarAway` is false; he is `noSave` and lives only inside an `ArenaSession` (8.6).

**Look** (original; W5's artist brief in `ASSET_MANIFEST.md`, 64×64 skin from `gen_textures_boss.py`):

| Part | Description |
|---|---|
| Silhouette | Tall and spare, slightly stooped; hood down; greying hair tied back; a short greying beard |
| Clothing | Layered Archivist robes in faded ochre and ink-grey, the cuffs ink-stained; fingerless leather gloves; a long cloak or sash as a separate model part (it streams forward in the Surge tell) |
| Gear | A satchel and a bandolier of empty holocron cradles across the chest |
| Blade | Amber, from a squared, archival hilt with a brass collar |
| Phase 3, "Unkept" | Hood up; the stolen Sith holocron in his off-hand lights the hand from below with its crimson seams; the cloak's edges fray into drifting dust (steady, no flicker, with flashing off) |
| Echo of the Collector | The same model, desaturated, its outline breaking into slow smoke at the edges |

Lines (subtitled):

- *"Give them back? To whom?"*
- *"Every Order burns what it fears. I only kept what they would have burned."*
- *"Listen to them sing. That is what the Treaty sold."*
- To an Independent: *"You walked away from them too. Stay. Read with me."*

### 8.2 Arena: the Reliquary (`boss/reliquary_hall`, 31×18×31)

```
 N   [ hoard vault 9x7, behind the dais; reliquary_seal until resolved ]
     [ P1 ] [ dais 11x5, +3 high: lectern, book, shelves; Vasch starts here ] [ P2 ]
    +--------------------------- 29 ---------------------------+      P1, P2 = pylons on the dais wings (+3), within 9 blocks of the dais centre
    | gallery W (stairs at S end)          gallery E (stairs) |      galleries +5: 2 sentinel alcoves each (SENTINEL_1-4)
    |     c            c            c                          |       c = broken column (2x2, 4 high): cover
    |              sunken floor 15x15, -1                      |       4 debris piles (Hurl sources, not terrain)
    |     c            c            c                          |
 P3 +------------------------ threshold -----------------------+ P4   P3, P4 = pylons on the gallery S ends (+5)
              antechamber 9x9: vanilla bed, reliquary_heart, reliquary_seal
              spiral descent up to the watchtower stump (boss/reliquary_surface, 17x16x17)
```

A solo loop of the four pylons is short: P1 and P2 sit beside him on the dais, and only P3 and P4 need the gallery stairs or a Force Jump.

- **Light:** static block light 7 from crystal sconces. Pulses are particle-based; nothing flashes when flashing effects are off.
- **`reliquary_heart`** (W5, block entity in the antechamber) anchors the session. It stores the owner's UUID, the state, `firstClear` and the echo flags.

  | State | Meaning |
  |---|---|
  | `DORMANT` | placed; the owner is away |
  | `READY` | the owner is within 48 blocks; Vasch stands at the lectern (spawned, `noSave`, Allegiance `ARCHIVE`) |
  | `ACTIVE` | a fight is running |
  | `YIELDED` | he kneels at the dais; this state persists |
  | `RESOLVED` | the owner has chosen; the hoard is open; the rematch is available |

  The heart also saves `attempts` (fights started by the owner), so retries skip the meeting dialogue.

- **Seals.** Two `reliquary_seal` blocks (W5), driven by the heart: one in the antechamber doorway, and one in front of the hoard vault (`vault` property).
- **The bed** is a vanilla bed. Using it sets the respawn point, so retries need no special code.

### 8.3 Fight flow

**Start:**

1. **First attempt.** The owner crosses the threshold. The doorway seal closes after 40 ticks, once nobody stands in it. Vasch speaks: dialogue `c3_collector_meet` (W2) offers "Give them back." (MINOR light +1) or "Why?" (a lore branch). An Independent also hears his offer, *"You walked away from them too. Stay. Read with me."*, and answers "No." or "Not like this." (no shift; flag `c3.vasch_offer`). The fight begins when the dialogue closes (W5 listens on `Dialogues.ON_CLOSE`), or after 400 ticks; when the timer starts the fight, any dialogue still open is closed.
2. **Later attempts** (the heart's `attempts` ≥ 1). No dialogue. The doorway seal stays open and Vasch waits at the lectern, so the player can walk in and pick up anything dropped. The fight starts when a participant comes within 6 blocks of the dais or uses the lectern ("Face me."), with a one-line bark (*"Again, then."*); the seal closes behind them as before.
3. **Rematch:** no dialogue. The echo forms from smoke over 60 ticks (`boss_echo` variant 0).
4. **Peaceful:** no fight; he talks and kneels (6.9).

**Participants** are awakened players inside the hall box at the start, or who enter during phase 1; the session lists them by UUID. A participant who dies stays on the bar while the fight runs, and the doorway seal opens for listed participants coming back from the antechamber (and only for them), then closes behind them. A participant who leaves the hall box alive, changes dimension or disconnects is removed from the bar after 100 ticks.

| Phase | Health band | Behaviour | Tells (posture, dust, light, sound; never ground marks) | Vulnerability and counterplay |
|---|---|---|---|---|
| **1. The Archivist** | 100% → 60% | A guarded duellist on `DuelistCombatGoal` with profile `collector_archivist`: light hits 6, heavy 11 (`heavyDamage`), guard 140, parries light attacks above 70% stamina (`parryMinStamina`) 30% of the time, strings of 2 to 3, wind-up 10, recovery 16. **Archive Hurl** every 180 ticks: an `archive_debris` chunk thrown at the current threat (8 damage, speed 1.1). **Repelling Palm** if a player stays within 3 blocks for 80 ticks: 4 damage (`repelling_palm`) and knockback 1.4. | **Hurl:** the off-hand rises, a chunk tears from the nearest debris pile, grit pours off it, stone grinds (24 ticks). **Palm:** hand drawn back, dust drawn in toward the palm (16 ticks). | **Pull** during the Hurl tell rips the chunk away: it falls between them and he staggers for 20 ticks. **Lift** during the tell catches the chunk and holds it (it drops harmlessly on release; with `lift_hurl`, the release throws it back at him for the Push-return damage). **Push** within 6 blocks of the flying chunk returns it: 12 damage (×1.5) and a 30-tick stagger. A saber guard blocks it (drain 30) but cannot deflect it. A **Push** during the Palm tell causes a push clash (the M2 acolyte rule): both are pushed back and he staggers for 20 ticks. Heavy attacks break his guard. Sidestep. |
| Transition | at 60% | Invulnerable for 60 ticks. He leaps to the dais and lifts a singing crystal. The four pylons wake one by one, 15 ticks apart. The bar turns purple: "The Singing Wall". | ceiling dust, the sting `boss.collector.transition`, camera shake 0.3 (scaled by the client setting) | |
| **2. The Singing Wall** | 60% → 30% | **Resonance Ward:** with 3–4 pylons singing he takes ×0.35 damage; with 1–2, ×0.65; with none, ×1.0. **Resonant Lash** every 140 ticks. 2 bound Watcher sentinels wake in the gallery alcoves (`SENTINEL_1`–`SENTINEL_4`), +1 per extra participant (at most 4). His melee keeps the phase 1 profile, but he stays within 8 blocks of the dais. A silenced pylon **re-sings** after 800 ticks, but only while at least one other pylon still sings, and at most once per phase. **Unsung:** when the last pylon falls silent, he staggers off the dais, guard broken, and takes ×1.5 damage for 100 ticks; then the ward stays down for the rest of the phase. | **Ward:** sparse heat-haze specks drift off his shoulders and blade, denser with more pylons singing, and thin, wavering currents of crystal motes stream from each singing pylon to him. No bubble, no halo, no outline. **Lash:** he drags his blade tip and a seam of floor dust lifts in a straight line toward the target as the hum rises (20 ticks). Then a 1-block-high ridge of dust and grit travels along the line at 0.8 blocks per tick for up to 20 blocks. **Re-sing:** the crystal regrows over 60 ticks with a rising whine. **Unsung:** the last currents snap back into the pylons, he reels, and dust shakes from the ceiling (`boss_ward` variant 1, `boss.collector.unsung`). | **Silencing a pylon.** Each `pylon_focus` (a non-living entity, attackable like the vanilla end crystal) holds 30 resonance × (1 + 0.25 × (n − 1)) and loses: saber hit 10; Push 10 (Focused Blast 15); Saber Throw 15; Lightning 10 per second; a parried sentinel lance 15 (inside the Reliquary a parried lance flies toward the crosshair within 12°, so "parry the Watcher's lance into the singing crystal" is a real tactic). **Pull** rips the focus crystal out at once, and **Lift** silences it after a 20-tick channel (both within 12 blocks, line of sight). Hitting a regrowing crystal cancels the regrowth and restarts the 800 ticks. **Lift** on a bound sentinel suspends it at the push-resistant height. The Lash deals 9 damage and knockback 0.6 to grounded players it passes; a normal jump clears it. |
| Transition | at 30% | The remaining pylons shatter into him. He takes up the stolen Sith holocron and his voice drops; his hood goes up and the cloak's edges start to fray into dust (8.1). 60 ticks invulnerable. The bar turns red: "Unkept". | glass shards streaming into him, the sting, shake 0.4 | |
| **3. Unkept** | 30% → 10% | Aggressive, with profile `collector_unkept`: light 7, heavy 12, guard 100, parry 15%, strings of 3 to 4, wind-up 8, recovery 12. **Lunge** across up to 8 blocks for 10 damage. **Holocron Surge** every 300 ticks: radius 8, 10 damage, knockback 2.0. Then he is **Spent** for 60 ticks. **Archive Grip** every 400 ticks, never within 60 ticks of a Surge: the farthest participant is lifted 1.5 blocks and held for up to 80 ticks, taking 2 damage (`archive_grip`) per 20 ticks. The hold is the synced **held** state (`Holds`, 12.3): no gravity, movement and every action except Push swallowed on the client, a HUD hint "Held: Push to break free". He stands still and channels (damage taken ×1.25). | **Lunge:** crouch, dust kicked back, blade drawn low (14 ticks). **Surge:** he raises the holocron for 30 ticks; the air bends toward him, floor dust and grit stream inward, his cloak streams forward and the hum rises. On release, an irregular wall of dust puffs and tumbling debris of varied heights, with gaps, rushes outward along the floor; never a continuous ring and never a flat sprite at floor level. **Grip:** his fingers curl toward the target, and pebbles and dust rise around their feet (20 ticks). | **Surge cover (decided on the server):** at release the server casts two rays from his chest to each participant's eye and chest (block colliders). Both blocked: no damage. One blocked: ×0.5. Airborne at release: ×0.5, which stacks. A participant who casts Push or Deflecting Pulse facing him in the last 8 ticks of the gather **clashes**: they and anyone within 3 blocks behind them take nothing. The client only draws the dust wall and clips it against blocks for looks. **Spent:** guard broken, damage taken ×1.5, full control durations (still with diminishing returns), and Lift lifts him 1.2 blocks with a 10-tick stagger. **Lunge:** Disarming Pull or Push during the tell interrupts it (disarmed for 40 ticks or staggered for 20). **Grip:** broken when allies deal ≥ 8 damage to him, or when the held player casts Push (he staggers for 20 ticks). |
| **Yield** | at 10% | He kneels, damage is refused, and the music drops to a single drone. The owner's dialogue `c3_collector_yield` opens (8.7); helpers with credit get their stance dialogue (8.9). If the owner is dead or away, the heart stays `YIELDED` until the owner returns. | the blade retracts, the crystal light dims, dust settles | |

### 8.4 Resistances and interrupts

**At all times:**

- tags `#oldrepublic:push_resistant` and `#c:bosses`;
- knockback resistance 0.8;
- control diminishing returns (the second control within 200 ticks is half as long, the third does nothing);
- Lightning ×0.6 and Saber Throw ×0.7, except while Spent;
- immune to fall damage, drowning, fire, poison and wither;
- not Lift-able, except while Spent (his `ForceReactions.Handler.liftHeight` returns 0 otherwise, 15.1);
- leashed to the hall box: if he leaves it, he reappears at the dais in a puff of smoke and settling dust (`boss_leash`, `boss.collector.leash`);
- damage from players outside the hall box, or from non-participants, is refused.

**Interrupts.** His handler implements the new `interruptWindup(target, caster, cause)` overload (15.1) and ignores the old one-argument form (cause `unknown`). It allows only:

- the Hurl tell: causes `force_pull` or `pull_disarm` (Lift's catch is handled through `onForceLift` on the debris, not as an interrupt);
- the Palm tell: the push clash (the M2 `onPushHit` path);
- the Lunge tell: `pull_disarm` or `force_push`;
- the Grip tell: `force_push`, `force_pull` or `pull_disarm`, or a single hit of 6 or more, which Vasch's own `hurtServer` detects.

It never interrupts the Surge gather or the transitions. `stagger`, `shock`, `saber_throw` and `heavy_fear` causes are ignored.

### 8.5 Boss bar, music and effects

- **Boss bar:**
  - uses `ServerBossEvent` (API-CHECK, probed in Phase 0);
  - players are added and removed by the arena session, not by entity tracking;
  - name per phase: "Orren Vasch · The Archivist", "· The Singing Wall", "· Unkept"; in a rematch, "Echo of the Collector";
  - colour white, purple, then red; progress is health over max health;
  - fallback if the probe fails: the `boss_bar` payload and a mod HUD bar with the same layout.
- **Music:**
  - three streamed loops (`stream: true` in `events_m3.json`), `music.collector.archivist`, `music.collector.singing_wall` and `music.collector.unkept`, in `SoundSource.MUSIC` so the vanilla music slider controls them, crossfaded over 40 ticks by a client `TickableSoundInstance` and started by `boss_phase` events;
  - while a boss loop plays, the client stops vanilla background music (`MusicManager#stopPlaying`) and holds it off until the loop ends;
  - procedural placeholders, labelled as such in `ASSET_MANIFEST.md`;
  - every loop (music, `pylon.sing`, `boss.collector.ward_hum`) stops on death, leaving the hall, dimension change, disconnect, reset, yield (the drone takes over) and victory;
  - client toggle `ClientConfig.bossMusic` (`/oldrepublicfx boss_music on|off`).
- **Effects:** one event per gameplay moment (13.3).
- **Readability:**
  - presets never thin out the tells: tell particles use the new `Vfx.tellCount`, which ignores the preset multiplier (only ambient and aftermath particles go through `Vfx.count`);
  - with flashing off, transition flashes become steady glows;
  - the hall stays within the preset budgets (Standard: at most 900 mod particles).

### 8.6 Scaling, death, reset and session

- **Health:** 450 × (1 + 0.6 × (n − 1)), where n is the number of participants, capped at 4. If a player joins during phase 1, the maximum rises and the current health keeps its fraction.
- **Scaling:** pylon resonance and sentinels scale with n as in 8.3. Damage is unchanged.
- **Threat:** the participant who dealt the most damage in the last 200 ticks. For the Hurl and the Lunge, the target switches to the nearest other participant every 200 ticks. The Surge hits everyone in range.
- **Death:** the fight continues while any participant is alive inside. A dead participant stays on the bar and may come back in (8.3). If no participant is alive inside for 200 ticks, the fight **resets**:
  - he returns to the lectern at full health;
  - the pylons reset and the sentinels are discarded;
  - item entities inside the hall box are moved to the antechamber floor (keeping their owner and pickup rules), so a dropped saber and armour wait by the bed;
  - the seal opens and the heart returns to `READY`; the next attempt starts at the dais, not the threshold (8.3).

  The antechamber bed keeps retries short. There is no keep-inventory rule: vanilla death rules apply.
- **Session:** an `ArenaSession` in `BossArenas`, held in memory and keyed by the heart position, cleared on `SERVER_STOPPING`. Vasch, the pylon foci and the debris are `noSave`. If every participant logs out, restarts or changes dimension, the fight simply resets.
- **Saved exceptions:**
  - A yield reached is saved: the heart becomes `YIELDED` and `Chapter3.boss` becomes 1. When the owner returns, he kneels at the dais until they choose.
  - `RESOLVED` is saved in the heart and in `Chapter3.boss` (2).

### 8.7 Ending choices (owner only; dialogue `c3_collector_yield`, W2)

| Option | Paths | Effect | Scene (effect `collector_resolve`, variant in brackets) |
|---|---|---|---|
| **"You'll answer to the Watch."** (Spare) | Jedi | Light MAJOR +10 (`collector.spare`). Jedi Order +60, Republic +100, Saelen trust +10. | (0) Two Republic troopers (`Troopers.spawnEscort`) are waiting at the tower stump; they come down the descent, cuff him and walk him up and out, then despawn out of sight. Republic custody, so the Republic reward fits. |
| **"Go, before I change my mind."** (Spare) | Sith | Light MAJOR +10 (`collector.spare`). Sorn trust -25 ("Weak. But it is done."). | (1) He rises slowly, leaves his blade on the dais, and walks out alone. |
| **Execute** | all | Dark MAJOR -10 (`collector.execute`). **Sith:** Sith Empire +80, trust +10. **Jedi:** Saelen trust -25. **Independent:** Frontier +20. | (2) A single stroke. He falls forward, his amber blade rolls from his hand and gutters out, and dust settles over him (no gore). |
| **"Kneel. What you know is mine."** (Bind) | Sith | Dark MAJOR -5 (`collector.bind`), Sith Empire +100, Sorn trust +10. Codex `the_collector_bound`. | (3) Two Imperial troopers arrive with binders, lock his wrists and march him out to the Empire. |
| **"Go. Never come back."** (Release) | Independent | Light MAJOR +5 (`collector.release`), Frontier +50. | (4) He bows his head once, picks up his satchel and walks out alone. |
| **Bring him in** (to the Republic or the Empire) | Independent | No shift (`collector.bring_in`). +100 with the chosen army and 2 of its commendations: the bounty the boards had been posting. | (5) Two troopers of the chosen army escort him out, as in Spare or Bind. |

The choice calls `BossArenas.resolve(owner, outcome)` with `SPARED`, `EXECUTED`, `BOUND`, `RELEASED`, `BROUGHT_TO_REPUBLIC` or `BROUGHT_TO_EMPIRE`. The heart becomes `RESOLVED` and the vault seal opens. W5 plays the scene and calls `QuestHooks.onBossDefeated` once per participant with the `firstClear` flag; W2 grants every reward (8.8). The per-outcome reputation, trust and alignment values are `collector.*` keys (14).

### 8.8 Rewards (guaranteed; flag-guarded per player)

**W2 grants every Collector reward** from `QuestService.onBossDefeated`, using the item ids Phase 0 registers (15.1), including the rematch rewards and the pity counter. W5 only runs the fight and calls the hook.

| Who | Rewards |
|---|---|
| Owner, first clear | 3,000 XP; +2 SP (flag `c3.collector_sp`); Resonance `collector` +600; `pommel_archivist` and a `singing_crystal` (both dropped for the owner alone if the inventory is full); codex `the_collector`; journal entry for the outcome; banner `boss` (`lesson_complete` variant 6) |

- `pommel_archivist` (`HiltParts.Pommel.ARCHIVIST`): parry window +2 ticks, attack speed -0.05.
- `singing_crystal`: a kyber crystal item, purely cosmetic like every M1 crystal. Its natural hue is the new `BladeColor.AMBER` (appended), and a blade built with it carries a resonant hum layer, `saber.hum.singing`, over the hilt's normal hum. The chapter is about singing crystals; this one is the player's own.

### 8.9 Co-op credit, rematch and Unchained

- **Co-op credit.** A helper whose own chapter is at `RELIQUARY` or `CONFRONTATION` gets the full owner rewards above. When Vasch yields, each such helper gets their own **stance** dialogue, `c3_collector_stance`, with the options of their own path (8.7). Their pick decides their own alignment, reputation, trust and journal entry (`c3:stance`); the world outcome (custody, death, release) follows the owner. If the owner's outcome clashes with the helper's stance, the helper's mentor report has a line for it ("You let the Empire take him?"). Their Chapter Three then jumps to `AFTERMATH` ("You fought beside <owner>."), and `HolocronService.markArchivePending` lets them make their own Archive choice at the same hoard plinth. Their own Reliquary, if it was placed, stays as an empty ruin. This is the M2 per-player story rule, accepted and documented.
- **Other helpers** (any other stage): 1,000 XP and 1 `holocron_shard`, once per in-game day (`StoryData` counter `c3.helper_day`).
- **Rematch, "Echo of the Collector":**
  - After `RESOLVED`, using the heart offers "Call the echo" (dialogue `reliquary_heart`, W5, action `boss:call_echo`). The owner starts it; others can join as participants.
  - The echo forms from smoke over 60 ticks and has the same phases, with damage ×1.15. It does not yield: at 0 health it dissolves into smoke that drains into the floor (`boss_echo` variants 0 and 1, `boss.collector.echo_form` and `boss.collector.echo_fade`).
  - Each participant's reward, once per in-game day (granted by W2 from `onBossDefeated` with `BossOutcome.ECHO`): 600 XP, 2 durasteel, 1 `holocron_shard`, and a cache roll (`gameplay/echo_cache`) for `emitter_reliquary` at 25%.
  - Bad-luck protection: the emitter is guaranteed on a player's 4th clear without one (counter `c3.echo_pity` in `StoryData` counters).
- **Unchained** (offered after the first echo clear, action `boss:unchained`): health ×1.5, damage ×1.25, phase 3 begins at 40%, two cache rolls, and 900 XP instead of 600.
- `emitter_reliquary` (`HiltParts.Emitter.RELIQUARY`): aimed deflections go within 12° of the crosshair (instead of 20° with `deflection_control`, or the random 50° arc without it), and parried bolts deal +15% (through the new `redirect(..., damageMultiplier)`, 15.1).

---

## 9. Era armour

W4 owns this section (`com.oldrepublic.equipment`). Gabriel moved these four sets from the brief's Milestone 4 into Milestone 3.

### 9.1 Sets and stats

Each set has its own build role. Pieces can be mixed: per-piece traits always apply, and a set bonus needs all four pieces (4/4).

| Set (item ids) | Armour H/C/L/B | Toughness / knockback resistance | Per piece | Set bonus (4/4) |
|---|---|---|---|---|
| **Republic trooper armour** `republic_trooper_helmet`, `_chestplate`, `_leggings`, `_boots` | 2/6/5/2 (15) | 0.5 / 0 | blaster damage taken -6%; Force cost +5%; Force regen -5%; move speed -1.5% | **Shoulder to Shoulder:** a further -10% blaster damage; guard stamina drain from bolts -25% |
| **Sith trooper armour** `sith_trooper_helmet`, `_chestplate`, `_leggings`, `_boots` | 2/6/5/2 (15) | 1.0 / 0.05 | melee damage taken -4%; Force cost +5%; Force regen -5%; move speed -1.5% | **Iron Discipline:** control durations on the wearer -30% for every `ControlEffects` kind (`stagger`, `slow`, `disarm`, `shock`, `hold`), which covers the parry stagger, vent and Lightning slowness, the Archive Grip and Lift holds; and knockback taken -20% (a +0.2 knockback-resistance modifier while 4/4) |
| **Jedi robes** `jedi_hood`, `jedi_robe` (or `jedi_knight_robe`), `jedi_trousers`, `jedi_boots` | 1/3/2/1 (7) | 0 / 0 | Force regen +6%; Force cost -2% | **Calm of the Watch** (alignment ≥ +15, or an Independent in the Balanced band): a successful parry, melee or bolt, restores 5 FE; Force Heal cost -15% (for Independents, Lift instead) |
| **Sith robes** `sith_hood`, `sith_robe` (or `sith_lord_robe`), `sith_trousers`, `sith_boots` | 1/3/2/1 (7) | 0 / 0 | max Force Energy +5; Force cost -2% | **Hunger of the Tomb** (alignment ≤ -15, or an Independent in the Balanced band): a kill within 160 ticks of hitting that target with a power restores 8 FE; Force Lightning cost -15% (for Independents, Lift instead) |

- **Clamps:** the combined `ForceModifiers` cost product stays within 0.70–1.50, and the regen product within 0.50–1.50.
- **Stacking caps:** all FE refunds from one kill (`hungering_dark`, `tomb_hunger`, Hunger of the Tomb) together give at most 12 FE (`progression.killRefundCap`), and all FE refunds from one parry at most 6 (`progression.parryRefundCap`).
- **Worked example:** a full trooper set gives cost ×1.216, regen ×0.815 and speed -6%. Full Jedi robes give regen ×1.262 and cost ×0.922.
- **Durability multiplier:** 18 for trooper sets, 10 for robes. **Enchantability:** 9 for trooper sets, 15 for robes. The pieces are in the vanilla `enchantable/*` item tags for their slot (`head_armor`, `chest_armor`, `leg_armor`, `foot_armor`, plus `armor`, `equippable` and `durability`).
- **Repair materials:** each `ArmorMaterial` takes an item tag: `oldrepublic:repairs/trooper_armour` (`durasteel_plate`), `repairs/jedi_robes` (`lumen_thread`), `repairs/sith_robes` (`grave_silk`).
- **Tooltips:** `appendHoverText` shows the exact numbers. The live set line ("Set 4/4 · requires Light (+15)") needs the viewer's worn armour and alignment, which `appendHoverText` does not receive in 26.2, so W4 adds it on the client through Fabric `ItemTooltipCallback.EVENT`, reading `Minecraft.getInstance().player`.

### 9.2 How traits apply

- **Armour, toughness, knockback resistance and speed** are vanilla attributes, merged into **one** `ItemAttributeModifiers` per item. They must be merged because `.attributes()` replaces what `humanoidArmor` sets, so `.attributes(merged)` is called after `.humanoidArmor(...)`.
- **Modifier ids are per attribute and slot** (frozen in Phase 0): `oldrepublic:armour.<slot>.armor`, `.toughness`, `.knockback` and `.speed`, for example `oldrepublic:armour.chest.speed`. Vanilla does the same with `armor.<type>`. Equipment changes remove and re-add modifiers by id, so one shared id would collapse a full set's -6% speed into one piece's -1.5%. Each piece is built with `ItemAttributeModifiers.builder().add(attr, new AttributeModifier(id(slot), value, op), EquipmentSlotGroup.bySlot(slot))`.
- **Every other trait** goes in the data component `oldrepublic:equipment_traits` = `EquipmentTraits(set, costPct, regenPct, maxEnergy, blasterResist, meleeResist)`. W4's registrations read it:
  - `ForceModifiers` for cost, regen and max energy;
  - `DamageModifiers` for blaster and melee resistance;
  - `ControlEffects.registerDurationModifier` for Iron Discipline;
  - `CombatEvents.PARRY` and `POWER_HIT` for Calm and Hunger.

  Nothing writes to the `ForceData` counters.
- **"Blaster damage"** means damage whose direct entity is a `BlasterBolt`. **"Melee"** means a direct attack by a living attacker.
- On an armour change (Fabric `ServerEntityEvents.EQUIPMENT_CHANGE`; there is no inventory-change event), W4 calls `Progression.refreshModifiers(player)`, which recomputes max energy at once, and `QuestHooks.onArmourEquipped` for the codex. Completing a set sends `armour_set` (toPlayer) once per equip.

### 9.3 How to get it (anti-farm economy)

Troopers never drop armour or commendations. Trooper sets cost durasteel plus commendations, which come only from faction work (5.6). Robes need exploration resources.

| Set | Recipe (vanilla crafting table, armour shapes) |
|---|---|
| Republic trooper | helmet 4 `durasteel_plate` + 1 `republic_commendation`; chestplate 7 + 1; leggings 6 + 1; boots 3 + 1. A full set costs 20 durasteel and 4 commendations. |
| Sith trooper | the same, with `imperial_commendation` |
| Jedi robes | hood 4 cloth + 1 `lumen_thread` (helmet shape, 5 slots); robe 6 cloth + 2 thread (8); trousers 6 cloth + 1 thread (leggings shape, 7); boots 2 leather + 1 cloth + 1 thread (4). Cloth is `#oldrepublic:jedi_robe_cloth` (white, light grey or brown wool). |
| Sith robes | the same with `grave_silk`, and cloth from `#oldrepublic:sith_robe_cloth` (black, grey or red wool) |

Every recipe fills a vanilla armour shape exactly. Path players get the hood and robe at the Chapter Three lesson; the trousers and boots are crafted; rank 3 gives the Knight's robe or Lord's robe, which counts as the robe of the set (5.8). W2 gives them through `ArmourSets.piece`.

### 9.4 Assets and rendering

- **Probe first:** the lead runs `m3_equipment.txt` before workers start (15.1).
- **Items:** `ModRegistry.item(id, Item::new, new Item.Properties().humanoidArmor(MATERIAL, ArmorType.X).attributes(merged))` (API-CHECK).
  - One `ArmorMaterial` per set.
  - Equip sounds `armour.equip_trooper` (clasp clicks) and `armour.equip_robe` (a cloth swish), as `Holder`s through the Phase 0 helper `ModSounds.holder`.
- **Equipment assets:** `assets/oldrepublic/equipment/{republic_trooper,sith_trooper,jedi_robes,sith_robes}.json`, with layers `humanoid` and `humanoid_leggings`. Textures are 64×32 at `textures/entity/equipment/humanoid[_leggings]/<asset>.png`; the probe confirms the paths.
- **Robe custom model** (client option `ClientConfig.robeModels`, default on): a Fabric `ArmorRenderer` for the 10 robe items (including the two rank 3 robes) with:
  - a hood with a brim;
  - a robe with sleeves and a back panel (the rank 3 robes add a mantle and sash);
  - trousers with front and back skirt panels that follow the legs;
  - boot wraps.

  Textures are 64×64. Fabric cancels vanilla armour rendering for any item with a registered `ArmorRenderer`, and registration is permanent, so the renderer itself handles the option:
  - it is registered through the `ArmorRenderer.register(ArmorRenderer.Factory, items...)` overload, keeping `context.getEquipmentRenderer()` and armour models baked from `ModelLayers.PLAYER_ARMOR` (and `PLAYER_SLIM_ARMOR`);
  - option on: `ArmorRenderer.submitTransformCopyingModel(...)`, so the robes follow the saber and Force poses;
  - option off: it copies the pose from the context model and calls `EquipmentLayerRenderer#renderLayers` with `LayerType.HUMANOID` or `HUMANOID_LEGGINGS` for the piece's asset, which draws the plain equipment layers;
  - hoods override `shouldRenderDefaultHeadItem` to return false.

  If the probe shows a renderer cannot be registered, the plain equipment layers ship (cut item 5).
- **Troopers** use composited skins (7.1), so mobs need no armour layer.
- **Textures:** `tools/assets/gen_textures_equipment.py` (W4, original PIL art) exports the palette and the compositing function that W4's `gen_textures_trooper.py` imports.

### 9.5 Uniform disguise (stretch goal, first on the cut list)

A full trooper set of one army makes that army's troopers treat the wearer one attitude step friendlier: Hostile becomes Wary, and Wary becomes Neutral. It never goes past Neutral.

- The disguise breaks for 2,400 ticks when the wearer uses a Force power or lights a saber within 24 blocks of any of that army's troopers.
- It never applies to incident squads or mission encounters.
- `ArmourSets.disguise(entity)` returns the `Allegiance`, or `NONE` when the feature is cut.

---

## 10. Abilities, skills and other systems

The M2 ability rules all carry over:

- the server decides;
- every power goes through `Progression.trySpendEnergy` and `AbilityCosts`, which now includes `ForceModifiers.cost`;
- cooldowns live in `ForceData.cooldowns`;
- powers are cancelled on death, disconnect, dimension change, a screen opening, or a slot change;
- each gameplay moment sends one effect event;
- the client VFX go through the `Vfx` budgets and never hide enemy tells.

**No M3 power changes terrain.** Puzzle blocks change only their own state.

### 10.1 Force Sense (`force_sense`, Universal; W3)

| Field | Value |
|---|---|
| Purpose | Find what is hidden (veiled stone, cairns and caches, anchors, urn echoes, creatures out of sight), point toward unattuned holocrons, and read danger. |
| Unlock | Granted by the Chapter Three lesson (Jedi, Sith) or by `wayfarers_compass` (Independent). `granted_only`. It is the root of the Exploration tree. |
| Input | **Its own key, default H** (`key.oldrepublic.force_sense`), TAP. It sends `AbilityInputPayload(SENSE_SLOT = 8, START)`. It never takes an ability slot. It is refused, with the M1 failure feedback, while another power is charging or channelling. |
| Cost / cooldown | 15 FE / 240 ticks |
| Range | Creatures within 24 blocks; blocks within 16; holocron search 256 blocks (512 with Seeker's Sight). The reveal lasts 160 ticks. |
| Effect | The server validates the cast and collects `SenseTargets` from every provider (at most 32 points). It sends `sense_reveal` to the caster and the effect `force_sense` to tracking players. Providers: W1 reveals veiled stone within 16 (POI query, a shared block state) and makes an urn keeper within 12 play its order; W2 reveals the owner's own cairns within 24 (`EchoCairns.reveal`); W3 adds creatures, classed by `Attitudes`. **Holocron direction:** `StructureLocator` is asynchronous, so the cast reads `StructureLocator.cached(p, kind)` at once and queues a refresh. If the cache holds a result, its direction goes in the event's `dir` and its closeness (0 to 1) in `intensity`, with variant 1. If a fresh result arrives within 40 ticks of the cast, the caster gets a follow-up `calling` variant 4 pulse toward it. A cold first cast therefore shows the reveal at once and the holocron pulse a moment later. |
| Client (caster only) | A 1 s inhale, and mod ambience dips by 30% for 20 ticks. Motes lift around the player and drift outward. For each point, a thin stream of dust motes runs from near the player toward it and settles into a small cluster of slow-drifting motes at chest height. On a creature the cluster follows it for 160 ticks, tinted by attitude: hostile ember orange, wary amber, neutral grey-white, ally pale blue. Blocks get pale gold. Revealed veiled stone seeps dust on its own (its block state). For the holocron, three soft pulses of Calling variant 4 move in its direction. **No outline, no glow, no ring and no ground wave.** |
| Branches (group `sense_shape`, level 8, 1 SP) | **Danger Sense** (`sense_danger`): passive while owned. A danger tell within 12 blocks **behind** the player (outside a 100° front cone) plays the directional prickle `force.sense.danger` (at most one per 20 ticks) and a faint dust tick at that edge of the view. It runs on the client from the frozen tell table `DangerTellTable.isTell(id, variant)` (common code, so the server tests it; the client `DangerTells` delegates, 15.1). **Seeker's Sight** (`sense_seeker`): holocron search 512 blocks, reveal +80 ticks, and pedestals (POI) and site loot chests (the `CHEST_n`, `CACHE`, `DECK_CACHE` and `ANCHOR_LOCKER` anchors of the sites within 24 blocks, through `StructureLocator.pieceAt` and `WorldSites`) glint pale gold. W1's provider emits them as `Kind.CACHE` points when the caster owns `sense_seeker`; no block scan. |
| Mastery | +1 per cast that reveals at least one hidden point: veiled stone, a cairn, an anchor, an urn, or a creature out of line of sight. Thresholds 50 / 150 / 400. Each rank: -1 FE and +4 blocks creature range. |
| Counterplay | Other players are never revealed unless `force_sense.pvpEffects` is on (default false); then a sensed player feels a prickle: "You feel watched." |
| Animation | Head tilts, two fingers to the temple (free-arm pose `SENSE`, 10 ticks). |
| SFX | `force.sense.cast` (an inhale and a soft resonant swell), `force.sense.reveal` (a soft tick per hidden point, at most 6 per cast, staggered), `force.sense.danger` |
| Multiplayer | Reveal visuals are for the caster only. Others see the gesture and a faint breath of dust around the caster. The holocron search uses `StructureLocator`'s shared budget. |

### 10.2 Telekinetic Lift (`force_lift`, Independent signature; W3)

| Field | Value |
|---|---|
| Purpose | Control a single foe, open rubble-blocked caches, and give Independents their own signature power. |
| Unlock | `wayfarer_pell` (Independent). Exploration tree node with `path: independent`, `granted_only`. |
| Input | **Hold (channel).** Can be put in any slot. |
| Cost / cooldown | 20 FE to start + 8 FE per second; holds up to 60 ticks; cooldown 280 ticks from release |
| Range and targeting | 14 blocks. The crosshair ray, then an 8° cone (the Pull rules), with line of sight. Order (10.6): a `ForceReactiveEntity` on the ray first (`onForceLift`: debris, pylon focus, grenade), then a living target, then a `collapsed_rubble` block on the aim (`ForceAct.LIFT`). Mobs up to 2.4 blocks tall (ruin sentinels included). Players only with `force_lift.pvpEffects`. |
| Effect | The target rises to 2.5 blocks over 10 ticks (push-resistant: 1.2) and hangs with gravity off. Its movement goals are paused and it cannot attack (a hold through `ControlEffects.diminished(..., "hold")`, with diminishing returns). On release, or at the end of the hold, it is **slammed** down: 6 damage and a 15-tick stagger. **Rubble** crumbles after 20 ticks of channel. **Bosses:** a living target's `ForceReactions.Handler.liftHeight` decides (0 refuses); Vasch allows 1.2 blocks with a 10-tick stagger only while Spent. **In the Reliquary:** Lift catches a Hurl chunk during its tell, silences a pylon after a 20-tick channel, and suspends bound sentinels (8.3). **On players** (PvP option only): the synced held state (12.3), never velocity nudges. The channel stops if the caster takes 6 or more damage. |
| Branches (group `lift_shape`, level 9, 1 SP) | **Suspend** (`lift_suspend`): hold up to 100 ticks; allies deal +20% to the lifted target. **Hurl** (`lift_hurl`): release throws the target 10 blocks along the aim, dealing 6 damage to it and to the first entity it hits, with a 20-tick stagger. |
| Mastery | +1 per hostile held for 20 ticks or more. Each rank: -5% cost and +1 block range. |
| Counterplay | Line of sight, one target, heavy targets lifted less, bosses only while Spent, interrupted by damage. |
| Animation | Free arm raised, palm up, fingers spread, a slight tremble (free-arm pose `LIFT`). The target floats untethered, turning slowly, its arms and legs flailing for balance; there is no throat or chest constriction, which stays reserved for Choke (M4) and Vasch's Grip. |
| VFX | Under the target, a column of bent-air shimmer (`lift_shimmer` particles, no disc), with pebbles and dust falling from its feet. Slam: a dust burst and block particles of the landing block. Hurl: a bent-air streak along the path. |
| SFX | `force.lift.start`, `force.lift.loop` (stops on every cancel path), `force.lift.slam`, `force.lift.hurl` |
| Multiplayer | Effects `force_lift` (variants: start, end, slam, hurl) and `force_lift_target` (entity). A lifted player gets the held state through `Holds` (12.3). |

### 10.3 Exploration and Survival tree (`skill_tree/exploration.json`, order 4)

The tree is visible when `TreeVisibility.SENSE` holds, that is, when `force_sense` is owned. "Lv" is the minimum level per rank.

| Id | Kind | Cost | Lv | Req | Effect |
|---|---|---|---|---|---|
| `force_sense` | ability, granted | – | – | | 10.1 |
| `sense_danger` / `sense_seeker` | branch, group `sense_shape` | 1 | 8 | force_sense | 10.1 |
| `trailwise` | passive ×2 | 1 / 1 | 7 / 10 | force_sense | Fall damage -20% / -40% (a vanilla `FALL_DAMAGE_MULTIPLIER` attribute modifier; no mixin); sprint hunger -15% / -30% (one mixin on `Player#causeFoodExhaustion` that scales the amount while sprinting, in W3's `mixin/skill`) |
| `relic_lore` | passive | 1 | 8 | force_sense | Holocron XP +25%. Under Sense, puzzle blocks that still need a Force act (counterweights not yet sunk, urns, anchors) show a faint pale-gold dust stream. |
| `hardy` | passive ×2 | 1 / 2 | 9 / 12 | trailwise 1 | Max health +2 / +4 |
| `scavenger` | passive | 1 | 9 | trailwise 1 | Trooper and sentinel kills have a 35% chance of +1 `durasteel_plate`, `lumen_thread` or `grave_silk` (never commendations or armour) |
| `quiet_step` | passive | 2 | 11 | force_sense | Dormant sentinels wake at 4 blocks instead of 6. Wary troopers warn at 10 blocks instead of 16 and attack after 200 ticks instead of 120. |
| `force_lift` | ability, granted, path Independent | – | – | | 10.2 |
| `lift_suspend` / `lift_hurl` | branch, group `lift_shape` | 1 | 9 | force_lift | 10.2 |
| `archivists_road` | passive, granted | – | – | | From keeping the Archive: Sense cooldown -30%, Lift cost -10% |
| `pathfinders_poise` | passive, granted | – | – | | Pathfinder: +10% move speed out of combat; fall damage -25% (all fall reductions combined cap at -60%) |

Purchasable: 10 SP for path players, 11 for Independents. M3 earns about 11 to 12 SP (14.1), so builds still differ.

### 10.4 Holocron and rank nodes (granted only; M2 dormancy rules)

| Node | Tree | Active when | Effect | Implemented by |
|---|---|---|---|---|
| `watchers_insight` | jedi | alignment ≥ +15 | A parry, melee or bolt, restores 3 FE; Force Heal cost -10% | two separate node checks: W5 implements the parry FE (keys in `saber`), W3 the Heal cost (key in `skills`) |
| `mending_breath` | jedi | ≥ +15 | Force Heal also clears Slowness, Weakness and Poison from its targets | W3 |
| `tomb_hunger` | sith | ≤ -15 | A kill within 60 ticks of hitting that target with a power restores 6 FE | W3 |
| `crackling_grasp` | sith | ≤ -15 | Force Lightning leaves its target 30% slower for 40 ticks after it ends | W3 |
| `archivists_ward` | jedi | ≥ +15 | Parry window +2 ticks | W5 |
| `archivists_hunger` | sith | ≤ -15 | Push and Lightning deal +15% to targets above 75% health | W3 |
| `archivists_road` | exploration | always | 10.3 | W3 |
| `knights_vigil` | jedi | rank 3, Jedi path, ≥ +15 | While guarding, allies within 6 blocks take -10% damage (`DamageModifiers`) | W5 |
| `lords_dominion` | sith | rank 3, Sith path, ≤ -15 | After the Lord lands a heavy attack, non-boss enemies within 8 blocks deal -10% damage for 100 ticks | W5 |
| `pathfinders_poise` | exploration | title Pathfinder | 10.3 | W3 |

Holocron nodes carry an alignment requirement only, with no path requirement. A Sith who listens to a Jedi holocron therefore owns the node, and it stays dormant while they are Dark. The Jedi and Sith tabs are already visible to anyone past the Crossing (M2), so dormant nodes show there. W3 adds these granted nodes to `skill_tree/jedi.json` and `sith.json` (W3 owns both files in M3, 15.2). The balance keys of the nodes W5 implements live in W5's `saber` section (14).

**Stacking limits.** Parry window: base 6 ticks, plus the flat bonuses (`centered_mind`, `archivists_ward`, `pommel_archivist`), then the form multiplier (Soresu ×1.5), then the cap `saber.parryWindowCap` (11 ticks). FE refunds are capped per kill and per parry (9.1). `SaberGameTests` checks the order and the caps.

### 10.5 Rule changes

- `TreeVisibility.SENSE` is appended.
- Phase 0 adds the overload `SkillRules.visibleTrees(SkillTreeSet, boolean pushKnown, boolean saberBuilt, boolean crossed, boolean senseKnown)`. The old method delegates with `senseKnown = false`, and `Skills.context` uses the new one.
- `AbilityRuntime` (W3) routes slot 8 to `force_sense`. Slots 0 to 3 are unchanged.
- Respec refunds purchased Exploration nodes. Granted nodes are kept (the M2 rule).

### 10.6 Force-reactive blocks and entities

- **Push** (W3): after the normal cone, `ForceBlockInteractions.findOnAim(caster, pushRange)`, then a `PUSH` dispatch. The cone also reaches `ForceReactiveEntity` entities (the grenade, debris and pylon foci) and calls `onForcePush`; a `true` result skips the normal knock. Every living target the Push moves also gets `ForceReactions.interruptWindup(target, caster, "force_push")`.
- **Pull:**
  1. a `ForceReactiveEntity` on the ray first;
  2. otherwise a living target;
  3. otherwise a reactive block on the aim (`PULL`);
  4. otherwise items, as in M2.
- **Lift:**
  1. a `ForceReactiveEntity` on the ray first (`onForceLift`);
  2. otherwise a living target, asking `ForceReactions.Handler.liftHeight` (0 refuses);
  3. otherwise a reactive block on the aim (`LIFT`).
- **Lightning** (lead adds the hook in Phase 0): a `ForceReactiveEntity` on the aim ray receives `onForceLightning(caster, perTick)` each tick of the channel instead of a living target.
- **Saber Throw** (lead adds the hook in Phase 0): a thrown saber that touches a `ForceReactiveEntity` calls `onSaberThrowHit(caster, damage)`.
- **Sense:** providers only (10.1). There are no block scans; providers use POI queries.
- Puzzle handlers are registered by W1. Every handler runs on the server and returns `true` when it consumed the act.

### 10.7 Saber additions (W5)

- **NPC bolt deflection.** `SaberGuard.onProjectile` now also handles `SaberDuelist` NPCs whose profile has `deflects` (Vasch, and the acolyte from M2). If the NPC is `GUARDING` and faces the shooter within 70°, the bolt is sent back toward the shooter's chest within 15°.
- **Heavy bolts** (`BlasterBolt.heavy()`, the sentinel lance only): a normal guard blocks with a 35 stamina drain and no redirect. Only a parry returns it, straight at the shooter, except inside the Reliquary (`BossArenas.isInArena(player)`), where it goes toward the crosshair within 12° so it can be sent into a pylon focus (8.3). The returned bolt is marked `parried()`.
- **`saberDeflectable() == false`** (`archive_debris`): a guard blocks with a 30 stamina drain and never deflects. `archive_debris` still implements the other `DeflectableProjectile` methods (`redirect` is a no-op).
- **Parry events.** Every parry, melee or bolt, fires `CombatEvents.PARRY` once (the lead's Phase 0 change to `onBoltDeflected`, 15.1), so Calm of the Watch and `watchers_insight` work against bolts.
- **Hilt parts** appended to `HiltParts`: `Pommel.ARCHIVIST` (`pommel_archivist`) and `Emitter.RELIQUARY` (`emitter_reliquary`), with stats in `SaberStats` (8.8, 8.9). Phase 0 freezes the constants and registers the items (15.1); W5 adds the stats, the textures in `gen_textures_saber.py` and the lightsaber item-model parts (`models/item/lightsaber/pommel_archivist.json`, `emitter_reliquary.json`).
- **Singing crystal:** `singing_crystal` item, `BladeColor.AMBER` appended, hum layer `saber.hum.singing` (8.8).

### 10.8 Commands (all take a single player argument, `@p` style)

| Command | Owner |
|---|---|
| `/oldrepublic c3 stage <player> <stage>`, `/oldrepublic c3 lead add <player> <lead>`, `/oldrepublic c3 reliquary <player>` | W2 |
| `/oldrepublic mission start <player> <id>`, `… complete <player>`, `… abandon <player>`, `… offers <player>` | W2 |
| `/oldrepublic holocron grant <player> <id>`, `… reset <player>`, `… shards <player> <n>` | W3 |
| `/oldrepublic site place <kind> <pos>` (jigsaw structures go through the same code as vanilla `/place structure`; runtime templates through `RuntimeSites`), `… locate <player> <kind>`, `/oldrepublic outpost ensure <player>` | W1 |
| `/oldrepublic trooper spawn <faction> <role> <pos>`, `… squad <faction> <pos> [incident]`, `/oldrepublic armour give <player> <set>` | W4 |
| `/oldrepublic sentinel spawn <variant> <pos> [bound]`, `/oldrepublic boss place <player>` (places the Reliquary without W2's stage logic, so W5 can test alone), `/oldrepublic boss start <player>`, `… phase <player> <n>`, `… reset <player>` | W5 |

---

## 11. Controls and HUD

### 11.1 Key plan

| Action | Default | Category | Notes |
|---|---|---|---|
| Force Sense | H | Echoes of the Old Republic | `key.oldrepublic.force_sense`. The Phase 0 probe checks the vanilla 26.2 defaults for H; if H is taken, the default becomes U. |
| Force Power slots 1–4 | R, Z, V, B | same | Unchanged. Lift is slotted here (hold). |
| Force screen, Journal, Toggle Lightsaber, Saber Form | K, J, G, unbound | same | Unchanged |

- None of the defaults clash with vanilla 26.2 or with each other; a client test checks this, now including H.
- All keys can be rebound.
- A mission tracker toggle uses the client command `/oldrepublicfx tracker on|off`, not a key.

### 11.2 HUD

- **Sense pip** (W3, `client/hud`): a small eye-shaped pip to the right of the ability sockets, with a cooldown sweep and an amber rim when energy is too low.
- **Mission tracker** (W2): 5.7.
- **Boss bar** (W5): the vanilla boss bar at top centre, or the fallback bar with the same layout (8.5).
- **Barks** (W4): one line low in the centre, above the hotbar, for 60 ticks, with a faction-tinted speaker tag ("Republic Sergeant:"). Client option `ClientConfig.showBarks`.
- **Held hint** (lead, part of `Holds`, 12.3): while the player is held by the Archive Grip or a PvP Lift, a short line under the crosshair: "Held: Push to break free" (or "Held" when Push is not allowed).
- **Toasts:** holocron attuned; site discovered (W2); mission update; set bonus active (once per equip).
- **Banners** (`Banners.show`): holocron attuned, mission complete, boss phase names, the Chapter Three report, and rank 3 ("Knight of the Watch", "Lord of the Empire", "Pathfinder").

### 11.3 Screens

- **Journal:** the Chapter Three page, plus the tabs **Holocrons** (W3) and **Missions** (W2) through `JournalTabs`.
- **Board screen** (W2): 5.7.
- **Standing tab** (W2, `StandingPanel`): 4 faction rows, the titles row, and the rank 3 checklist.
- **Force screen** (W3): a fifth tab, Exploration.

---

## 12. Persistence, multiplayer, dedicated server and config

### 12.1 Saved state

All player attachments follow the M2 pattern: persistent, kept on death (`copyOnDeath`), synced to their owner only (`targetOnly`), and **sealed**. `seal()` stores a frozen `syncView = copy()`, and code commits with `setAttached(data.copy().seal())` and calls `get` again after anything that commits. Codecs only add fields (`optionalFieldOf`). Enums are stored by ordinal, append only.

| Attachment / storage | Owner | Contents |
|---|---|---|
| `oldrepublic:story` | W2 | New record `Chapter3`: `stage`, `wait_since`, `leads` (≤ 16), `cairns` (≤ 3 positions), `cairns_touched` (mask), `fall`, `shrines` (≤ 4 positions), `reliquary` (positions), `boss` (0 none, 1 yielded, 2 resolved), `outcome` (-1 or `BossOutcome`), `stance` (-1 or `BossOutcome`, helpers), `echo_day`, `echo_clears`. New `flags` (`c3.*`, including `c3.collector_sp`, `c3.vasch_message`, `c3.ambush`, `c3.vasch_offer`) and `counters` (`c3.echo_pity`, `c3.helper_day`). The Archive choice is **not** stored here (only in `HolocronData`). |
| `oldrepublic:missions` (`MissionData`) | W2 | `active` (id, kind, stage, site, progress, target, started_day, seed, issuer, board); `story_done` (≤ 8); `contract_day`, `contracts_today`; `board_day`, `used_offers` (terminal position + offer index, ≤ 16); `own_side` (issuer → count, ≤ 4); `duty_day`; `discovered` (site kinds, ≤ 16); `surveyed` (≤ 32 positions); `pull_until` |
| `oldrepublic:holocrons` (`HolocronData`) | W3 | `attuned` (≤ 16), `choices` (id → `HolocronChoice`, ≤ 16), `returned` (≤ 16), `attuned_at` (pedestal dimension + position + structure start or site key → id, ≤ 16), `serials` (id → int, ≤ 16), `echoes` (≤ 64 pedestal positions), `milestones` (mask), `archive` (-1 or choice), `archive_pending`, `restore_day` |
| `oldrepublic:identity` | W2 | `reputation` gains `republic`. `choices` gains `title.*` and the M3 choice ids. New `kill_rep` (day and per-faction totals of the capped -10 losses). |
| `oldrepublic:held` | lead (`Holds`) | Transient but synced to its owner: hold end tick, source, anchor position, whether Push is allowed. Not persistent: cleared on load. |
| Global `oldrepublic:world_sites` (`WorldSites`) | lead skeleton, W1 | Runtime outposts and runtime site records (≤ 512): kind, dimension, origin, rotation, template, owner, placed day; plus the expired cairn owners (≤ 64 UUIDs). Stored through Fabric `GlobalAttachments` (`server.globalAttachments()`, saved with the world). Server only; never synced. The rules work on a detached `WorldSites.Data` value so tests never touch the server's copy. |
| Block entities | W1, W5 | `bounty_terminal` (officer and garrison UUIDs, refill time, failed-check count), `urn_keeper`, `holocron_pedestal` (kind, `fixed_id`), `echo_cairn`, `site_spawner` (W1); `reliquary_heart` (owner, state, `firstClear`, `attempts`, echo flags) (W5) |
| Item components | W3, W4 | `oldrepublic:holocron` (id, owner, serial); `oldrepublic:equipment_traits` |
| Entities | | Garrisons, officers, mission and worldgen troopers and sentinels: persistent, with owner and encounter tags where owned. Troopers save `Role`, `Squad`, `Incident`, `Provoked`, `Engaged`, `BleedAt` (downed) and `Terminal` (the bounty terminal they belong to, if any); sentinels save `Variant` and `Bound`. Natural patrols: not persistent. Vasch, escorts, `pylon_focus`, `archive_debris` and `frag_grenade`: `noSave`. |

### 12.2 Edge cases

| Event | Behaviour |
|---|---|
| Death | Lift, Sense visuals, the Grip (the held state) and dialogues end. A lifted target drops. A dead participant stays on the boss bar, can come back in through the seal (8.3), and the fight goes on while any participant is alive inside. The active mission continues. |
| Respawn | At the Reliquary bed if the player set it. Energy refills and cooldowns clear (the M1 behaviour). |
| Dimension change | Sense and Lift cancel; the held state clears. A boss participant leaves the bar after 100 ticks. Missions and Chapter Three sites are overworld only and wait. |
| Relog | Dialogue closed with no choice: a holocron is not attuned, and the yield is still pending. The held state is cleared on load. If no participant remains, the boss fight resets. |
| Server restart | Sessions are cleared and persistent entities stay. Vasch (`noSave`) is spawned again by the heart when the owner comes near. The yielded and resolved states are kept. |
| Lost holocron | Sneak-use at any pedestal of its kind restores it once per in-game day, with no rewards; never a returned, broken or (unless kept) Archive id, and the old copy becomes stale (4.3). |
| Lost mission item (`archive_record`, `fence_book`) | Re-issued at the mission site (the crate or tent) while the mission is active. |
| Duplicate officer or garrison member | Discards itself on load when its terminal lists a different UUID for its slot; the terminal respawns only after two failed checks with its entities loaded (3.3). |
| Peaceful | 6.9: nothing is removed; troopers never attack players; combat objectives resolve on arrival; the Reliquary has no fight. |
| Mission site in unloaded chunks | Entities spawn when the owner first comes within 48 blocks (the M2 rule). |
| Puzzle state when another player arrives | Shared: an open puzzle stays open for whoever comes next, until it resets after 1,200 idle ticks. Rewards stay per player. |
| Griefing | Puzzle, pedestal, terminal, heart and seal blocks are unbreakable in survival. Decor can be broken and does not regrow. |

### 12.3 Multiplayer

- Every power, puzzle act, trooper tell, boss tell and holocron moment sends compact effect events, and clients build the particles and sounds locally (the M1 rule).
- **Per player and owned:** Courier's Fall, Echo Shrines, mission camps, the Reliquary, cairns, mission NPCs and choices. Kills in an owned encounter by anyone count for the owner (the M2 rule).
- **Shared:** worldgen structures and their puzzle states, outposts, officers and garrisons. Pedestals are shared, but their rewards are per player.
- **Attitudes are per player.** The same squad can be Allied to one player and Wary to another. An Allied squad defends its player only against attackers that can hurt that player (PvP rules apply).
- **Boss co-op:** 8.9. The bar membership comes from the session.
- **Patrol caps** are per player and per server (7.6).
- **Sense** never reveals players unless `force_sense.pvpEffects` is on. **Lift** affects players only with `force_lift.pvpEffects` and server PvP on, and never teammates (the M2 rule).
- **Holding a player** (the Archive Grip, a PvP Lift). Player movement is decided by the client, so velocity nudges cannot hold a player. The lead's `Holds` (Phase 0, real code) does it instead:
  - the server sets the synced `oldrepublic:held` attachment and a transient `GRAVITY` modifier (`oldrepublic:held_gravity`, -1.0, `ADD_MULTIPLIED_TOTAL`), which brings gravity to 0; vanilla stops counting floating ticks at zero gravity, so a dedicated server with `allow-flight=false` does not kick the held player for flying;
  - the client, while held: no gravity, movement input zeroed, attack, use and ability keys swallowed (except the Push slot when allowed), and the HUD hint (11.2);
  - the server keeps the position with `setDeltaMovement` and `hurtMarked` each tick, and teleports the player back if they drift more than 0.5 blocks;
  - both sides clear it on break, release, death, disconnect, dimension change and boss reset, and remove the gravity modifier on every one of those paths;
  - a client test on a dedicated server (default properties, `allow-flight=false`) grips the test player for the full 80 ticks and asserts they are still connected (17.2).

### 12.4 Dedicated server

- No client classes in `src/main` (as M1 and M2). The board screen, tracker, shelf, barks, Sense visuals, robe renderer, tooltip set line, held-state input blocking and boss bar fallback live in `src/client`. The guard is `splitEnvironmentSourceSets` (a compile error) plus the server game-test boot in `./gradlew build`; a client test cannot check it, because its in-process dedicated server shares the client's JVM.
- Mission definitions and site anchor sidecars load on the server only. The Exploration tree is synced like the other M2 trees.
- The game tests' dedicated-server start covers the new registries.
- `WorldSites` is covered by a codec round trip in `WorldM3GameTests` and by the lead's client test `ExplorationPersistenceTest`, which creates a world, writes state, closes it and reopens the save (the Fabric `PersistenceGametest` pattern, 17.2). A server game test cannot restart its world.
- A client test connects to a dedicated server, casts Sense and Lift, and holds a player in the Grip (17.2).

### 12.5 Config

**Server (`balance.json`) new sections:** `chapter_three`, `missions`, `holocron`, `sites`, `force_sense`, `force_lift`, `trooper`, `patrols`, `sentinel`, `collector`, `equipment`. **Extended sections:** `identity`, `skills`, `saber`, `progression`.

**Notable server switches:**

| Key | Default | Effect |
|---|---|---|
| `story.placeStructures` (M2) | true | Also covers Courier's Fall, mission camps and runtime outposts (a runtime outpost places only its terminal block and the entities). It does not cover the Reliquary. |
| `collector.placeReliquary` | true | When false, an operator places the Reliquary with `/oldrepublic c3 reliquary <player>` |
| `sites.runtimeOutposts` | true | When false, only worldgen outposts exist. |
| `patrols.enabled` | true | |
| `trooper.coldPeace` | true | When false, the two armies fight on sight. This is documented for servers that want it. |
| `force_sense.pvpEffects`, `force_lift.pvpEffects` | false | |

**Client (`ClientConfig`, lead):** `bossMusic`, `robeModels`, `showMissionTracker` and `showBarks`, all on by default. They are saved in `client.json` and toggled with `/oldrepublicfx boss_music|robe_models|tracker|barks on|off`; the lead adds these four toggles to `ClientCommandsFx` in Phase 0 (the file stays W3's afterwards). The existing presets, shake and flashing settings apply to every new effect. The boss music follows the vanilla music slider (`SoundSource.MUSIC`, 8.5).

**Data** (owner in brackets; section 16 lists every id):

- `data/oldrepublic/mission/*.json` (W2);
- worldgen (W1): the 5 `structure` files, the 2 structure sets `oldrepublic:ruins` and `oldrepublic:outposts`, template pools per structure (`<structure>/start`, `/underground`, `/side`, `/path`), the processor lists of 3.7, and the pool element type `oldrepublic:adapted_pool_element`;
- site anchor sidecars `data/oldrepublic/site_anchors/<template>.json` (written by each template's generator);
- POI types (3.3) and the banner pattern `oldrepublic:republic_chevron` (W1);
- loot tables: 11 named tables (`chests/jedi_ruin`, `chests/sith_tomb`, `chests/wreck_field`, `chests/force_cache`, `gameplay/r1_supply`, `gameplay/frontier_contract`, `gameplay/wayfarer_cache`, `gameplay/echo_cache`, `entities/republic_trooper`, `entities/sith_trooper`, `entities/ruin_sentinel`), plus one block loot table for each breakable new block;
- tags: block `force_reactive` and the M3 additions to `built_blocks` (lead, Phase 0); item `jedi_robe_cloth`, `sith_robe_cloth`, the repair tags `repairs/trooper_armour`, `repairs/jedi_robes`, `repairs/sith_robes` (lead creates, W4 fills) and the vanilla `enchantable/*` additions (9.1); entity `push_resistant` additions and `c:bosses`; biome and structure tags;
- damage types `tomb_miasma`, `urn_dust` (W1), `frag_grenade` (W4), `collector_surge`, `resonant_lash`, `archive_debris`, `archive_grip`, `repelling_palm` (W5);
- damage type tags: `minecraft:is_explosion` gains `frag_grenade`; `minecraft:is_projectile` gains `archive_debris`; `oldrepublic:force_harm` gains `collector_surge`, `resonant_lash`, `archive_grip` and `repelling_palm`. Files under `data/minecraft/tags/**` belong to the lead and change by request (15.2).

Structure spacing lives in the worldgen JSON, not in `balance.json`. Turning structures off takes a datapack override, which is documented.

---

## 13. Effects and sounds

Each worker writes the full phase-by-phase treatment (anticipation, activation, travel, impact, aftermath) into their design doc. The lead merges it into `VFX_SFX_SPEC.md` as a "Milestone 3" section, together with the overdue "Milestone 2" section.

Art direction applies throughout (`VFX_SFX_SPEC.md`):

- bent air, dust, debris, pressure and light;
- **no ground circles, sigils, halos, glyphs or glow outlines**;
- anything that spreads along the floor (the Surge, grenade bursts, the sentinel slam) is built from irregular dust puffs and debris of varied heights, with gaps; never a continuous ring, and never a flat `air_ripple` sprite at floor level;
- no effect claims refraction: "bent air" is particles and motion, never a lensing claim;
- tells are never hidden or reduced by presets;
- flashing off replaces flicker and flashes with steady glows;
- every loop stops on cancel, death, disconnect, dimension change, leaving the area, and item or slot change.

### 13.1 Particle budgets per active instance at Standard

Ambient, travel and aftermath particles are scaled by the preset multiplier (0.35 / 1 / 1.5 / 2.2) through `Vfx.count`. **Tell** particles (every row that `DangerTellTable.isTell` classifies as a tell, 15.1) go through the new `Vfx.tellCount` (lead, Phase 0), which ignores the preset, so presets never thin out a tell. The budgets below are at Standard.

| Effect | Budget |
|---|---|
| Sense reveal (all streams and clusters) | ≤ 60 |
| Lift (shimmer column and falling pebbles) | ≤ 30 |
| Holocron attunement, including the gatekeeper figure | ≤ 120 |
| Trooper muzzle and impact | ≤ 8 per shot |
| Grenade burst | ≤ 40 |
| Sentinel slam | ≤ 35 |
| Boss Surge (gather and release) | ≤ 160 |
| All sources in the boss hall | ≤ 900 |
| Site ambience, per site | ≤ 40 live |

### 13.2 New particle types

| Owner | Particles |
|---|---|
| W1 world | `light_mote`, `leaf_fall`, `tomb_dust`, `veil_dust`, `vent_grit`, `miasma`, `conduit_spark` |
| W2 quest | `falling_ember` (Courier's Fall Calling); `force_mote` (existing M2 particle, reused) |
| W3 force and holocron | `sense_mote`, `sense_stream`, `lift_shimmer`, `holocron_mote`, `projection_band` (the gatekeeper's horizontal banding) |
| W4 enemy | `kolto_mist`, `vent_steam`, `grenade_smoke`, `flare_ember` |
| W5 boss | `ward_haze`, `ward_current`, `surge_dust`, `lash_seam`, `lumen_spark`, `echo_smoke`; `crystal_glint` (existing M1 particle, reused, not registered again) |

### 13.3 Effect events (look and sound)

Each row is one gameplay moment, with one `EffectEventPayload` (`EffectEvents.around` / `toTracking` / `toPlayer`).

| Id | Owner | Delivery (variant, intensity) | Look | Sound |
|---|---|---|---|---|
| `veil_reveal` | W1 | around; intensity = group size | Fine pale dust seeps from the mortar lines and drifts down; hairline seams catch the light, steadily | `puzzle.veil_reveal` (a soft granular hiss) |
| `veil_open` | W1 | around | The group crumbles inward: chunks (the skin's block particles), a dust fall, a small puff outward | `puzzle.veil_crumble` |
| `counterweight` | W1 | around; 0 sink, 1 warn, 2 rise | Sink: dust squeezed from the seams and a grit puff. Warn: a grit trickle. Rise: dust slides off. | `puzzle.counterweight_sink`, `puzzle.counterweight_warn`, `puzzle.counterweight_rise` |
| `step_stone` | W1 | around; 0 extend, 1 retract | A grit spill along the ledge | `puzzle.step_extend`, `puzzle.step_retract` |
| `urn_echo` | W1 | around the urn; variant = step 0–3 | A slow plume of fine dust from the urn mouth, with a faint warm light inside it | `puzzle.urn_echo` (four notes of a minor tetrachord, by step) |
| `urn_wrong` | W1 | around | A 3-block curtain of dust and grit falls from the ceiling | `puzzle.urn_wrong` (a rumble and rattle) |
| `anchor` | W1 | around; 0 strain, 1 release | Strain: the ring shivers, shedding rust motes and dust. Release: a clank and a grit puff. | `puzzle.anchor_strain`, `puzzle.anchor_release` |
| `rubble_crumble` | W1 | around | The rubble shudders, then collapses in a dust burst with rolling pebbles | `puzzle.rubble_crumble` |
| `seal_open` | W1 | around | A dust burst from the seams, then the face crumbles away | `puzzle.seal_open` |
| `puzzle_solved` | W1 | toPlayer | (none) | `puzzle.solved` (a soft resolving chord) |
| `pedestal_ready` | W1 | around | Thin light in the pedestal seams; slow motes drift up from the cradle | `puzzle.pedestal_ready` |
| `tomb_vent` | W1 | around; 0 tell, 1 puff | Tell: grit dances up through the grate with a thin wisp. Puff: a dull grey-green miasma cloud 1.5 wide and 2 high that lingers 30 ticks. | `vent.hiss`, `vent.puff` |
| `site_discovered` | W2 (from `onSiteEntered`, 3.7) | toPlayer; variant = `SiteKind` | (toast) | `site.discovered` (3 variants) |
| `terminal_use` | W1 | around | The terminal screen brightens (steady) | `terminal.use` |
| `calling` variant 4 `HOLOCRON` | W2 (W3 sends the Sense follow-up pulse) | toPlayer; dir; intensity = closeness; `entityId` = holocron kind ordinal (0 Jedi, 1 Sith, 2 Wayfarer; there is no entity) | Pale motes drift along the direction, tinted by kind: warm gold, rust, brass | `story.calling_holocron` (a crystal hum swell) |
| `calling` variant 5 `CONTRACT` | W2 | toPlayer | A thin drift of ash-grey motes at eye height along the direction | the M1 Calling chime, pitched down |
| `calling` variant 6 `RELIQUARY` | W2 | toPlayer | Deep amber glints and a resonant hum that grows with closeness | `story.calling_reliquary` |
| `calling` variant 7 `OUTPOST` | W2 | toPlayer; intensity sign = army | Faint blue-white or crimson motes | the M1 Calling chime |
| `calling` variant 8 `FALL` | W2 | toPlayer | Ember streaks slanting down toward the site | `story.calling_fall` (a distant crackle) |
| `awakening_dream` variant 3 | W2 | toPlayer | The dream overlay: banner silhouettes snapping in wind, a crystal glow inside stone | `story.ones_that_sing` (loop) |
| `lesson_complete` variants 4–7 | W2 | toPlayer | The M2 flourish and banner (`mission`, `chapter3`, `boss`, `rank3`) | `mission.complete`, `story.lesson_complete` (existing M2 sound, reused), `story.collector_fallen`, `story.rank_three` |
| `cairn_touch` | W2 | around | The cairn settles into dust; one mote rises toward the player | `story.cairn_touch` |
| `rank_ceremony` | W2 | around; 0 Knighting, 1 Naming, 2 Pathfinder | Knighting: Saelen's lit blade touches each shoulder, a slow hum and a little drifting dust. Naming: the writ's seal cracks, then the old-name tablet breaks in two with a dust puff. Pathfinder: Tessaly Arn's figure projects from the Compass (the gatekeeper look) and nods. | `story.blade_touch`, `story.tablet_break`, `holocron.gatekeeper_form` |
| `mission_update` | W2 | toPlayer; 0 accept, 1 progress | (HUD) | `mission.accept`, `mission.update` |
| `force_sense` | W3 | toTracking (gesture); caster: dir, closeness, variant 1 when a holocron is sensed | Caster: the inhale, motes lifting outward, streams to points, pulses toward the holocron. Others: the gesture and a breath of dust. | `force.sense.cast`, `force.sense.reveal` |
| `force_lift` | W3 | toTracking; 0 start, 1 end, 2 slam, 3 hurl | Bent air at the raised palm; slam dust burst; hurl streak | `force.lift.start`, `force.lift.loop`, `force.lift.slam`, `force.lift.hurl` |
| `force_lift_target` | W3 | toTracking; entity | The shimmer column under the target, pebbles and dust falling | (the loop) |
| `holocron_attune` | W3 | around the pedestal; variant = kind | The holocron rises, seams open, alcove dust drifts upward | `holocron.rise`, `holocron.open` |
| `holocron_gatekeeper` | W3 | toPlayer; variant = gatekeeper index; intensity 1 form, 0 fade | A narrow spill of light from the opened seams widens into a thin, slightly translucent figure with faint horizontal banding (steady with flashing off), tinted by kind; motes only at its edges while it assembles and folds back into the holocron | `holocron.gatekeeper_form`, `holocron.gatekeeper_fade`, `holocron.hum` (loop) |
| `holocron_choice` | W3 | around; 0 seal, 1 break, 2 listen | Seal: the seams close with a dust exhale. Break (always a Jedi holocron): the pale stone splits along its gold seams, warm gold light escapes and gutters out, stone chips and gold dust fall. Listen: motes flow from the holocron into the player's chest. | `holocron.seal`, `holocron.break`, `holocron.listen` |
| `holocron_echo` | W3 | around | A faint gatekeeper silhouette, then a shard forms in the cradle | `holocron.echo` |
| `archive_choice` | W3 | around the plinth; 0 return, 1 keep, 2 unmake | Return: the three holocrons dim and settle into a travelling case, a dust exhale, the clasp closes. Keep: their seams warm and motes drift into the player. Unmake: the seams flare, light drains out of them as dust, the shells crack and slump (a steady glow, no flash, with flashing off). | `holocron.archive_return`, `holocron.archive_keep`, `holocron.unmake` (a low resolving sting) |
| `trooper_charge` | W4 | toTracking; entity; variant = bolt style | Muzzle glow | `trooper.republic.charge`, `trooper.imperial.charge` |
| `blaster_fire` (M1, extended) | W4 | variant = bolt style | A muzzle flash tinted by style | `trooper.republic.fire`, `trooper.imperial.fire` (poachers keep the M1 sound) |
| `trooper_vent` | W4 | toTracking; entity | A heat shimmer and steam from the rifle vents | `trooper.vent` |
| `kolto_heal` | W4 | toTracking; entity = patient | Pale teal vapour around the patient's chest | `trooper.kolto` |
| `trooper_tend` | W4 | toTracking; entity = downed trooper; 0 tending (every 20 ticks of the kneel), 1 stands | Tending: the player's hands at the trooper's chest, a little teal vapour. Stands: the trooper pushes up on one arm and rises, dust falls from the armour. | `trooper.tend`, `trooper.stand` |
| `trooper_rally` | W4 | toTracking; entity | A raised fist and a dust scuff | `trooper.rally` |
| `flare_call` | W4 | around | A crimson flare climbs 20 blocks trailing embers and smoke, and hangs for 40 ticks (a steady glow with flashing off) | `trooper.flare` |
| `grenade_tell` | W4 | toTracking; entity | Arm drawn back, a blinking indicator (steady with flashing off) | `grenade.arm` |
| `grenade_fuse` | W4 | toTracking; entity = the landed grenade | A quickening blink and thin smoke curling from the grenade for its 40-tick fuse (a steady glow with flashing off), so the player can see where it lies | `grenade.fuse` (a rising tick) |
| `grenade_burst` | W4 | around | Dust and smoke, sparks, debris of the ground block, a brief pressure shimmer; no ring decal | `grenade.burst` |
| `grenade_return` | W4 | around; 0 push, 1 pull | A bent-air streak along the new path | (none; the power's own sound plays) |
| `trooper_bark` | W4 | around the speaker; `entityId` = the speaking trooper (the client reads its army and role for the line and the tag); variant = category × 4 + line | The bark line on the HUD | `trooper.radio` |
| `trooper_salute` | W4 | toTracking; entity | The salute pose | (none) |
| `armour_set` | W4 | toPlayer; variant = set | A soft cloth or clasp flourish at the shoulders | `armour.set_bonus` |
| `sentinel_wake` | W5 | toTracking; entity; 0 wake, 1 bow | Wake: the lumen ignites, stone dust sheds from the shoulders. Bow: the head lowers, a little dust falls. | `sentinel.wake` |
| `sentinel_slam` | W5 | toTracking; 0 tell, 1 impact | Tell: arms raised, dust shedding. Impact: dust and ground debris across the arc, a pressure shimmer. | `sentinel.slam_tell`, `sentinel.slam` |
| `sentinel_core` | W5 | toTracking | The chest plates part and the lumen brightens (steady) for 40 ticks | `sentinel.core_exposed` |
| `sentinel_lance` | W5 | toTracking; 0 charge, 1 fire | Charge: the lumen brightens as motes are drawn in. Fire: an amber-white heavy bolt. | `sentinel.lance_charge`, `sentinel.lance_fire` |
| `sentinel_death` | W5 | toTracking; entity | The lumen gutters out, the plates slump and break into stone and bronze debris, and dust falls | `sentinel.death` |
| `boss_hurl` | W5 | toTracking; 0 lift (the tell), 1 throw, 2 ripped, 3 returned, 4 caught | Lift: a chunk tears from the pile, grit pouring off it. Ripped: it falls between them with a dust burst. Returned: a bent-air streak back toward him. Caught: the chunk hangs in bent air, grit trickling from it. | `boss.collector.hurl_lift`, `boss.collector.hurl_throw`, `boss.collector.hurl_rip`, `boss.collector.hurl_return` (caught: Lift's own loop) |
| `boss_palm` | W5 | toTracking; 0 tell, 1 release, 2 clash | Dust drawn toward the palm; an air shove; the M2 clash burst | `boss.collector.palm_tell` (release and clash reuse the M2 acolyte sounds) |
| `boss_lash` | W5 | toTracking; dir; 0 tell, 1 wave | Tell: a seam of floor dust lifts in a line toward the target. Wave: a travelling 1-block ridge of dust and grit. | `boss.collector.lash_tell`, `boss.collector.lash_wave` |
| `boss_surge` | W5 | toTracking; 0 gather, 1 release, 2 clash | Gather: the air bends toward him, floor dust and grit stream inward, his cloak streams forward. Release: an irregular wall of dust puffs and tumbling debris of varied heights, with gaps, rushes outward, clipped by blocks on the client; never a continuous ring. Clash: the wall breaks around the clashing player. | `boss.collector.surge_gather`, `boss.collector.surge_release`, `boss.collector.surge_clash` |
| `boss_spent` | W5 | toTracking | He sags, the holocron's light gutters, and dust settles | `boss.collector.spent` |
| `boss_grip` | W5 | toTracking; entity = target; 0 tell, 1 hold, 2 break | Tell: pebbles and dust rise around the target's feet. Hold: limbs strained, dust trickling. Break: a burst of grit. | `boss.collector.grip`, `boss.collector.grip_hold`, `boss.collector.grip_break` |
| `boss_lunge` | W5 | toTracking; 0 tell, 1 strike | Crouch, dust kicked back; then a streak | `boss.collector.lunge` |
| `boss_ward` | W5 | toTracking; 0 ward (intensity = pylons singing, 0–4), 1 Unsung | Ward: sparse heat-haze specks (`ward_haze`) drift off his shoulders and blade, denser with more pylons singing; never a bubble, an outline or a halo. Unsung: the last currents snap back into the pylons, he reels off the dais, dust shakes from the ceiling. | `boss.collector.ward_hum` (loop), `boss.collector.unsung` |
| `boss_phase` | W5 | toTracking (participants); 0 start, 1 transition one, 2 transition two, 3 yield, 4 reset, 5 victory, 6 music stop | Transitions: ceiling dust, shards streaming into him, camera shake | `boss.collector.transition`, the music loops |
| `boss_yield` | W5 | toTracking | The blade retracts, crystal light dims, dust settles | `boss.collector.yield` |
| `boss_leash` | W5 | toTracking; 0 vanish, 1 reappear | A puff of smoke where he was; smoke and settling dust at the dais | `boss.collector.leash` |
| `boss_echo` | W5 | toTracking; 0 form, 1 dissolve | Form: smoke gathers from the floor over 60 ticks into his desaturated shape. Dissolve: at 0 health he breaks into slow smoke that drains into the floor. | `boss.collector.echo_form`, `boss.collector.echo_fade` |
| `collector_resolve` | W5 | toTracking; variant 0–5 as the 8.7 scenes | 0, 3, 5: troopers come down the descent, binders lock, he is walked out. 1: he rises, sets his blade on the dais, and walks out alone. 2: a single stroke; he falls forward, his amber blade rolls away and gutters out, dust settles (no gore). 4: he bows his head, picks up his satchel and walks out. | `boss.collector.binders` (0, 3, 5), `boss.collector.execute` (2), `boss.collector.walk_out` (1, 4) |
| `pylon_state` | W5 | around the pylon; 0 sing, 1 hit (intensity = resonance left), 2 silenced, 3 regrow tell, 4 re-sung, 5 shatter | Sing: thin wavering mote currents flow to Vasch. Hit: crystal chips. Silenced: the crystal cracks dark and glints fall. Regrow: the crystal grows back. Shatter: shards fly into him. | `pylon.sing` (loop), `pylon.crack`, `pylon.silence`, `pylon.regrow`, `pylon.shatter` |
| `npc_deflect` | W5 | toTracking; entity | A blade sweep with sparks at the blade | the M1 deflect sound |
| `hoard_open` | W5 | around | The vault seal crumbles; motes rise from three holocrons | `hoard.open` |

**Sound rules.** Every sound has a subtitle (`subtitles.oldrepublic.<id>`), positional attenuation, and the variation rules of `AUDIO_BRIEF.md` (pitch randomisation, no immediate repeat of a variant).

Concurrency limits:

| Sound | Limit |
|---|---|
| `trooper.*.fire` | ≤ 3 per squad per 4 ticks |
| `grenade.burst` | ≤ 2 per 10 ticks |
| `grenade.fuse` | ≤ 2 at once |
| `trooper.tend` | 1 per tending player |
| `pylon.sing` | ≤ 4 loops |
| `force.sense.reveal` | ≤ 6 per cast |
| `trooper.radio` | follows the bark rate limits |

Every loop (the site beds, `holocron.hum`, `force.lift.loop`, `pylon.sing`, `boss.collector.ward_hum`, `saber.hum.singing`, the boss music and `story.ones_that_sing`) is listed in 16 with its stop rule and stops on cancel, death, disconnect, dimension change, leaving the area, and item or slot change.

---

## 14. Balance (defaults)

Ticks: 20 per second. "SP" means skill points. Cumulative XP to reach each level (`Progression.xpToNext`):

| Level | Cumulative XP |
|---|---|
| 6 | 4,063 |
| 7 | 6,172 |
| 8 | 8,832 |
| 9 | 12,085 |
| 10 | 15,971 |
| 11 | 20,529 |
| 12 | 25,795 |

### 14.1 Expected budget (solo, normal pace)

Path players (Jedi, Sith) enter Chapter Three after the Trial at level 6. Independents have no lessons (2 × 300) and no Trial (800) and no level gate on "No Master", so they enter about 1,400 XP behind, at level 5. Courier's Fall (the Compass and the caches) and the two extra story missions open to them (they can take all four) close the gap before the level 9 gate.

| Source | Path XP | Path total | Lv | Independent XP | Independent total | Lv |
|---|---|---|---|---|---|---|
| Chapter Three begins | – | ≈ 5,000 | 6 | – | ≈ 3,600 | 5 |
| Opening: the lesson (path) or the Compass 600 + caches 400 (Independent) | 400 | 5,400 | 6 | 1,000 | 4,600 | 6 |
| First-echo holocron | 600 | 6,000 | 6 | 600 | 5,200 | 6 |
| Discovery: first structure type 300, both outposts 150 each | 600 | 6,600 | 7 | 600 | 5,800 | 6 |
| Story missions: 2 of the own army (path) or all 4 (Independent) | 2,400 | 9,000 | 8 | 4,800 | 10,600 | 8 |
| Second holocron, plus discovery of a second structure type | 900 | 9,900 | 8 | 900 | 11,500 | 8 |
| 3 contracts at level 8 (570 each) | ≈ 1,700 | ≈ 11,600 | 8 | ≈ 1,700 | ≈ 13,200 | **9** |
| About 45 kills (troopers 36, sentinels up to 80, with fatigue) | ≈ 1,600 | ≈ 13,200 | **9**: the Reliquary gate | ≈ 1,600 | ≈ 14,800 | 9 |
| The Collector | 3,000 | ≈ 16,200 | 10 | 3,000 | ≈ 17,800 | 10 |
| The Archive choice | 300 | ≈ 16,500 | 10 | 300 | ≈ 18,100 | 10 |
| Report | 600 | ≈ 17,100 | 10 | 600 | ≈ 18,700 | 10 |
| The loop: contracts at level 10 (650 each), a holocron, kills | ≈ 3,500 | ≈ 20,600 | **11**: rank 3 | ≈ 1,900 | ≈ 20,600 | **11**: Pathfinder |

If playtests show Independents reaching level 9 noticeably later, raise `chapter_three.cacheXp` (the Courier's Fall caches only) before anything else.

**Skill points in M3:**

| Source | Path SP | Independent SP |
|---|---|---|
| Levels to 11 | 5 (6 to 11) | 6 (5 to 11) |
| The boss | 2 | 2 |
| About 4 holocrons (one SP every second holocron) | 2 | 2 |
| Rank 3, or the Pathfinder title | 2 | 2 |
| **Total** | about 11 | about 12 |

A player who reaches the 6-holocron milestone gets one more. The Exploration tree sells 10 SP (11 for Independents).

**Reputation and trust toward rank 3** (typical choices; the requirements are 5.8). Both paths clear the gate before level 11; Sith overshoot more, which changes nothing, because standing above Honoured unlocks nothing new in M3.

| Source | Jedi: Jedi Order | Sith: Sith Empire | Independent (Pathfinder: Trusted with any faction) |
|---|---|---|---|
| End of Chapter Two (M2 4.2: joining, lessons, Smoke on the Ridge, the Trial) | ≈ 420–500 | ≈ 420–500 | Frontier ≈ 100–250; both armies 0 |
| Chapter Three lesson, or the caches | +40 | +40 | Frontier +40 |
| Own story missions | +50 to +100 (the Republic gains through `jediOrderShare`) | +80 to +200 | +100 to +200 with each army |
| 3 own-side contracts | +36 (Republic +25 each, shared ×0.5) | +75 | +75 with the issuer |
| Mentor duties | +25 each | +25 each | – |
| A returned holocron | +40 | +40 | +40 with an army |
| The Collector's ending | Spare +60 | Execute +80 or Bind +100 | Bring him in +100 |
| Archive Return (optional) | +150 | +150 | Frontier +100 |
| **Typical by level 11** | ≈ 600–700 | ≈ 700–850 | Trusted with one army after its first mission |
| Mentor trust (gate 80) | ≥ 60 at the Trial; lesson +10, a return +10, Spare +10, sessions +5 a day | the same (Execute or Bind +10) | – |

### `chapter_three` (`quest.story.ChapterThreeBalance`)

| Key | Default | Meaning |
|---|---|---|
| `waitTicks` / `minSleepTicks` | 24000 / 100 | WAITING |
| `leadsNeeded` | 4 | |
| `reliquaryMinLevel` / `recommendedLevel` | 9 / 10 | |
| `lessonXp` / `cacheXp` | 400 / 400 | Path lesson; Independent caches (tuned separately, 14.1) |
| `lessonTrust` / `lessonRep` | 10 / 40 | |
| `cairnCount` / `cairnRadius` / `cairnSpacing` | 3 / 24 / 8 | |
| `fallRings` / `fallFallback` | [64, 80, 96] / [32, 48] | Courier's Fall, from the player |
| `cacheMin` / `cacheMax` | 12 / 24 | Independent caches from the fall |
| `firstEchoRadius` | 768 | |
| `shrineRings` / `shrineFallback` | [96, 128] / [48, 80] | |
| `extraShrinesPerKind` | 1 | the old-world guarantee (4.3) |
| `reliquaryRings` / `reliquaryFallback` | [96, 128, 144] / [48, 80] | from the player; capped at 144 (3.6) |
| `retryTicks` / `forwardFallbackAfter` | 1200 / 3 | |
| `vaschMessageLeads` / `ambushLeads` | 2 / 3 | Vasch's two beats (6.4) |
| `meetGiveBackLight` | 1 | "Give them back." in `c3_collector_meet` (MINOR light, 8.3) |
| `ambushMinSiteDistance` / `ambushRange` / `ambushDespawnDistance` | 64 / [24, 32] / 128 | |
| `reportXp` / `reportTrust` / `reportRep` / `reportResonance` | 600 / 10 / 50 / 300 | `reportRep` is the Independent's army, or Frontier for "Send word" |
| `rank3MinLevel` / `rank3MinTrust` / `rank3MinRep` / `rank3Alignment` | 11 / 80 / 500 / 15 | |
| `rank3Holocrons` / `rank3Contracts` | 2 / 3 | |
| `pathfinderHolocrons` / `pathfinderContracts` / `pathfinderTier` | 3 / 3 / 100 (Trusted) | |
| `rank3Xp` / `rank3Sp` / `rank3Resonance` | 1000 / 2 / 700 | |

### `missions` (`mission.MissionBalance`)

Per-choice rewards of the four story missions live in their mission JSON (5.4), not here.

| Key | Default |
|---|---|
| `storyXp` / `storyCommendations` | 1200 / 2 |
| `jediOrderShare` | 0.5 (a Jedi's Republic gains from Republic work also pay this share as Jedi Order) |
| `offersPerBoard` / `dutiesPerDay` / `usedOffersMax` | 2 / 1 / 16 |
| `fullPayPerDay` / `overCapMultiplier` | 3 / 0.25 |
| `contractXpBase` / `contractXpPerLevel` / `contractXpCap` | 250 / 40 / 900 |
| `contractRep` / `contractCommendations` | 25 / 1 |
| `dutyTrust` / `dutyRep` | 5 / 25 |
| `siteRings` / `siteFallback` | [96, 128, 144] / [48, 80] (from the player) |
| `siteSpawnDistance` / `siteSeparation` | 48 / 48 |
| `surveyRadius` / `surveyHeartRange` / `secureRadius` | 512 / 8 / 384 |
| `interceptYield` / `borderYield` / `chiefYield` / `fenceYield` | 0.25 / 0.20 / 0.25 / 0.25 |
| `tendTicks` / `healTendRange` / `downedBleedTicks` | 60 / 4 / 2400 |
| `escortDespawnTicks` | 300 |
| `interrogatePullTicks` / `interrogateRadius` | 12000 / 1024 (also the freed-captive cameo's Calling) |
| `discoveryStructureXp` / `discoveryOutpostXp` | 300 / 150 |
| `trackerHintTicks` / `trackerNear` / `trackerHere` | 20 / 160 / 48 |
| `guidanceIntervalTicks` | 40 |

### `identity` (additions to `identity.IdentityBalance`)

W2 owns kill reputation (5.2), so its keys live here.

| Key | Default |
|---|---|
| `republicSeedJedi` / `republicSeedSith` / `republicSeedIndependent` | 100 / -100 / 0 |
| `killedSoldier` | -2 |
| `neutralKillRep` / `hostileKillRep` / `killRepDailyCap` | -40 / -10 / 200 (the cap covers only the -10 losses) |

### `holocron` (`holocron.HolocronBalance`)

| Key | Default |
|---|---|
| `baseXp` / `resonance` / `relicLoreXpBonus` | 600 / 120 / 0.25 |
| `sealShift` / `jediListenShift` / `breakShift` / `sithListenShift` | +3 / -4 / -3 / +4 |
| `listenResonance` / `breakResonance` / `breakTrust` | 80 / 80 / 10 |
| `carriedRegenPct` / `carriedMax` / `carryRefreshTicks` | 4 / 3 / 20 |
| `returnTrust` / `returnRep` / `returnCommendations` | 10 / 40 / 1 |
| `amendsRep` / `amendsShards` | 40 / 2 |
| `pedestalClearRange` | 16 (no hostile within this range) |
| `spEvery` | 2 |
| `milestone3Resonance` / `milestone6Sp` / `milestone9Resonance` / `milestone12Resonance` | 150 / 1 / 250 / 400 |
| `echoXp` / `echoShards` / `echoRing` / `shardsPerCommendation` | 150 / 1 / 64 / 2 |
| `restoreCooldownTicks` | 24000 |
| `riseTicks` / `gatekeeperDelayTicks` | 30 / 30 |
| `senseFollowUpTicks` | 40 |
| `archiveXp` | 300 |
| `archiveReturnOwnRep` / `archiveReturnRepublicRep` / `archiveReturnTrust` / `archiveReturnLight` | 150 / 60 / 20 / 5 |
| `archiveReturnFrontierRep` / `archiveReturnIndependentLight` | 100 / 3 |
| `archiveKeepJediShift` / `archiveKeepIndependentShift` | -6 / -2 |
| `archiveUnmakeResonance` / `archiveUnmakeLight` / `archiveUnmakeRepLoss` | 400 / 3 / -50 |

### `sites` (`world.site.SiteBalance`)

| Key | Default |
|---|---|
| `veilRevealTicks` / `veilGroupMax` | 160 / 27 |
| `puzzleResetTicks` / `puzzleResetRange` / `puzzleTickInterval` | 1200 / 24 / 200 |
| `counterweightTicks` / `counterweightWarnTicks` | 600 / 40 |
| `anchorStrainTicks` / `anchorOpenTicks` | 10 / 600 |
| `rubbleTicks` | 20 |
| `urnSenseRange` / `urnStepTicks` / `urnRetryTicks` | 12 / 20 / 100 |
| `urnWrongDamage` / `urnWrongRadius` / `sentinelWakeRadius` | 2 / 3 / 16 |
| `ventMinTicks` / `ventMaxTicks` / `ventTellTicks` / `ventActiveRange` | 60 / 100 / 15 / 8 |
| `ventDamage` / `ventSlownessTicks` | 4 / 60 |
| `cairnRevealTicks` | 600 |
| `spawnerRange` | 32 (`site_spawner`) |
| `outpostWorldgenRadius` / `outpostReuseRadius` | 640 / 512 |
| `outpostRings` / `outpostFallback` / `outpostSeparation` | [112, 128, 144] / [80, 96] / 128 |
| `maxRuntimeOutposts` / `runtimeOutposts` | 8 / true |
| `garrisonRefillTicks` / `garrisonRefillClearRange` / `garrisonLeash` / `terminalChecksBeforeRespawn` | 12000 / 48 / 12 / 2 |
| `locatorCacheTicks` / `locatorCacheMove` / `locatorCellsPerTick` / `locatorMaxRings` | 600 / 48 / 4 / 4 |
| `presenceCheckTicks` | 20 |

### `force_sense` (`force.sense.SenseBalance`)

| Key | Default |
|---|---|
| `cost` / `cooldownTicks` / `durationTicks` | 15 / 240 / 160 |
| `creatureRange` / `blockRange` / `maxPoints` | 24 / 16 / 32 |
| `holocronRadius` / `seekerRadius` / `seekerBonusTicks` / `glintRange` | 256 / 512 / 80 / 24 |
| `dangerRange` / `dangerConeDegrees` / `dangerRepeatTicks` | 12 / 100 / 20 |
| `masteryCostPerRank` / `masteryRangePerRank` | 1 / 4 |
| `pvpEffects` | false |

### `force_lift` (`force.lift.LiftBalance`)

| Key | Default |
|---|---|
| `startCost` / `costPerSecond` / `cooldownTicks` | 20 / 8 / 280 |
| `maxHoldTicks` / `suspendHoldTicks` | 60 / 100 |
| `range` / `coneDegrees` / `maxTargetHeight` | 14 / 8 / 2.4 |
| `height` / `resistantHeight` / `riseTicks` | 2.5 / 1.2 / 10 |
| `slamDamage` / `slamStaggerTicks` | 6 / 15 |
| `hurlDistance` / `hurlDamage` / `hurlStaggerTicks` | 10 / 6 / 20 |
| `suspendAllyBonus` / `interruptDamage` | 0.2 / 6 |
| `bossHeight` / `bossStaggerTicks` / `rubbleTicks` | 1.2 / 10 / 20 |
| `masteryCostPerRank` / `masteryRangePerRank` | 0.05 / 1 |
| `pvpEffects` | false |

### `skills` (additions to `skill.SkillBalance`)

Only W3's nodes. The keys of the nodes W5 implements are in `saber`.

| Key | Default |
|---|---|
| `trailwiseFall` / `trailwiseHunger` | [0.2, 0.4] / [0.15, 0.3] |
| `hardyHealth` | [2, 4] |
| `scavengerChance` | 0.35 |
| `quietStepWake` / `quietStepWarn` / `quietStepWaryTicks` | 4 / 10 / 200 |
| `pathfinderSpeed` / `pathfinderFall` / `fallReductionCap` | 0.10 / 0.25 / 0.6 |
| `watchersInsightHealCost` | 0.10 |
| `tombHungerFe` / `tombHungerWindowTicks` | 6 / 60 |
| `cracklingSlow` / `cracklingTicks` | 0.3 / 40 |
| `archivistsHungerBonus` / `archivistsHungerThreshold` | 0.15 / 0.75 |
| `archivistsRoadSenseCooldown` / `archivistsRoadLiftCost` | 0.3 / 0.1 |

### `trooper` (`enemy.trooper.TrooperBalance`)

| Key | Default |
|---|---|
| `republicRifleman` / `republicMedic` / `republicSergeant` (health / armour) | 24 / 4, 20 / 2, 30 / 6 |
| `imperialRifleman` / `imperialGrenadier` / `imperialSergeant` (health / armour) | 24 / 5, 26 / 5, 30 / 7 |
| `speed` / `chargeTicks` / `bandMin` / `bandMax` | 0.27 / 12 / 10 / 18 |
| `republicBoltDamage` / `burstSize` / `burstGap` / `burstCooldown` | 4 / 3 / 4 / 50 |
| `ventAfterBursts` / `ventTicks` | 3 / 40 |
| `imperialBoltDamage` / `imperialCooldown` / `flankerRange` | 7 / 34 / [12, 16] |
| `pistolDamage` / `pistolCooldown` | 4 / 40 |
| `medicHeal` / `medicThreshold` / `medicChannelTicks` / `medicCooldown` / `interruptDamage` | 8 / 0.5 / 20 / 160 / 4 |
| `rallyTellTicks` / `rallyTicks` / `rallyCooldown` / `rallyFireRate` | 30 / 200 / 600 / 0.25 |
| `grenadeInterval` / `grenadeTellTicks` / `grenadeFuseTicks` | 200 / 20 / 40 |
| `grenadeDamage` / `grenadeRadius` / `grenadeKnockback` / `grenadeRange` | 8 / 3 / 0.5 / [6, 14] |
| `grenadePushSpeed` | 1.2 |
| `reinforceTellTicks` / `reinforceDelayTicks` / `reinforceCount` / `reinforceDistance` | 30 / 200 / 2 / [24, 32] |
| `deflectWindowTicks` / `deflectCount` / `holdFireTicks` / `repositionRange` | 100 / 2 / 40 / [4, 8] |
| `durasteelChance` | 0.2 |
| `hostileRange` / `warnRange` / `waryRange` / `waryTicks` / `warnRepeatTicks` | 24 / 16 / 8 / 120 / 600 |
| `allyDefendRange` / `provokedTicks` / `engagedTicks` / `incidentTicks` | 16 / 1200 / 1200 / 1200 |
| `standoffRange` / `standoffDistance` / `standoffTicks` | 16 / 12 / 200 |
| `barkRange` / `barkTrooperTicks` / `barkSquadTicks` | 24 / 120 / 40 |
| `extraRiflemanLevel` / `moraleRetreatTicks` / `moraleRetreatDistance` | 11 / 200 / 24 |
| `coldPeace` | true |

### `patrols` (`enemy.trooper.PatrolBalance`)

| Key | Default |
|---|---|
| `enabled` | true |
| `intervalTicks` / `chance` | 1200 / 0.15 |
| `minDistance` / `maxDistance` / `passDistance` / `routeLength` | 48 / 80 / 16 / 96 |
| `perPlayerRange` / `perPlayerCap` / `globalCap` | 128 / 1 / 6 |
| `despawnDistance` / `despawnCheckTicks` / `outpostBiasRange` / `siteClearance` | 128 / 100 / 384 / 64 |

### `sentinel` (`enemy.construct.SentinelBalance`)

| Key | Default |
|---|---|
| `watcherHealth` / `tombHealth` / `armor` | 48 / 60 / 8 |
| `watcherSpeed` / `tombSpeed` / `wakeRange` / `wakeTicks` | 0.18 / 0.16 / 6 / 20 |
| `dormantAlignment` | 15 (Watchers sleep for ≥ +15, Tomb sentinels for ≤ -15) |
| `slamTellTicks` / `slamDamage` / `slamRadius` / `slamArcDegrees` / `slamKnockback` / `slamCooldown` | 20 / 10 / 2.5 / 90 / 0.6 / 60 |
| `exposedTicks` / `exposedMultiplier` | 40 / 1.5 |
| `lanceTellTicks` / `lanceDamage` / `lanceSpeed` / `lanceRange` / `lanceCooldown` | 24 / 7 / 1.6 / [6, 20] / 80 |
| `lanceGuardDrain` / `lanceReturnDamage` / `lanceReturnStaggerTicks` | 35 / 10 / 20 |
| `pushMultiplier` / `lightningMultiplier` / `shockDurationMultiplier` | 0.2 / 1.5 / 2 |
| `blasterMultiplier` / `saberMultiplier` | 0.5 / 0.85 |
| `durasteelChance` / `threadChance` | 0.4 / 0.2 |

### `collector` (`boss.CollectorBalance`)

| Key | Default |
|---|---|
| `maxHealth` / `perParticipant` / `scalingCap` / `armor` / `knockbackResistance` | 450 / 0.6 / 4 / 10 / 0.8 |
| `phase2At` / `phase3At` / `yieldAt` / `transitionTicks` | 0.60 / 0.30 / 0.10 / 60 |
| P1 `lightDamage` / `heavyDamage` / `guard` / `parryChance` / `parryMinStamina` / `windupTicks` / `recoveryTicks` | 6 / 11 / 140 / 0.30 / 0.70 / 10 / 16 |
| `hurlInterval` / `hurlTellTicks` / `hurlDamage` / `hurlSpeed` / `hurlGuardDrain` | 180 / 24 / 8 / 1.1 / 30 |
| `hurlRipStaggerTicks` / `hurlReturnRange` / `hurlReturnMultiplier` / `hurlReturnStaggerTicks` | 20 / 6 / 1.5 / 30 |
| `palmRange` / `palmDwellTicks` / `palmTellTicks` / `palmDamage` / `palmKnockback` / `palmClashStaggerTicks` | 3 / 80 / 16 / 4 / 1.4 / 20 |
| `deflectConeDegrees` / `deflectSpreadDegrees` | 70 / 15 |
| `wardHigh` / `wardLow` | 0.35 / 0.65 |
| `unsungTicks` / `unsungMultiplier` | 100 / 1.5 |
| `pylonResonance` / `pylonScale` / `pylonPullRange` / `pylonLiftTicks` | 30 / 0.25 / 12 / 20 |
| `pylonSaber` / `pylonPush` / `pylonFocusedBlast` / `pylonThrow` / `pylonLightningPerSecond` / `pylonParriedLance` | 10 / 10 / 15 / 15 / 10 / 15 |
| `resingTicks` / `regrowTellTicks` / `resingPerPhase` | 800 / 60 / 1 |
| `lashInterval` / `lashTellTicks` / `lashDamage` / `lashSpeed` / `lashRange` / `lashKnockback` | 140 / 20 / 9 / 0.8 / 20 / 0.6 |
| `sentinels` / `sentinelsMax` / `leashRange` | 2 / 4 / 8 |
| P3 `lightDamage` / `heavyDamage` / `guard` / `parryChance` / `windupTicks` / `recoveryTicks` | 7 / 12 / 100 / 0.15 / 8 / 12 |
| `lungeTellTicks` / `lungeRange` / `lungeDamage` | 14 / 8 / 10 |
| `surgeInterval` / `surgeTellTicks` / `surgeRadius` / `surgeDamage` / `surgeKnockback` | 300 / 30 / 8 / 10 / 2.0 |
| `surgePartialMultiplier` / `surgeAirborneMultiplier` / `surgeClashTicks` / `surgeClashShelter` | 0.5 / 0.5 / 8 / 3 |
| `spentTicks` / `spentMultiplier` | 60 / 1.5 |
| `gripInterval` / `gripTellTicks` / `gripHoldTicks` / `gripDamage` / `gripBreakDamage` / `gripTakenMultiplier` / `gripSurgeGap` | 400 / 20 / 80 / 2 / 8 / 1.25 / 60 |
| `interruptHitDamage` | 6 (a single hit that breaks the Grip tell) |
| `lightningMultiplier` / `saberThrowMultiplier` | 0.6 / 0.7 |
| `threatWindowTicks` / `threatSwitchTicks` | 200 / 200 |
| `resetTicks` / `sealCloseTicks` / `meetDialogueTicks` / `participantLeaveTicks` / `readyRange` / `daisStartRange` | 200 / 40 / 400 / 100 / 48 / 6 |
| `ownerXp` / `ownerSp` / `resonance` / `otherHelperXp` / `otherHelperShards` | 3000 / 2 / 600 / 1000 / 1 |
| `spareLight` / `spareJediOrderRep` / `spareRepublicRep` / `spareJediTrust` / `spareSithTrust` | 10 / 60 / 100 / 10 / -25 |
| `executeDark` / `executeSithRep` / `executeSithTrust` / `executeJediTrust` / `executeFrontierRep` | -10 / 80 / 10 / -25 / 20 |
| `bindDark` / `bindSithRep` / `bindSithTrust` | -5 / 100 / 10 |
| `releaseLight` / `releaseFrontierRep` | 5 / 50 |
| `bringInRep` / `bringInCommendations` | 100 / 2 |
| `echoFormTicks` / `echoDamageMultiplier` / `echoXp` / `echoDurasteel` / `echoShards` / `emitterChance` / `emitterPity` | 60 / 1.15 / 600 / 2 / 1 / 0.25 / 4 |
| `unchainedHealth` / `unchainedDamage` / `unchainedPhase3At` / `unchainedXp` / `unchainedRolls` | 1.5 / 1.25 / 0.40 / 900 / 2 |
| `placeReliquary` | true |

W2 reads the reward and ending keys (it grants them, 8.8); W5 reads the fight keys.

### `equipment` (`equipment.EquipmentBalance`)

| Key | Default |
|---|---|
| `trooperCostPct` / `trooperRegenPct` / `trooperSpeedPct` | 5 / -5 / -1.5 |
| `republicBlasterPct` / `republicSetBlasterPct` / `republicSetBoltDrainPct` | -6 / -10 / -25 |
| `sithMeleePct` / `sithSetControlPct` / `sithSetKnockbackResist` | -4 / -30 / 0.2 |
| `jediRegenPct` / `jediCostPct` / `jediSetParryFe` / `jediSetHealCost` | 6 / -2 / 5 / -15 |
| `sithMaxEnergy` / `sithCostPct` / `sithSetKillFe` / `sithSetWindowTicks` / `sithSetLightningCost` | 5 / -2 / 8 / 160 / -15 |
| `setAlignment` | 15 |
| `trooperDurability` / `robeDurability` / `trooperEnchant` / `robeEnchant` | 18 / 10 / 9 / 15 |
| `disguiseBreakTicks` / `disguiseBreakRange` | 2400 / 24 |

### `progression` (additions to `progression.ProgressionBalance`)

| Key | Default |
|---|---|
| `modifierCostMin` / `modifierCostMax` | 0.70 / 1.50 |
| `modifierRegenMin` / `modifierRegenMax` | 0.50 / 1.50 |
| `damageModifierMin` / `damageModifierMax` | 0.25 / 2.0 |
| `killRefundCap` / `parryRefundCap` | 12 / 6 (FE) |

### `saber` (additions to `saber.SaberBalance`)

Includes the keys of the nodes W5 implements (10.4).

| Key | Default |
|---|---|
| `npcDeflectConeDegrees` / `npcDeflectSpreadDegrees` | 70 / 15 |
| `heavyBoltGuardDrain` / `nonDeflectableGuardDrain` | 35 / 30 |
| `parryWindowCap` | 11 (ticks, after flat bonuses and the form multiplier) |
| `archivistParryTicks` / `archivistAttackSpeed` | 2 / -0.05 |
| `reliquaryDeflectDegrees` / `reliquaryParryBonus` | 12 / 0.15 |
| `watchersInsightFe` | 3 |
| `archivistsWardTicks` | 2 |
| `knightsVigilRange` / `knightsVigilReduction` | 6 / 0.1 |
| `lordsDominionRange` / `lordsDominionReduction` / `lordsDominionTicks` | 8 / 0.1 / 100 |

---

## 15. Architecture and work split

### 15.1 Phase 0 (lead, before workers start)

The lead writes the following **contracts** with real signatures and minimal bodies so every worker compiles against them from day one. Where a bracket says "real code", the lead writes the whole body; otherwise the worker named in brackets fills it in. Signatures are frozen: new methods can be added, existing ones are not changed. Every enum below is stored or synced by ordinal and is append only. Where a contract touches an **existing** M1 or M2 type, the text says "existing" and names only what is added; nothing existing is removed, renamed or given a new meaning.

```java
// com.oldrepublic.combat  [lead: real code; owners register rules]
public enum Allegiance { NONE, CREW, REPUBLIC, EMPIRE, ANCIENT, ARCHIVE }
public interface Aligned { Allegiance allegiance(); }
public final class Allegiances {
	public static Allegiance of(Entity e);                                    // Aligned, else NONE (players: ArmourSets.disguise)
	public static boolean friendly(Entity a, Entity b);                       // same allegiance, and not NONE
}
public enum Attitude { HOSTILE, WARY, NEUTRAL, ALLIED }
public enum IncidentMode { NONE, ARMY, ARMY_AND_OWNER }                        // 5.2; saved on each trooper
public final class Attitudes {
	public interface Rule {
		@Nullable Attitude toward(LivingEntity self, Player p);               // null = no opinion
		default boolean engaged(LivingEntity self, Player p) { return false; }    // fired first at p, or an incident that includes p
		default boolean provokedBy(LivingEntity self, Player p) { return false; } // p struck first
	}
	public static void register(Rule rule);                                   // W4 registers the trooper rule (5.2)
	public static Attitude toward(LivingEntity self, Player p);               // first non-null; else HOSTILE for Enemy, NEUTRAL otherwise
	public static boolean countsAsHostile(Player p, Entity e);                // HOSTILE or engaged; never provoked alone. XP, mastery, quest credit
	public static boolean provokedBy(Entity e, Player p);
	public static boolean shieldedFromArea(Player p, Entity e);               // !countsAsHostile && !provokedBy: skipped by area powers and aim assist
	public static void snapshotOnDamage(ServerPlayer attacker, LivingEntity victim); // called by the lead's damage hook when a player's damage lands
	public static boolean hostileSnapshot(LivingEntity victim, ServerPlayer killer); // countsAsHostile at the killer's last damage (5.2)
}
public interface DamageModifier { float modify(LivingEntity victim, DamageSource source, float amount); }
public final class DamageModifiers { public static void register(DamageModifier m); public static float apply(LivingEntity victim, DamageSource source, float amount); } // product, clamped 0.25–2.0
public final class CombatEvents {                                             // Fabric Event<>, fired by the lead's call sites
	public static final Event<Parry> PARRY;            // (ServerPlayer defender, @Nullable LivingEntity attacker, boolean projectile); once per melee or bolt parry
	public static final Event<PowerHit> POWER_HIT;     // (ServerPlayer caster, String abilityId, LivingEntity target); living targets only
	public static final Event<Technique> TECHNIQUE;    // (ServerPlayer p, String technique, @Nullable LivingEntity target, boolean hit)
}
public final class DangerTellTable { public static boolean isTell(Identifier id, int variant); } // common code, frozen table below; the client DangerTells delegates
public final class Holds {                                                    // [lead: real code] attachment oldrepublic:held (12.1, 12.3)
	public static void hold(ServerPlayer p, int ticks, String source, Vec3 anchor, boolean pushAllowed);
	public static void release(ServerPlayer p, String reason);                // break, release, death, logout, dimension change, reset
	public static boolean isHeld(Player p);
	public static boolean pushAllowed(Player p);
}                                                                             // registers an AbilityLocks lock: only force_push, and only when allowed
// existing com.oldrepublic.combat.DeflectableProjectile keeps its three methods and gains:
	default boolean saberDeflectable() { return true; }                       // archive_debris returns false and implements redirect as a no-op

// existing com.oldrepublic.enemy.BlasterBolt  [lead changes the class; W4 adds the styles' looks, W5 the lance]
public enum Style { POACHER, REPUBLIC, IMPERIAL, SENTINEL }                    // nested BlasterBolt.Style, synced through SynchedEntityData
public static BlasterBolt create(Level level, @Nullable Entity owner, Vec3 pos, float damage, int lifeTicks, Style style, boolean heavy);
	// pos is the spawn point (the muzzle) and the caller then calls shoot(), exactly as with the M2 create.
	// The M2 create(Level, Entity, Vec3 pos, float, int) stays and delegates with Style.POACHER and heavy = false.
public Style style(); public boolean heavy(); public boolean parried();       // heavy: guard drain 35, no redirect, only a parry returns it
public void redirect(Vec3 velocity, @Nullable Entity newOwner, boolean parried, float damageMultiplier); // the M2 three-argument redirect delegates with 1.0
	// canHitEntity skips anything Allegiances.friendly with the owner, and any player the owner is ALLIED with

// com.oldrepublic.progression  [lead: real code]
public interface ForceModifier {
	default float costMultiplier(Player p, String abilityId) { return 1f; }
	default float regenMultiplier(Player p) { return 1f; }
	default int maxEnergyBonus(Player p) { return 0; }
}
public final class ForceModifiers {
	public static void register(ForceModifier m);
	public static float cost(Player p, String abilityId);                     // product, clamped 0.70–1.50
	public static float regen(Player p);                                      // product, clamped 0.50–1.50
	public static int maxEnergyBonus(Player p);                               // sum
}
Progression.refreshModifiers(ServerPlayer p);                                 // recompute max energy; W4 calls it from ServerEntityEvents.EQUIPMENT_CHANGE
	// Fabric has no inventory-change event: the carried-holocron bonus is computed live inside W3's ForceModifier (4.6)

// com.oldrepublic.ability  [lead: real code; W3's AbilityRuntime calls refusal before trySpendEnergy]
public final class AbilityLocks {
	public interface Lock { @Nullable Component refusal(ServerPlayer p, String abilityId); } // null = allowed
	public static void register(Lock lock);
	public static @Nullable Component refusal(ServerPlayer p, String abilityId);
}

// com.oldrepublic.force  [lead: registries and hooks real; W3 calls them from its powers; W1 and W5 register]
public enum ForceAct { PUSH, PULL, LIFT }
public record ForceBlockContext(ServerPlayer caster, ServerLevel level, BlockPos pos, BlockState state, ForceAct act, Vec3 direction, float strength, String abilityId) {}
public interface ForceBlockHandler { boolean handle(ForceBlockContext ctx); } // true = consumed
public final class ForceBlockInteractions {
	public static final TagKey<Block> REACTIVE;                               // #oldrepublic:force_reactive
	public static void register(Block block, ForceBlockHandler handler);      // throws on a duplicate block
	public static @Nullable BlockHitResult findOnAim(ServerPlayer caster, double range); // first reactive block on the eye ray
	public static boolean dispatch(ForceBlockContext ctx);
}
public interface ForceReactiveEntity {                                        // non-living: grenade, debris, pylon focus
	default boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) { return false; }
	default boolean onForcePull(ServerPlayer caster, Vec3 dir, float strength) { return false; }
	default boolean onForceLift(ServerPlayer caster) { return false; }        // each tick of the channel while aimed at it
	default boolean onForceLightning(ServerPlayer caster, float perTick) { return false; }
	default boolean onSaberThrowHit(ServerPlayer caster, float damage) { return false; }
}
ForcePowers.isHostile(ServerPlayer caster, Entity e);                         // new overload: countsAsHostile || provokedBy (a valid target); the M2 form stays for non-player casters
// existing ForceReactions.Handler gains (the defaults keep every M2 handler working unchanged):
	default void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) { interruptWindup(target); }
	default float liftHeight(LivingEntity target, float requested) { return requested; }   // combined by min; 0 refuses (Vasch: 0 unless Spent)
ForceReactions.interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause); // the M2 one-argument static delegates with (null, "unknown")
ForceReactions.liftHeight(LivingEntity target, float requested);
	// interrupt causes (frozen): force_push, force_pull, pull_disarm, saber_throw, heavy_fear, stagger, shock, unknown
// existing ControlEffects gains a kind (frozen kinds: stagger, slow, disarm, shock, hold):
ControlEffects.diminished(LivingEntity target, int baseTicks, String kind);   // the M2 two-argument form delegates with "stagger"
ControlEffects.stagger(@Nullable Entity source, LivingEntity target, int baseTicks, int amplifier, String kind); // M2 form delegates with "stagger"
ControlEffects.registerDurationModifier(DurationModifier m);                  // interface DurationModifier { float multiplier(LivingEntity target, String kind); }

// com.oldrepublic.force.sense  [lead: registry real; W3 owns the rest]
public final class SenseTargets {
	public enum Kind { BLOCK, CACHE, ANCHOR, URN, CREATURE }
	public record Point(Kind kind, Vec3 pos, int entityId, Attitude attitude) {}   // entityId -1 for blocks
	public interface Provider { void collect(ServerPlayer caster, int blockRange, int creatureRange, Consumer<Point> out); }
	public static void register(Provider provider);
	public static List<Point> collect(ServerPlayer caster, int blockRange, int creatureRange, int max);
}
AbilityInputPayload.SENSE_SLOT = 8;                                           // W3's AbilityRuntime routes it to force_sense

// com.oldrepublic.world.site  [lead: real code unless marked]
public enum SiteKind { CRYSTAL_CAVE, JEDI_RUIN, SITH_TOMB, WRECK_FIELD, REPUBLIC_OUTPOST, IMPERIAL_OUTPOST, ECHO_SHRINE, COURIERS_FALL, MISSION_SITE, RELIQUARY; String id(); static @Nullable SiteKind byId(String id); }
public record SitePiece(SiteKind kind, Identifier template, BoundingBox box, Rotation rotation, BlockPos origin, String siteKey) {}
	// siteKey names the whole site: worldgen = dimension + start chunk; runtime = dimension + record origin. HolocronData.attuned_at stores it.
public final class StructureLocator {                                         // queue and cache real; W1 fills the worldgen lookups
	public static void find(ServerPlayer p, SiteKind kind, int radiusBlocks, @Nullable Predicate<SitePiece> exclude, Consumer<@Nullable SitePiece> callback);
		// radius in blocks, converted to placement cells internally (3.8); the result is the start piece
	public static @Nullable SitePiece cached(ServerPlayer p, SiteKind kind);  // the cached result, read synchronously; never queues
	public static @Nullable SitePiece pieceAt(ServerLevel level, BlockPos pos);
	public static @Nullable BlockPos anchor(SitePiece anyPiece, SiteAnchors.Anchor anchor); // searches every piece of the same start, or the WorldSites record
}
	// The queue is a LocatorQueue(Lookup lookup, int cellsPerTick) instance; tests build their own with a fake Lookup, never the server's.
public final class SiteAnchors {                                              // loads data/oldrepublic/site_anchors/*.json through a server reload listener
	public enum Anchor { /* the frozen table below, in that order */ }
	public static List<BlockPos> of(Identifier template, Anchor anchor);      // template-local, from the sidecar
	public static @Nullable BlockPos find(SitePiece piece, Anchor anchor);    // first match in this piece, world space
	public static List<BlockPos> all(SitePiece piece, Anchor anchor);
	public static BlockPos toWorld(SitePiece piece, BlockPos local);
}
public final class RuntimeSites {
	public record Placement(SitePiece piece, BlockPos origin, Rotation rotation) {}
	public static @Nullable Placement placeSurface(ServerLevel l, Identifier template, Vec3 origin, Vec3 forward, int[] rings, int[] fallback, MeetingSites.Rules rules, int buried, @Nullable UUID owner);
		// buried = the template's lowest layers set into the ground (Echo Shrine 9, Reliquary surface 9)
	public static @Nullable Placement placeSunken(ServerLevel l, Identifier template, BlockPos surfaceOrigin, int depth, Rotation rotation, @Nullable UUID owner);
	public static Placement placeAt(ServerLevel l, Identifier template, BlockPos origin, Rotation rotation, int buried, @Nullable UUID owner); // no search: commands and client tests
}
public final class WorldSites {                                               // skeleton real; W1 fills the rules
	public record SiteRecord(SiteKind kind, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, Identifier template, @Nullable UUID owner, long placedDay) {}
	public static final class Data { /* add (≤ 512), all, nearest(kind, dim, from, radius), count(kind), expiredCairnOwners (≤ 64) */ } // detached value
	public static Data snapshot(MinecraftServer s);                           // a copy; rules and tests work on Data, never on the server's copy
	public static void commit(MinecraftServer s, Data d);
	public static List<SiteRecord> all(MinecraftServer s);
	public static void add(MinecraftServer s, SiteRecord r);
	public static @Nullable SiteRecord nearest(MinecraftServer s, SiteKind kind, ResourceKey<Level> dim, BlockPos from, int radius);
}

// com.oldrepublic.world  [W1]
public final class EchoCairns {
	public static List<BlockPos> place(ServerPlayer owner, BlockPos centre, int minRadius, int maxRadius, int minSpacing, int count, boolean cache);
	public static void reveal(ServerLevel l, BlockPos pos, UUID owner, int ticks); // W2's Sense provider calls it
	public static void expire(ServerPlayer owner);                           // lists the owner in WorldSites; cairns remove themselves lazily (3.3)
}
public final class EchoShrines {
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind);  // the pedestal; balance rings; null = retry later
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind, int[] rings, int[] fallback, MeetingSites.Rules rules); // test seam
}
public final class Outposts {
	public enum Decision { WORLDGEN, REUSE, PLACE, CAP_NEAREST }
	public record OutpostQuery(BlockPos player, @Nullable BlockPos worldgen, @Nullable BlockPos runtimeNearest, int runtimeCount) {}
	public static Decision decide(OutpostQuery q, SiteBalance b);           // pure (3.5)
	public static void ensureNear(ServerPlayer p);
	public static @Nullable BlockPos nearestTerminal(ServerPlayer p, Faction army, int radius);
}

// com.oldrepublic.quest.QuestHooks  [lead adds; W2 implements in QuestService]
static void onSiteEntered(ServerPlayer p, SitePiece piece);                   // W1 reports presence; W2 owns discovery state, XP and toast
static void onPuzzleSolved(ServerPlayer p, SitePiece piece, String puzzleId);
static void onForceSense(ServerPlayer p, int revealed);
static void onCairnTouched(ServerPlayer p, BlockPos pos, int index, boolean cache);
static void onHolocronAttuned(ServerPlayer p, String holocronId, HolocronChoice choice, boolean first);
static void onArchiveResolved(ServerPlayer p, ArchiveChoice choice);
static void onTrooperKilled(ServerPlayer p, LivingEntity trooper, Faction army, TrooperRole role, boolean wasHostile); // W4 fires; W2 applies all kill reputation
static void onTrooperTended(ServerPlayer p, LivingEntity trooper, Faction army);
static void onConstructKilled(ServerPlayer p, LivingEntity construct, SentinelVariant variant);
static void onArmourEquipped(ServerPlayer p, ArmourSet set);                  // W4, from its equipment-change listener
static void onBossPhase(ServerPlayer p, String bossId, int phase);
static void onBossYielded(ServerPlayer owner, String bossId);
static void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear); // W5 calls once per participant; W2 grants every reward
static void onBoltDeflected(ServerPlayer p, boolean parried, @Nullable Entity shooter); // new overload; fires CombatEvents.PARRY when parried; the M2 form delegates with null

// com.oldrepublic.quest  [lead: Guidance real; W2 registers providers and calls pulse from the journal-hold tick]
public enum BossOutcome { SPARED, EXECUTED, BOUND, RELEASED, BROUGHT_TO_REPUBLIC, BROUGHT_TO_EMPIRE, ECHO }
public final class Guidance {
	public record Request(int priority, Vec3 target, int callingVariant, String source) {}
	public interface Provider { @Nullable Request request(ServerPlayer p); }
	public static final int STORY = 100, MISSION = 50, INTERROGATION = 40, OUTPOST = 20;
	public static void register(Provider provider);
	public static @Nullable Request current(ServerPlayer p);                 // highest priority; ties by registration order
	public static void pulse(ServerPlayer p);                                // sends one Calling for current(p)
}
// quest.story  [W2; ChapterThree is the only way other workers read the chapter]
public enum ChapterThreeStage { NOT_STARTED, WAITING, SUMMONS, LISTENING, FIRST_ECHO, LEADS, RELIQUARY, CONFRONTATION, AFTERMATH, COMPLETE }
public record StoryData.Chapter3(...)                                         // fields of 12.1; codec with optionalFieldOf only
public final class ChapterThree {
	public enum Site { FALL, RELIQUARY }
	public static ChapterThreeStage stage(Player p);
	public static boolean atLeast(Player p, ChapterThreeStage stage);
	public static @Nullable BlockPos site(Player p, Site site);
	public static List<BlockPos> shrines(Player p);                          // ≤ 4
}
QuestEffects: CALLING_HOLOCRON 4, CALLING_CONTRACT 5, CALLING_RELIQUARY 6, CALLING_OUTPOST 7, CALLING_FALL 8; DREAM_ONES_THAT_SING 3;
              LESSON_MISSION 4, LESSON_CHAPTER_THREE 5, LESSON_BOSS 6, LESSON_RANK_THREE 7   // MentorClient.MILESTONES += mission, chapter3, boss, rank3

// com.oldrepublic.dialogue  [lead adds; W2 owns the package]
Dialogues.ON_CLOSE;                                                           // Fabric Event<Closed>: (ServerPlayer p, Identifier dialogue, boolean chose); on a choice, Escape, disconnect, death and forced close
// com.oldrepublic.identity  [lead appends in Phase 0; W2 owns the behaviour]
Faction.REPUBLIC;                                                             // appended, ordinal 3
ContextAct.KILLED_SOLDIER;                                                    // appended, MINOR dark -2
YieldOutcome.INTERROGATED; YieldOutcome.HANDED_OVER;                          // appended after SPARED, EXECUTED, FLED
// existing Yieldable gains:
	default @Nullable Identifier yieldDialogue() { return null; }             // null = M2's yield.json
Identity.promote(p, rank)                                                     // refuses Path.INDEPENDENT (asserted by a test)

// com.oldrepublic.holocron  [W3]
public enum HolocronKind { JEDI, SITH, WAYFARER }
public enum HolocronChoice { OWN, LISTEN, SEAL, BREAK }
public enum ArchiveChoice { RETURN, KEEP, UNMAKE }
public record HolocronStamp(String id, UUID owner, int serial) {}             // component oldrepublic:holocron
public final class HolocronService {
	public static void usePedestal(ServerPlayer p, BlockPos pos, HolocronKind kind);   // sneaking = restore (4.3)
	public static boolean hasAttuned(Player p, String holocronId);
	public static int attunedCount(Player p, @Nullable HolocronKind kind);    // null = any kind, world holocrons only
	public static Predicate<SitePiece> usedSites(Player p);                  // true where a pedestal already gave p a holocron: the locator's exclude
	public static boolean returnTo(ServerPlayer p, String receiver);          // jedi | sith | republic | imperial
	public static void openArchive(ServerPlayer p, BlockPos plinth);
	public static void resolveArchive(ServerPlayer p, ArchiveChoice choice);
	public static void markArchivePending(ServerPlayer p);
}

// com.oldrepublic.mission  [W2]
public final class Missions {
	public static void openBoard(ServerPlayer p, BlockPos terminal, Faction issuer);
	public static @Nullable String activeId(Player p);
	public static int ownSideContracts(Player p);
}
public final class Commendations { public static ItemStack of(Faction army, int count); }   // REPUBLIC or SITH_EMPIRE

// com.oldrepublic.enemy  [W4]
public final class Poachers { public static List<LivingEntity> spawnCrew(ServerLevel l, BlockPos at, int count, @Nullable UUID owner); }
// com.oldrepublic.enemy.trooper  [W4]
public enum TrooperRole { RIFLEMAN, SERGEANT, MEDIC, GRENADIER, OFFICER }
public final class Troopers {
	public static List<LivingEntity> spawnSquad(ServerLevel l, BlockPos at, Faction army, List<TrooperRole> roles, @Nullable UUID owner, IncidentMode incident); // a fresh squad UUID per call
	public static LivingEntity spawnOfficer(ServerLevel l, BlockPos at, Faction army, int nameSeed); // name key npc.oldrepublic.officer.<republic|imperial>.<nameSeed>
	public static LivingEntity spawnDowned(ServerLevel l, BlockPos at, Faction army, UUID owner);
	public static List<LivingEntity> spawnEscort(ServerLevel l, BlockPos at, Faction army, LivingEntity prisoner, BlockPos exit); // two noSave troopers
	public static boolean isTrooper(Entity e); public static TrooperRole role(Entity e); public static @Nullable Faction faction(Entity e);
	public static boolean isDowned(Entity e);
	public static boolean tend(ServerPlayer p, Entity downed, boolean instant); // a kneel tick (false) or a Force Heal revive (true); true once it stands
}
// com.oldrepublic.enemy.construct  [W5]
public enum SentinelVariant { WATCHER, TOMB }
public final class Sentinels {
	public static int wakeNear(ServerLevel l, BlockPos pos, int radius, @Nullable Player cause); // never wakes a sentinel dormant for cause's side; returns the number woken
	public static LivingEntity spawn(ServerLevel l, BlockPos at, SentinelVariant v, boolean bound, @Nullable UUID owner);
}
// com.oldrepublic.boss  [W5]
public final class ReliquarySites {
	public static @Nullable BlockPos place(ServerPlayer owner, Vec3 origin, Vec3 forward); // heart position, or null: retry later; adds a WorldSites RELIQUARY record with the owner
	public static @Nullable String evaluate(ServerLevel l, BoundingBox hallAndShell, Predicate<BlockPos> isWorldgenPiece); // pure check; null = accepted (3.6)
}
public final class BossArenas {
	public static boolean isInArena(Entity e);
	public static void resolve(ServerPlayer owner, BossOutcome outcome);
	public static boolean isResolved(ServerPlayer owner);
}
// com.oldrepublic.equipment  [W4]
public enum ArmourSet { REPUBLIC_TROOPER, SITH_TROOPER, JEDI_ROBES, SITH_ROBES }
public record EquipmentTraits(ArmourSet set, float costPct, float regenPct, int maxEnergy, float blasterResist, float meleeResist) {} // component oldrepublic:equipment_traits
public final class ArmourSets {
	public static int count(LivingEntity e, ArmourSet set);                  // jedi_knight_robe and sith_lord_robe count as their set's robe
	public static boolean fullSet(LivingEntity e, ArmourSet set);
	public static ItemStack piece(ArmourSet set, EquipmentSlot slot);
	public static Allegiance disguise(LivingEntity e);                       // NONE while the feature is cut
}
// com.oldrepublic.saber  [lead appends the constants; W5 owns stats and looks]
HiltParts.Pommel.ARCHIVIST; HiltParts.Emitter.RELIQUARY; BladeColor.AMBER;    // appended
// com.oldrepublic.registry.M3Items  [lead: real code]
	// Registers the items that other workers' recipes, loot and rewards name, so every merge resolves them:
	// durasteel_plate, lumen_thread, grave_silk (W1); republic_commendation, imperial_commendation (W2);
	// holocron_shard (W3); singing_crystal (W5). The hilt parts pommel_archivist and emitter_reliquary are
	// HiltPartItems registered explicitly in SaberItems (as built: SaberItems has one field per part, nothing automatic).
	// Each gets a placeholder texture and a lang line in its owner's lang part; the owner then owns art, name and behaviour.

// client
com.oldrepublic.client.quest.JournalTabs.register(Tab tab)                    [lead real; W2 and W3 register]
record Tab(String id, Component label, int order, Supplier<Panel> panel, Predicate<LocalPlayer> visible)
interface Panel { void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick); default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return false; } default boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) { return false; } }
com.oldrepublic.client.fx.ClientEffects.addListener(Consumer<EffectEventPayload> listener)   [lead; W3's Danger Sense listens]
com.oldrepublic.client.fx.Vfx.tellCount(int base)                             [lead; ignores the preset multiplier (8.5, 13.1)]
com.oldrepublic.client.force.DangerTells.isTell(Identifier id, int variant)   [lead; delegates to DangerTellTable]
com.oldrepublic.client.combat.ClientHolds                                     [lead; the client half of Holds: no gravity, movement zeroed, keys swallowed, HUD hint]
ClientConfig.bossMusic, robeModels, showMissionTracker, showBarks              [lead; the four /oldrepublicfx toggles go into ClientCommandsFx in Phase 0]
IdentityStyle REPUBLIC case; StandingPanel with 4 faction rows                [lead adds the cases; W2 owns the panel]
ModSounds.holder(SoundEvent e)                                                [lead; returns the registered Holder for vanilla APIs]
```

**Danger Sense tell table (frozen).** `DangerTellTable.isTell(id, variant)` is true for exactly these rows. A server game test checks every tell row of 13.3 against it.

| Event | Tell variants |
|---|---|
| `npc_windup` | 0 (`WINDUP_FIRST`) |
| `captain_heavy`, `acolyte_shove` (M2) | 0 (the wind-up) |
| `trooper_charge` | any (the variant is the bolt style) |
| `grenade_tell`, `grenade_fuse` | any |
| `tomb_vent` | 0 |
| `sentinel_slam`, `sentinel_lance` | 0 |
| `boss_hurl`, `boss_palm`, `boss_lash`, `boss_surge`, `boss_grip`, `boss_lunge` | 0 |

**Other lead Phase 0 changes:**

- **Call sites.** The lead makes these edits before any worker owns the files; afterwards each file is its owner's again (15.2), and the owner keeps the call.
  - Damage: `SaberCombat.modifyIncomingDamage` ends with `DamageModifiers.apply`. The lead's player-damage hook calls `Attitudes.snapshotOnDamage`.
  - Costs: `AbilityCosts.costMultiplier` multiplies by `ForceModifiers.cost`; `Progression` reads `regen` and `maxEnergyBonus`; `AbilityRuntime` asks `AbilityLocks.refusal` before `trySpendEnergy`.
  - Bolts: `BlasterBolt.canHitEntity` uses `Allegiances.friendly` and skips players the owner is ALLIED with; `PoacherCrew` implements `Aligned` (`CREW`).
  - Events: `QuestHooks.onSaberTechnique` and `onAbilityHit` fire `CombatEvents.TECHNIQUE`, `PARRY` (melee) and `POWER_HIT` (living targets only). `SaberGuard` lines 197 and 211 call the new `onBoltDeflected(player, parried, bolt.deflectableShooter())`, which fires `PARRY(player, shooter if living, true)` when parried. No worker edits another worker's code to get these events.
  - **Hostility.** Troopers are not `Enemy` (7.1), so every `instanceof Enemy` check moves onto `Attitudes`:

    | Site | Becomes |
    |---|---|
    | `Progression.onDeath` (262) | kill XP only when `Attitudes.hostileSnapshot(victim, killer)` |
    | `SkillService` on-kill passives (607) | `hostileSnapshot` (`hungering_dark` never pays for an Allied, Neutral or provoked kill) |
    | `ForcePowers.isHostile` (150) and `hostileNear` (156) | `countsAsHostile` (Heal and Jump mastery cannot be farmed beside an Allied garrison) |
    | `ThrownSaber` (282, 306) | target and ricochet choice through `ForcePowers.isHostile(owner, e)` |
    | `PushEffects` (79, 135), `ForcePull` (230, 283), the `ForceLightning` chain (429), the Force Jump crush | skip `Attitudes.shieldedFromArea`; mastery and credit through `countsAsHostile` |
    | `SaberTechniques` heavy fear (213) | `ForcePowers.isHostile(p, e)` |
    | `Lessons` (91, 100) | `countsAsHostile` |
    | `ContextualActs` (93, 102) | `Attitudes.toward(e, player) == HOSTILE` |
  - **Interrupts.** The new `interruptWindup(target, caster, cause)` overload, with every caller passing its cause: `ForcePull` 323 (`pull_disarm`, or `force_pull` for a plain pull), `ThrownSaber` 289 (`saber_throw`), `SaberTechniques` 211 (`heavy_fear`), `ControlEffects.stagger` (`stagger`), `ForceLightning` 455 (`shock`). Push calls it with `force_push` for every living target it moves.
  - **Control kinds.** `ForcePull` 300 → `disarm`, `ForcePull` 408 → `stagger`, `ForceLightning` 445 → `shock`, `ForceJump` 484 → `stagger`, `PushEffects` 140 → `stagger`. `NpcGuard` 85, which slows a parried player directly today, goes through `ControlEffects.stagger(mob, player, ticks, 2, "stagger")`, so Iron Discipline applies to it.
  - **Reactive entities.** Lightning's aim ray and the thrown saber reach `ForceReactiveEntity` (10.6); Lift asks `ForceReactions.liftHeight` for living targets.
  - **Lang.** `mergeLang` in `build.gradle` throws on **any** duplicate key across parts, even with the same value (`if (merged.containsKey(k))`).
- **Skills:** the `SkillRules.visibleTrees` overload and `TreeVisibility.SENSE` (10.5).
- **Features:** `OldRepublicMod` and `OldRepublicModClient` call the new `HolocronFeature`, `MissionFeature`, `EquipmentFeature`, `BossFeature` and their client twins (stub classes, filled by the owners). `fabric.mod.json` lists the empty mixin configs `oldrepublic-boss.mixins.json` and `oldrepublic-equipment.mixins.json`.
- **Sounds:** `tools/assets/sounds/events_m3.json` lists every **new** sound id in 16 with its feature, subtitle, `loop` and `stream` flags, and volume. `gen_sounds.py` learns the `stream` field and writes it into `sounds.json`; `gen_sounds.py --m3` renders the new modules. Every new id gets placeholder audio and a `ModSounds` constant. Ids that 16 marks "existing, reused" are not listed and keep their M1 or M2 audio.
- **Id check:** `tools/check_m3_ids.py` (lead, run on CI) fails when an id that 16 marks new already exists in `sounds.json`, `assets/oldrepublic/particles/`, `assets/oldrepublic/items/` or a lang part, or when an id marked reused is missing.
- **Data:**
  - tags `force_reactive`, `jedi_robe_cloth`, `sith_robe_cloth`, `c:bosses`, the five structure tags and empty biome tags `has_structure/<id>`;
  - item tags `repairs/trooper_armour`, `repairs/jedi_robes`, `repairs/sith_robes` (created empty; W4 fills them);
  - `#built_blocks` += every M3 structure, decor and puzzle block, plus `blackstone`, `polished_basalt` and `deepslate_tiles` (the Imperial post's materials);
  - `#push_resistant` += `ruin_sentinel`, `orren_vasch`;
  - every vanilla-namespace tag addition of 9.1 and 12.5 (`enchantable/*`, `is_explosion`, `is_projectile`) and the `force_harm` additions.
- **Items:** `M3Items` (above).
- **Journal:** `JournalEntries` appends the 6.7 ids and sets `CHAPTER_THREE_FIRST = THE_ONES_THAT_SING`; `JournalScreen` gets chapter boundaries and the `JournalTabs` strip.
- **Test harness:**
  - `ContractsM3GameTests` (lead, 17.1) is merged with Phase 0 and green on CI before workers start.
  - Fixtures under `src/gametest/resources/data/oldrepublic_test/structure/` (path confirmed by `m3_tests.txt`), each at most 7 blocks high with its own anchor sidecar and used with `skyAccess = true`: `arena_24x7x24` (a floor, four pylon stand-ins and a dais block), `urn_fixture`, `seal_fixture`, `pedestal_fixture`, `terminal_fixture`, `anchor_fixture` (rotation).
  - `M3TestPlayers.listed(helper)`: a mock `ServerPlayer` that is in `level.players()`, so proximity rules fire. It is built on `GameTestHelper.makeMockServerPlayerInLevel` (probed in `m3_tests.txt`); M2's `FakePlayer` is not listed.
  - Stub classes `src/gametest/java/com/oldrepublic/gametest/m3/{World,Story,Holocron,Trooper,Boss}ClientSteps.java` with an empty `run(context, server)`, so `ExplorationClientTest` compiles from day one. Each worker owns its own class afterwards.
  - `/oldrepublic hold <player> <ticks>` (lead, operator only) for the dedicated-server hold test.
  - gametest `fabric.mod.json` entries for the classes in 17.1.

**Test rules (frozen; repeated in `WORKER_M3.md`):**

- Every test declares `maxTicks`, and none exceeds 400. Long timers are tested through pure rule functions (`*Rules.step(state, now, balance)`, one per timer: puzzle reset, vents, garrison refill, the heart, re-sing, provocation and incident expiry), so a 12,000-tick rule is checked in a few ticks.
- Server tests use the lead's small fixtures. They never place a whole M3 template: they load it and read its sidecar. Whole templates and worldgen run only in client tests, through `/oldrepublic site place`.
- Placement is tested through the seams (`Outposts.decide`, the `EchoShrines.place` overload with test rings, `ReliquarySites.evaluate`, `WorldSites.Data`), never with the balance rings.
- No test writes global state: not `WorldSites`, the patrol caps, the `StructureLocator` queue or the world's day time. Daily rules take the day as an argument (5.5).
- Proximity rules use `M3TestPlayers.listed`. Two-player rules use two listed players. All randomness is seeded.

**Frozen templates and anchors.** Generators are written by the owner, but these ids, sizes and anchor names are frozen in Phase 0.

| Template | Size | Owner | Anchors (`SiteAnchors.Anchor`) |
|---|---|---|---|
| `jedi_ruin/courtyard` | 31×14×31 | W1 | `STAIR_MOUTH`, `SURFACE_GUARD`, `DIGGERS`, `CHEST_1` |
| `sith_tomb/gate` | 17×12×17 | W1 | `STAIR_MOUTH`, `SURFACE_GUARD` |
| `jedi_ruin/stair`, `wreck_field/scar`, `sith_tomb/path`, `wreck_field/path` | 7×12×9, 15×6×15, 5×3×9, 5×3×9 | W1 | (none) |
| `jedi_ruin/listening_hall` | 25×14×25 | W1 | `HEART`, `PEDESTAL_JEDI`, `COUNTERWEIGHT_W`, `COUNTERWEIGHT_E`, `COUNTERWEIGHT_S`, `SENTINEL_1`, `SENTINEL_2`, `CHEST_1` |
| `jedi_ruin/pilgrim_niche`, `sith_tomb/pilgrim_niche` | 7×6×7 | W1 | `PEDESTAL_WAYFARER` |
| `jedi_ruin/anchor_cache`, `sith_tomb/rubble_room` | 7×6×7, 9×7×9 | W1 | `CACHE` |
| `sith_tomb/dig_camp`, `wreck_field/salvage_camp` | 19×8×19, 15×8×15 | W1 | `SQUAD`, `CHEST_1` |
| `sith_tomb/processional` | 9×14×27 | W1 | `VENT_1`–`VENT_4`, `SENTINEL_1`, `DIGGERS`, `NICHE` |
| `sith_tomb/burial_chamber` | 27×14×27 | W1 | `HEART`, `URN_KEEPER`, `URN_1`–`URN_4`, `PEDESTAL_SITH`, `SENTINEL_1`, `SENTINEL_2`, `CHEST_1` |
| `wreck_field/hull` | 41×18×19 | W1 | `HEART`, `HATCH`, `COUNTERWEIGHT`, `PEDESTAL_WAYFARER`, `DECK_CACHE`, `ANCHOR_LOCKER`, `SCAVENGER_1`, `SCAVENGER_2`, `SURFACE_GUARD` (beside the hull, outside it) |
| `outpost/republic_post`, `outpost/imperial_post` | 23×12×23, 23×14×23 | W1 | `TERMINAL`, `OFFICER`, `GARRISON_1`–`GARRISON_4` |
| `site/echo_shrine_{jedi,sith,wayfarer}` | 15×14×15 | W1 | `HEART`, `PEDESTAL`, `COUNTERWEIGHT`, `GUARD` |
| `story/couriers_fall` | 15×9×11 | W2 | `PEDESTAL_WAYFARER`, `STRONGBOX`, `SCAVENGER_1`, `SCAVENGER_2` |
| `mission/patrol_ambush` | 13×7×13 | W2 | `DOWNED_1`–`DOWNED_3`, `SENTINEL_1`–`SENTINEL_3`, `CRATE` |
| `mission/smuggler_cache`, `mission/fence_camp`, `mission/raider_camp_small` | 13×8×13, 15×8×15, 11×7×11 | W2 | `CREW_1`–`CREW_4`; plus `CHIEF`, `CRATE` (smuggler); `FENCE`, `BOOK`, `FIRE` (fence) |
| `mission/survey_post`, `mission/skirmish_line` | 15×8×15, 17×6×9 | W2 | `SQUAD_1`–`SQUAD_5` (the fifth only at level ≥ 11); plus `WOUNDED` (survey post) |
| `boss/reliquary_surface` | 17×16×17 | W5 | `DESCENT_TOP` |
| `boss/reliquary_hall` | 31×18×31 | W5 | `BED`, `HEART`, `THRESHOLD`, `DOOR_SEAL`, `LECTERN`, `DAIS`, `PYLON_1`–`PYLON_4`, `GALLERY_W`, `GALLERY_E`, `SENTINEL_1`–`SENTINEL_4`, `COLUMN_1`–`COLUMN_6`, `DEBRIS_1`–`DEBRIS_4`, `PLINTH`, `VAULT_SEAL` |

**Frozen rules** (not signatures, but contracts between workers):

- **Anchors are sidecars** (3). Each generator writes the real block in every anchor cell and emits `data/oldrepublic/site_anchors/<template path>.json` (`{"<ANCHOR>": [[x, y, z], …]}`, template-local). In worldgen and shrine templates the generator writes a `site_spawner` block at `SQUAD`, `SENTINEL_n`, `SCAVENGER_n`, `DIGGERS` and `GUARD` cells, with its group (3.2). `CHEST_n`, `CACHE`, `DECK_CACHE` and `ANCHOR_LOCKER` mark loot chests (Seeker's Sight, 10.1).
- **No template entities.** No template carries an entity list. Every squad gets a fresh UUID from `Troopers.spawnSquad`.
- **Entity NBT keys:** `Role`, `Squad`, `Incident`, `Provoked`, `Engaged`, `BleedAt`, `Terminal` (terminal position and slot), `Variant`, `Bound`. A STRUCTURE spawn reason never re-rolls a `Role` that was read.
- **The terminal loading rule** (3.3) and the "stale copy discards itself" rule (7.4).
- **Armour modifier ids:** `oldrepublic:armour.<slot>.<armor|toughness|knockback|speed>` (9.2). **Held gravity:** `oldrepublic:held_gravity`.
- **Officer name keys:** `npc.oldrepublic.officer.<republic|imperial>.<0-5>`.
- The interrupt causes, control kinds and Danger Sense table above. Sense providers use POI queries, never block scans (3.3).
- **Stub return values.** Entity-returning stubs (`Troopers.spawnOfficer`, `spawnDowned`, `Sentinels.spawn`) spawn and return an invulnerable vanilla `ArmorStand` named after the request. List-returning stubs (`spawnSquad`, `spawnEscort`, `Poachers.spawnCrew`) return an empty list. `ArmourSets.piece` returns `ItemStack.EMPTY`. `StructureLocator.find` calls back with null. `EchoShrines.place` and `ReliquarySites.place` return null (the caller retries). `HolocronService`, `BossArenas`, `Troopers.isDowned` and `tend` return false or 0. `ChapterThree.stage` returns `NOT_STARTED`.

**Probes before workers start.** The api-probe workflow matrix is changed to glob `tools/api-probe/*.txt`, so the M2 query files that never ran now run. Three new files:

- `m3_equipment.txt`: the `ArmorMaterial` constructor, `EquipmentAssets`, `EquipmentLayerRenderer#renderLayers`, `ArmorModelSet`, `HumanoidArmorLayer`, the equipment asset JSON format and texture paths, and the `ItemAttributeModifiers` merge order with `humanoidArmor`.
- `m3_boss.txt`: the `ServerBossEvent` constructor; `body:net.minecraft.server.network.ServerGamePacketListenerImpl#tick` (the floating kick and its gravity check); `ServerChunkCache`; `PoiManager#findAll`; `MusicManager`; `net.minecraft.client.Options` key defaults (is H free?); `SoundEvent` holder wrapping for `ModSounds.holder`.
- `m3_tests.txt`: `GameTestHelper` (`makeMockServerPlayerInLevel`), the gametest structure path, `TestEnvironmentDefinition` (time and weather isolation), and the `GameTestServer` world options (whether structures generate).

Already confirmed from the dumps, so recorded in the notes and not probed again: `ServerLevel#findNearestMapStructure`; `StructureManager#getStructureWithPieceAt`, `getAllStructuresAt` and `startsForStructure`; `RandomSpreadStructurePlacement` (one optional exclusion zone); `SinglePoolElement#getSettings` (ignores structure blocks); `StructureTemplate#placeEntities`; Fabric `GlobalAttachments`; Fabric `ArmorRenderer` with `submitTransformCopyingModel`; `TestDedicatedServerContext` with `createServer(Properties)`; `PoiHelper.register` and `ServerLevel#getPoiManager`; `EntityType.Builder#notInPeaceful` and `isAllowedInPeaceful`; the `GRAVITY` and `FALL_DAMAGE_MULTIPLIER` attributes; `areEntitiesLoaded` and `isPositionEntityTicking`; `Item.Properties#humanoidArmor` and `#attributes`; `ItemTooltipCallback`; `ServerEntityEvents.EQUIPMENT_CHANGE`.

Every probe output is copied into the scratchpad `api/` folder that workers grep. Each result goes into "Phase 0 notes (as built)" with the fallback chosen when a probe fails (8.5 for the boss bar, 11.1 for the key, 9.4 for the robe renderer).

**Seed survey.** The lead runs the playthrough seed once, from the point where Chapter Two ends, and logs the positions of the nearest Watchers' Rest, Sith Tomb, Wreck Field and both outposts. They go into the Phase 0 notes and drive 17.3.

**Documentation debt (lead, in Phase 0):**

- mark Milestone 2 green in `FEATURE_CHECKLIST.md`, and add the M2 sections still missing from `VFX_SFX_SPEC.md` and `AUDIO_BRIEF.md`;
- record Gabriel's decisions in `DECISIONS.md` (armour moved from M4 to M3, the two trooper armies, robes for both Orders) and the lore notes of section 18;
- write `docs/WORKER_M3.md` with the M2 lessons every worker reads first:
  - sealed attachments: commit with `setAttached(data.copy().seal())`, read `get` again after any commit, never mutate a synced view;
  - the test rules above;
  - commands take a single player (`@p`), never a selector list;
  - key handling: `consumeClick` for taps, and a tap is a short hold for HOLD_RELEASE abilities;
  - all randomness in tests is seeded.

**Phase 0 notes (as built).** The lead fills this list during Phase 0. It records every place where the code had to choose something the pseudo-Java above left open, each probe result and the seed survey. These notes are part of the frozen contract.

#### Process (as built)

- **One shared working tree, no worker branches.** The five workers edit the same checkout in parallel, each in their own files, and never commit. The lead checks each worker's file list with `tools/check_ownership.py W<N> --files ...`, commits it, and builds it on CI in the 15.3 merge order. CI runs `check_m3_ids.py` and `check_ownership.py --overlaps` on every push. This replaces the per-worker branches and the `m3-phase0` tag of 15.3 step 2.

#### Corrections to 15.1

- **Hilt part items:** `SaberItems` has one explicit field per hilt part; nothing registers parts automatically. `pommel_archivist` and `emitter_reliquary` are `HiltPartItem`s in `SaberItems`, not `M3Items`.
- **`Attitudes.shieldedFromArea`:** true only when some rule gives a non-null `toward` and the entity neither counts as hostile nor was provoked. The literal `!countsAsHostile && !provokedBy` would shield every non-`Enemy` (players, animals, villagers) and break M2 Push, Lightning and aim assist.
- **`PoacherCrew`** is an interface; it now extends `Aligned` with a default returning `CREW`.
- **`Guidance` data:** `Request` has no field for the 13.3 data, so `Guidance.variant(variant, data)` packs data into bits 8 and up and it travels as the effect event's `entityId`. OUTPOST sends the army as a `Faction` ordinal in `entityId`, not as the sign of `intensity`.
- **Standing rows:** `Faction.REPUBLIC` is appended (ordinal 3); `StandingPanel` uses its own display order (Jedi, Sith, Republic, Frontier).
- **Reliquary position:** `ChapterThree.site(p, RELIQUARY)` returns one position, the heart; `Chapter3` stores only that one.
- **`ContextualActs` threats:** `toward(e, player)` cannot run when a threat is recorded (no player yet), so every non-player living attacker is recorded and the HOSTILE check runs at the attacker's death.
- **Push and plain Pull interrupt wind-ups** now, as 15.1 asks; in M2 they did not.
- **Id check baseline:** "already exists" means "existed at commit fa82dc8", frozen in `check_m3_ids.py`, because Phase 0 itself registers the new sound ids.
- **Lance bolt:** the sentinel lance's bolt style (7.7) is drawn by the `BlasterBolt` renderer in `client/enemy/**`, so W4 draws every `BlasterBolt.Style`, including the lance, from the description in 7.7.

#### Combat core (`combat`, `client/combat`)

- **CombatEvents:** the listener methods are `Parry.onParry(defender, attacker, projectile)`, `PowerHit.onPowerHit(caster, abilityId, target)` and `Technique.onTechnique(p, technique, target, hit)`.
- **Attitudes rules:**
  - `Rule.toward` reports the reputation tier only. Provocation and engagement are reported only through `provokedBy` and `engaged`.
  - Rules are asked in registration order and the first non-null `toward` wins. `engaged` and `provokedBy` are true if any rule says so.
  - `countsAsHostile` and `provokedBy` are false for non-living entities and when the entity is the player.
  - Troopers are not `Enemy`, so without a rule they fall back to NEUTRAL.
- **`shieldedFromArea`:** true only when some rule gives a non-null `toward` and the entity neither counts as hostile nor was provoked. Anything no rule covers (vanilla mobs, animals, players) is never shielded, so it keeps its M2 behaviour. P0-B's call sites use `Attitudes.shieldedFromArea` directly.
- **Snapshots:** taken in `ServerLivingEntityEvents.AFTER_DAMAGE` when `damageTaken > 0`, and in `ALLOW_DEATH` for the killing blow, because Fabric skips `AFTER_DAMAGE` for lethal hits. `ALLOW_DEATH` runs before `AFTER_DEATH` pays kill XP. Snapshots are stored per victim and attacker UUID in a WeakHashMap. With no snapshot, `hostileSnapshot` falls back to the live `countsAsHostile`.
- **DamageModifiers:** every modifier sees the same unmodified amount and their ratios multiply, so registration order does not matter. The product is clamped to `damageModifierMin`/`Max` from the `progression` balance (0.25–2.0). Amounts of 0 or less pass through unchanged.
- **DangerTellTable:** a `Map<Identifier, Integer>` where `ANY = -1` means every variant is a tell. The ids are built from strings, so it does not depend on the owners' constants.
- **Holds data:**
  - Record `Holds.HeldState(long since, long until, String source, Vec3 anchor, boolean pushAllowed)` with a `ByteBuf` stream codec. Constants `Holds.HELD` and `Holds.GRAVITY_MODIFIER`.
  - The attachment is synced to its owner only (`targetOnly`) and is not persistent. A new hold replaces the current one.
  - The anchor is shortened toward the player until their box fits there (10 steps), so a low ceiling never puts them inside a block.
- **Holds server behaviour:**
  - The tick runs on `END_LEVEL_TICK` over `level.players()`, so game-test mock players listed in a level are held too.
  - Each tick it sets velocity to 0.5 × the offset to the anchor, with `hurtMarked`. After a 10-tick settle, it teleports the player back if they drift more than 0.5 blocks.
  - Release paths: expiry, death (`AFTER_DEATH`), logout (`LEAVE`), dimension change (`AFTER_PLAYER_CHANGE_LEVEL`), respawn (`AFTER_RESPAWN`, which drops the modifier an End-exit respawn copies across) and spectator mode. Callers pass `break`, `release` or `reset`.
  - The lock refuses every power except `force_push` when the hold allows it. Its refusal text is `message.oldrepublic.combat.held` ("You are held fast.").
- **Hold command:** ticks range 0–6000, and 0 releases. It lifts the player 1.5 blocks with Push allowed and source `command`. The `hold` literal also requires gamemaster permission.
- **BlasterBolt:** `parried` is a server-side field set by every redirect. The damage multiplier compounds over repeated redirects. The ALLIED pass-through is checked on the server only; the client never predicts it.
- **`Vfx.tellCount`:** returns `base` unscaled and still counts toward the per-second total. It is capped only by the Cinematic budget (2,600 per second), the largest of any preset, and always returns at least min(1, base).
- **ClientHolds:**
  - On `START_CLIENT_TICK` it releases and drains up, down, left, right, jump, sneak, sprint, attack and use. It does the same for every `ForceKeys.SLOTS` key except the slot holding `force_push` when Push is allowed.
  - It turns sprint off and sets velocity to 0.5 × (anchor − position).
  - When the hold ends it calls `KeyMapping.setAll()`, if no screen is open, so keys still held down work again.
  - The hint is drawn 20 px under the crosshair in amber with fade lines. It fades in over 6 ticks and pulses through `UiAnim.pulse`, which holds steady when flashing is off.

#### Force, progression, abilities, saber and skills

- **Kinds and causes:** the control kind strings are public constants on `ControlEffects`, and the interrupt cause strings are public constants on `ForceReactions`.
- **Duration modifiers:**
  - `DurationModifier` is an interface nested in `ControlEffects`.
  - Multipliers multiply together and are applied after diminishing returns.
  - The public `ControlEffects.duration(target, ticks, kind)` applies the modifiers without diminishing returns. Lightning uses it for non-resistant targets.
- **`liftHeight`:** the smallest answer of all handlers wins, and it is never below 0.
- **`ForceBlockInteractions.findOnAim`:**
  - It clips the block outline first and returns that block if it is reactive.
  - Otherwise it clips the collision shape and returns that block only if it is reactive.
  - `dispatch` looks the handler up by `state.getBlock()` and does not re-check the tag.
- **`ForceReactiveEntity.findOnAim`:** a static helper. It uses `PullTargeting.rayEnd`, the entity box inflated by 0.1, and picks the nearest hit.
- **Lightning and reactive entities:**
  - While a reactive entity on the aim ray accepts `onForceLightning`, it takes the whole channel and living targets are dropped.
  - `perTick` is the damage of one primary damage tick divided by `damageIntervalTicks`.
  - The arc's contact point is the reactive entity's box centre.
  - When the reactive entity changes, targets are chosen again at once.
  - Only the chain jumps skip shielded creatures.
- **ThrownSaber and reactive entities:**
  - When a reactive entity accepts the hit, it counts as a hit and the saber turns back.
  - Each reactive entity is offered the hit once per throw.
- **Aim assist:** the 8-argument `PullTargeting.find` takes an `assisted` filter. A shielded creature is never picked by aim assist but can still be targeted on the crosshair ray.
- **Lock checks:** `AbilityLocks` is checked in `AbilityRuntime.start` and again in `ForcePowers.mayStart`. Both checks run before any energy is spent.
- **`refreshModifiers`:** it is `commit(player, get(player))`. The modifier max energy bonus is flat and added before the skills' percent bonus. The regen multiplier is applied after the skills' regen percent.
- **SenseTargets:**
  - `Point.attitude` is NEUTRAL for blocks, and `entityId` is -1 for blocks.
  - An exception in one provider is logged and skipped.
- **Balance keys:** `DamageModifiers` clamps to `ProgressionBalance.damageModifierMin/Max`. `killRefundCap` and `parryRefundCap` are not read yet; W3, W4 and W5 will use them.
- **AMBER:**
  - The colour is `0xFFB02A`.
  - Like every palette colour, the workbench offers it for any crystal.
  - Trial crystals' natural hues are exact palette entries, so `BladeColor.nearest` returns the same colours as before.
- **New hilt parts:** they use base stats (the `SaberStats` switch statements have no case for them) until W5 adds their stats. The lightsaber item model falls back to the existing part models.
- **Placeholder models:** they reuse the pommel_balanced and emitter_duelist textures until W5 adds the new parts to `PARTS` in `gen_textures_saber.py`.

#### Story, identity, stubs, items and client wiring

- **Guidance:**
  - `current()` returns the highest priority; on a tie, the provider registered first wins.
  - `pulse()` sends one Calling event. `callingVariant & 0xFF` is the effect variant, and `callingVariant >>> 8` goes in the event's `entityId`. `intensity` stays the closeness that `Calling` already sends.
  - Providers pack data with `Guidance.variant(variant, data)`: the HolocronKind ordinal for HOLOCRON, which is what 13.3 asks for, and the army's Faction ordinal for OUTPOST.
- **Chapter3 record:**
  - `stage`: the enum, saved by ordinal.
  - `waitSince`: -1 when not set.
  - `leads`: an insertion-ordered set, at most 16.
  - `cairns`: at most 3, with `cairnsTouched` as a bitmask.
  - `fall`: nullable.
  - `shrines`: at most 4.
  - `reliquary`: one nullable position, the heart.
  - `boss`: 0 none, 1 yielded, 2 resolved.
  - `outcome` and `stance`: a BossOutcome ordinal, or -1.
  - `echoDay`: -1 when not set.
  - `echoClears`: rematches won.
  - The record is immutable. Every codec field uses `optionalFieldOf`, and the compact constructor truncates anything over the limits.
  - The `c3.*` flags and counters live in StoryData's existing flags and counters.
- **CombatEvents:**
  - TECHNIQUE fires for every technique, including `"parry"`.
  - A melee parry fires PARRY from `onSaberTechnique("parry")`; a bolt parry fires it only from `onBoltDeflected`. Neither calls the other, so each parry fires PARRY once.
- **ON_CLOSE:**
  - It fires after the session has been removed.
  - `chose` is true when the dialogue ends while a choice is being applied: an option that closes it, or an action that closes it or opens another dialogue.
  - `chose` is false for Escape, walking away, damage, death, dimension change, disconnect, an `open` that replaces it outside a choice, and `close()` outside a choice.
  - When `open` replaces a session, the old session's event fires after the new one is sent.
  - A session that `tick` drops after its player is gone fires nothing, because there is no ServerPlayer to pass.
- **ContextualActs line 93:** any living attacker that is not a player is recorded as a threat. The HOSTILE check against the killer runs at the attacker's death. Vanilla results are unchanged, because only `Enemy` falls back to HOSTILE.
- **Data formats:**
  - `EquipmentTraits` saves the set by its string id and syncs it by ordinal through `ByteBufCodecs.idMapper`.
  - `HolocronStamp` fields are `id`, `owner` and `serial`.
- **Placeholder entities:** stubs that return an entity spawn an invulnerable vanilla ArmorStand with a visible name: `"Trooper stub: <request>"` or `"Sentinel stub: <variant>[ bound]"`.
- **M3Items:**
  - Commendations and `holocron_shard` are `Rarity.UNCOMMON`.
  - `singing_crystal` is `stacksTo(1)` and `Rarity.RARE`.
  - Creative tab entries come through `ModRegistry.item`.
- **Journal:**
  - `JournalTabs` panels get book-local, unscaled coordinates. The box is `(LEFT_X, PAGE_TOP)`, both pages wide, down to `FEED_BOTTOM`.
  - A tab's `panel` supplier is called each time the journal opens on that tab or switches to it. A resize keeps the panel.
  - `JournalEntries.CODEX` places the 15 `holocron_<id>` entries right after `holocrons`.
- **Placeholder text:** the lesson banners for variants 4–7 use placeholder wording in `quest.json`. Rank 3 uses the spec's "Knight of the Watch", "Lord of the Empire" and "Pathfinder". W2 may reword them.
- **Texture generator:** `gen_textures_m3items.py` owns only the placeholders. When an owner replaces an item's art, remove that item from `TEXTURES` in the generator, or a rerun will overwrite the new art.

#### Sites and test harness (`world.site`, `src/gametest`)

**Paths and ids**
- Test fixtures are `oldrepublic_test:<name>`, stored in `src/gametest/resources/data/oldrepublic_test/structure/<name>.nbt`.
- Sidecar format: `{"ANCHOR": [[x,y,z], ...]}` at `data/<ns>/site_anchors/<path>.json`.
  - Each file's id is the template id.
  - Any namespace and subfolder is loaded, so the `oldrepublic_test` fixtures load too.
  - An unknown anchor name is logged and skipped. A malformed file (bad JSON, a wrong shape or a non-numeric coordinate) is logged and skipped whole.
- `siteKey` is `<dim>|chunk|<startX>|<startZ>` for a worldgen piece, and `<dim>|site|<x>|<y>|<z>` of its template origin for a runtime site.
- Transforms use pivot `BlockPos.ZERO` and no mirror, for both jigsaw and runtime pieces:
  - `toWorld(piece, local) = origin + StructureTemplate.transform(local, NONE, rotation, ZERO)`.
  - So CW90 maps (x, z) to (−z, x).

**WorldSites**
- `SiteRecord(kind, dimension, origin, rotation, template, @Nullable owner, placedDay)`.
- Codec:
  - kind is stored as its id string;
  - rotation as its ordinal, optional, defaulting to NONE;
  - owner is optional;
  - `placed_day` is overworld gameTime / 24000.
- Persistent, not synced. Every change goes through `snapshot`, then a change, then `commit` with a copy.
- `Data.add`:
  - replaces a record with the same dimension, origin and template;
  - when 512 records are held, drops the oldest record that is not an outpost.
- `nearest` measures horizontally to the record origin, with the radius inclusive; on a tie the older record wins.
- The expired cairn owners are an insertion-ordered set capped at 64, dropping the oldest first.

**Placement (RuntimeSites)**
- A stand is a feet position. The template is centred on it horizontally, with layer `buried` at `stand.y`.
- A template's entrance is its local north side. `placeSurface` turns it to face the origin: N→NONE, E→CW90, S→180, W→CCW90.
- `Placement.origin` is the stand (or `surfaceOrigin` for a sunken site). `SitePiece.origin` and `SiteRecord.origin` are the template origin.
- The surface search:
  - checks 16 angles per ring, the first along `forward`;
  - searches all `rings` together and keeps the best score (M2's: height above the ring average plus openness × 4);
  - searches `fallback` only if `rings` found nothing;
  - returns null instead of M2's forward fallback.
- Each candidate must:
  - pass `MeetingSites.evaluate`;
  - have every chunk of the clearance box plus one block loaded;
  - have no worldgen piece intersecting the clearance box;
  - have no `#built_blocks` inside the template box.
- `adaptTerrain` (M2's rule):
  - fills support at most 8 deep: grass at groundY, then dirt, then stone;
  - never fills a cell next to a built block, and stops that column when it meets one;
  - clears natural clutter up to box.maxY + 2;
  - puts grass back on dirt it uncovered.
- `placeSunken`:
  - the shell is the box inflated by 2;
  - the placement is refused if the shell plus 1 is not loaded, if the shell is within 8 blocks of the world's bottom, if it touches a worldgen piece, or if it holds a built block;
  - no terrain is adapted.
- Every `place*` call records itself in WorldSites, and recording the same placement again changes nothing.
- All three `place*` methods throw `IllegalArgumentException` for a template that is not a site template.

**StructureLocator**
- `pieceAt` checks the worldgen tags first, then WorldSites records within 64 blocks.
- `anchor` checks the piece itself first, then:
  - for a worldgen piece, the other pieces of the same start;
  - for a runtime piece, other records of the same kind and owner within 64 blocks, nearest first.
- A search whose player leaves never calls back.
- An empty plan finishes on the next tick and uses no budget.

**Outposts.decide**
1. WORLDGEN if a worldgen outpost is within 640 blocks.
2. Otherwise REUSE if this army's nearest runtime outpost is within 512 blocks.
3. Otherwise CAP_NEAREST if runtime outposts are off or `runtimeCount >= maxRuntimeOutposts`.
4. Otherwise PLACE.

Distances are horizontal and inclusive; squared distances use long arithmetic. `runtimeCount` counts both armies' outposts.

**Tests**
- The inert test modifiers are registered once. They act only on entities with the tags `oldrepublic_test.*` and on the abilities `oldrepublic_test:cheap/dear/plain`.
- The 17.1 test `placeAtAndInjectedRings` is built as `surfaceSpotOnInjectedRingsAndFallback`, through `findSurface` plus `stamp`.
- The 16 ContractsM3 tests:
  - `attitudeFallbackEnemyHostileElseNeutral`
  - `damageModifiersClampQuarterToDouble`
  - `forceModifiersClampCostAndRegen`
  - `abilityLocksRefuseWhileHeld`
  - `holdsReleaseOnEveryPathRemovesGravityModifier`
  - `dangerTellTableMatches13_3`
  - `siteAnchorsRotated90`
  - `surfaceSpotOnInjectedRingsAndFallback`
  - `adaptationNeverFillsBesideBuiltBlocks`
  - `locatorQueueChecksFourCellsPerTick`
  - `locatorNotFoundRadius768FinishesWithinBudget`
  - `locatorDropsResultsBeyondRadius`
  - `worldSitesDataCap512AndNearest`
  - `worldSitesDataCodecRoundTrip`
  - `outpostDecideEveryDecision`
  - `blasterBoltCreateEveryStyle`
- Client test screenshots are numbered in bands: W1 10–19, W2 20–29, W3 30–39, W4 40–49, W5 50–59.
- **Spec deviations (sites):** `placeAt` throws `IllegalArgumentException` for a missing or non-site template and `IllegalStateException` for unloaded chunks or a failed stamp. `StructureLocator.anchor` uses the server captured on SERVER_STARTED (it takes no level) and returns null without a server. Sidecars load from `data/<any namespace>/site_anchors/**`. A runtime site made of several templates has one record (and siteKey) per template; `anchor()` also searches same-kind, same-owner records within 64 blocks. `placeAtAndInjectedRings` is tested as `surfaceSpotOnInjectedRingsAndFallback` through `findSurface` and `stamp`, because `placeAt` writes the persistent global `WorldSites`, which game tests must not touch. Every `place*` records itself in `WorldSites`, so `ReliquarySites` adds no record of its own.

#### Data, sounds, tools and docs

- `ModSounds.holder(sound)` returns `BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound)`. Every constant is registered, so this is the registry's `Holder.Reference`.
- ModSounds is generated by `gen_sounds.py --java` and now uses tab indentation like the rest of the codebase.
- events_m3.json fields are `id`, `feature`, `subtitle`, `loop`, `stream`, `volume`, and optionally `variants`. `feature` is the recipe module: world (W1); story (W2); force and holocron (W3); enemy and equipment (W4); boss and saber (W5).
- Streaming: sounds.json gets an explicit `"stream"` from events_m3.json. Events without it keep the old rule (a loop longer than 4 s streams).
  - Streamed (`stream: true`): the 5 ambience beds, `story.ones_that_sing` and the 3 music loops.
  - Not streamed: `force.lift.loop`, `holocron.hum`, `boss.collector.ward_hum`, `pylon.sing`, `saber.hum.singing`.
- Placeholder file naming: `<first id segment>/<rest with dots as underscores>`, numbered 1..n for variants (`boss.collector.hurl_lift` becomes `boss/collector_hurl_lift`; `site/discovered1..3`). A worker's real recipe keeps these names, so sounds.json and the subtitles do not change.
  - Any M3 id without a FILES entry gets the placeholder and is listed by `--placeholders`.
  - An M2 id without a FILES entry is still a hard error.
- check_m3_ids reads "already exists" as "existed in the pre-M3 tree" (commit fa82dc8, frozen in the script), because Phase 0 itself registers the new sound ids. Reused particles must be registered exactly once in the Java sources.
- `mineable/pickaxe`: Phase 0 adds the 12 decor blocks and `wreck_salvage`. 15.2 says Phase 0 writes every M3 entry in `data/minecraft/tags`, and the puzzle blocks are unbreakable in survival.
- `enchantable`: the 18 pieces are in the four slot tags by slot, and all 18 are in `armor`, `equippable` and `durability`. Vanilla `armor` already includes the slot tags; listing them again is harmless and matches 9.1 and 16 to the letter.
- `c:bosses` lives at `data/c/tags/entity_type/bosses.json` with `replace: false` (the Fabric convention path, verified in fabric-convention-tags-v2).
- Biome tags `has_structure/*` are empty, as 15.1 says; W1 fills them with the 3.2 biome lists. Structure tags each contain their one structure, as an optional entry.
- `force_reactive`, the cloth tags and the repair tags are created empty.
- `m3-checks` is a separate job from `build`, so id and ownership failures show without a Gradle run.

#### Probes

- **Armour (`m3_equipment.txt`):** `ArmorMaterial(int durability, Map<ArmorType,Integer> defense, int enchantability, Holder<SoundEvent> equipSound, float toughness, float knockbackResistance, TagKey<Item> repairIngredient, ResourceKey<EquipmentAsset> assetId)`. `Item.Properties#humanoidArmor(ArmorMaterial, ArmorType)` sets durability, attributes, enchantable, an `EQUIPPABLE` component with the asset, and repairable. Custom pieces use `Equippable.builder(slot).setAsset(...).setEquipSound(...)` and `EquipmentAssets.createId(String)`. Equipment asset JSON lives at `assets/<ns>/equipment/<id>.json` with `layers.humanoid`, `humanoid_leggings` and `humanoid_baby`; textures at `textures/entity/equipment/humanoid{,_leggings}/<id>.png`. Fabric `ArmorRenderer.register(...)` renders through `render(PoseStack, SubmitNodeCollector, ItemStack, HumanoidRenderState, EquipmentSlot, int light, HumanoidModel)`, so the robe renderer of 9.4 needs no fallback. `ServerEntityEvents.EQUIPMENT_CHANGE(LivingEntity, EquipmentSlot, ItemStack old, ItemStack new)` and `ItemTooltipCallback.EVENT` exist. Vanilla repair tags are named `repairs_*_armor`; `enchantable/armor` includes the four slot tags.
- **Boss (`m3_boss.txt`):** `ServerBossEvent(UUID, Component, BossBarColor, BossBarOverlay)` exists, so the vanilla boss bar works and 8.5's fallback is not needed. `PoiManager`, `PoiHelper`, `Musics` and `Holder.Reference` are in the dump. The `Options` body is cut at 300 lines before the key defaults, so H was not confirmed free; Force Sense keeps H, and the client key-clash test (11.1) is the check.
- **Not available:** `GlobalAttachments` is only an empty marker interface extending `AttachmentTarget`; `StructureUtils#getStructureTemplate` is not public.

#### Seed survey

Pending: it needs W1's structures, so the lead runs it right after W1 is merged.

### 15.2 Workers and ownership

Five workers. **A file has exactly one owner.** Shared files (lead) change only through requests in a worker's report.

| Worker | Owns (code) | Owns (data, assets, docs) | Delivers |
|---|---|---|---|
| **W1 world** | `com.oldrepublic.world.**` (including the `world.site` bodies the lead left to W1, `world.puzzle`, `world.outpost`), `client/world/**`, `mixin/world/**` and `oldrepublic-world.mixins.json` | worldgen JSON for the five structures, the structure sets `ruins` and `outposts`, template pools, processor lists and the `adapted_pool_element` type; POI types; the banner pattern; `tools/assets/gen_structures_m3.py` (ruins, tombs, wreck, outposts, shrines) and its sidecars; `gen_textures_world.py` additions; `sounds/world.py`; lang `world.json`; loot `chests/*` and block loot; damage types `tomb_miasma`, `urn_dust`; `docs/design/world.md` and `docs/assets/world.md` (M3 sections) | Structures and puzzles (3), outposts and their runtime fallback (3.5), `site_spawner`, Echo Shrines and cairns, `WorldSites` rules, `StructureLocator` lookups, presence reporting and ambience (3.7), the Sense provider for veiled stone, urns and Seeker's Sight chests, `/oldrepublic site ...` commands |
| **W2 story, missions, identity** | `com.oldrepublic.quest.**` except `QuestHooks` and `Guidance`, `com.oldrepublic.mission.**`, `com.oldrepublic.identity.**`, `com.oldrepublic.dialogue.**`, `com.oldrepublic.mentor.**`, `client/{quest,mission,identity,dialogue,mentor}/**` except `JournalTabs`, `mixin/{quest,identity}/**` and their configs | lang `quest.json`, `identity.json`, `mission.json`; dialogues (officers, mentors, collector, pathfinder, yields, confirmations); `data/oldrepublic/mission/*.json`; `tools/assets/gen_structures_story_m3.py` (new: Courier's Fall, mission sites; M2's `gen_structures_story.py` is not edited) and its sidecars; `gen_textures_quest.py` additions (commendations, record, book, officer portraits); `sounds/story.py`; loot `gameplay/{wayfarer_cache,frontier_contract,r1_supply}` | Chapter Three (6), the Republic faction and all kill reputation (5.1, 5.2), discovery state, XP and toast (3.7), story missions, contracts and cameos (5.4, 5.5), board, tracker and Missions tab (5.7), Guidance providers, every Collector reward including the rematch and pity (8.8, 8.9), rank 3 and the Pathfinder (5.8), the Standing panel's 4 rows |
| **W3 Force, skills, holocrons** | `com.oldrepublic.force.**` except the lead's registries, `com.oldrepublic.skill.**`, `com.oldrepublic.ability.**` except `AbilityLocks`, `com.oldrepublic.holocron.**`, `client/{force,skill,ability,hud,holocron}/**` except `Banners` and `DangerTells`, `mixin/{force,skill}/**` (main and client) and their configs | lang `force.json`, `skill.json`, `holocron.json`; `skill_tree/exploration.json` and the M3 node additions to `skill_tree/jedi.json` and `sith.json`; holocron dialogues; `gen_textures_force.py`, `gen_textures_skill.py` and new `gen_textures_holocron.py` (holocrons, shard, 12 gatekeeper portraits); `sounds/force.py`, `sounds/holocron.py` | Force Sense and Telekinetic Lift (10.1, 10.2), the Exploration tree and granted nodes (10.3, 10.4), reactive-block dispatch from Push, Pull and Lift (10.6), Force Heal reviving downed troopers through `Troopers.tend`, holocrons, gatekeepers, choices, return, carry and the Archive (4), the Holocrons tab, the Danger Sense client |
| **W4 troopers and armour** | `com.oldrepublic.enemy.**` except `enemy.construct`, `com.oldrepublic.equipment.**`, `client/{enemy,equipment}/**`, `mixin/{enemy,equipment}/**` and their configs | lang `enemy.json`, `equipment.json`; new `gen_textures_trooper.py` (trooper skins, importing from `gen_textures_equipment.py`), `gen_textures_enemy.py` additions (grenade, spawn eggs), new `gen_textures_equipment.py` (18 pieces, layers, robe models); `sounds/enemy.py`, `sounds/equipment.py`; loot `entities/{republic,sith}_trooper`; recipes; the `repairs/*` tag contents; damage type `frag_grenade` | Troopers, squads, cold peace, incidents, officers, downed troopers and escorts (7.1–7.5), the patrol spawner (7.6), the poacher changes and `Poachers.spawnCrew` (7.8), the four armour sets with traits, recipes, tooltips and rendering (9), barks |
| **W5 boss, constructs, saber** | `com.oldrepublic.boss.**`, `com.oldrepublic.enemy.construct.**`, `com.oldrepublic.saber.**`, `client/{boss,saber}/**`, `mixin/{saber,boss}/**` (main and client) and their configs | lang `boss.json`, `saber.json`; `tools/assets/gen_structures_reliquary.py` and its sidecars; `gen_textures_boss.py` (sentinel, Vasch and his portrait, pylon, debris), `gen_textures_saber.py` additions; `sounds/boss.py`, `sounds/saber.py` additions; loot `entities/ruin_sentinel`, `gameplay/echo_cache` (W2 rolls it); damage types `collector_surge`, `resonant_lash`, `archive_debris`, `archive_grip`, `repelling_palm` | Ruin Sentinels (7.7), the Reliquary and its placement, Orren Vasch and the fight including the rematch and Unchained fights (8; W2 grants their rewards), boss bar and music, hilt parts, the singing crystal's hum and saber changes (10.7), the parry FE of `watchers_insight`, `archivists_ward`, `knights_vigil`, `lords_dominion` |

**Shared and inherited files:**

| Files | Owner | Notes |
|---|---|---|
| `src/main/resources/oldrepublic-core.mixins.json`, `mixin/core/**` | lead | |
| `data/minecraft/tags/**` (`mineable/pickaxe`, `damage_type/is_projectile`, `is_explosion`, `item/enchantable/*`) | lead | Phase 0 writes every M3 entry; later changes by request |
| `data/oldrepublic/tags/block/{built_blocks,story_ground}.json`, `entity_type/push_resistant.json`, `c:bosses` | lead | |
| `data/oldrepublic/tags/item/repairs/*`, `jedi_robe_cloth`, `sith_robe_cloth` | lead creates, W4 fills | |
| `data/oldrepublic/tags/damage_type/force_harm.json` | W3 | the lead writes the M3 entries in Phase 0 |
| `data/oldrepublic/skill_tree/{jedi,sith}.json` | W3 | |
| `src/gametest/resources/**` (fixtures and sidecars) | lead | workers request fixtures |
| `src/gametest/java/.../m3/<Feature>ClientSteps.java` | the feature's worker | stubs by the lead in Phase 0 |
| Existing test classes | `WorldGameTests` W1; `QuestGameTests`, `StoryGameTests`, `IdentityGameTests` W2; `ForceGameTests`, `SkillGameTests` W3; `EnemyGameTests` W4; `SaberGameTests` W5; `ProgressionGameTests`, `ClientSmokeTest`, `PlaythroughTest`, `IdentityBuildsClientTest` lead | identity and enemy changed hands since M2 |
| `tools/assets/gen_structures.py`, `gen_textures_ui.py`, `gen_sounds.py`, `tools/check_*.py` | lead | |
| `tools/assets/gen_textures_identity.py`, `gen_textures_mentor.py` | W2 | |
| `client/hud/ClientCommandsFx.java` | W3 | the lead adds the four M3 toggles in Phase 0 |
| `com.oldrepublic.registry.M3Items`, `client/combat/**`, `combat/**` | lead | |
| Balance sections | the section's owner (14) | the keys of nodes W5 implements are in W5's `saber` section |

Notes on boundaries:

- **Reactive blocks:** W1 owns each block and its handler; W3 owns the dispatch from the powers. Neither edits the other.
- **Sense:** W3 owns the power and the reveal; W1 and W2 register providers; Danger Sense reads only the frozen tell table.
- **Troopers in other workers' content:** W1's outposts and `site_spawner` groups, W2's mission squads, downed troopers and escorts all go through `Troopers.*` and `Poachers.spawnCrew` (W4). Sentinels go through `Sentinels.*` (W5).
- **Holocron pedestals:** the block is W1's; `usePedestal` and everything after it is W3's. A pedestal never wakes sentinels (4.3); W1's urn keeper calls `Sentinels.wakeNear` on a wrong push.
- **Kill reputation:** W4 computes `wasHostile` from `Attitudes.hostileSnapshot` and fires `onTrooperKilled`; W2 applies every reputation change, the cap and `KILLED_SOLDIER`.
- **Boss ending and rewards:** W5 runs the fight, the yield, `BossArenas.resolve` and the scenes, and calls `onBossDefeated` once per participant. W2 owns the dialogues, the journal and every reward. The Archive choice is W3's.
- **Discovery:** W1 reports presence through `onSiteEntered`; W2 owns the first-time state, XP and toast.
- **`watchers_insight`:** two separate node checks, W5 (parry FE) and W3 (Heal cost).
- **Armour given by the story:** W2 calls `ArmourSets.piece`. Commendations are registered in Phase 0 and are W2's items; W4's recipes use them.
- **Stubs while parallel:** W2 needs `Troopers`, `Poachers`, `Sentinels`, `ReliquarySites`, `EchoShrines` and `HolocronService` (stubs with the frozen return values exist). W3 needs W1's reactive blocks (test with vanilla stand-ins through a game-test-only handler). W5 needs W3's Lift and Sense (the boss falls back to Push and Pull only) and places its own Reliquary with `/oldrepublic boss place`. Every worker tests their part with commands until integration.

### 15.3 Integration order (lead)

1. Phase 0 contracts merged and compiling, `ContractsM3GameTests` green, probes read and copied into the scratchpad `api/` folder, the seed survey logged, `WORKER_M3.md` written.
2. Workers deliver in parallel. Each worker pushes a branch made of Phase 0 plus their own files. CI builds that branch and runs the worker's own game tests before merge (the stubs make every branch compile on its own). `tools/check_ownership.py` (lead), driven by a machine-readable copy of 15.2 (`tools/ownership_m3.json`), fails a worker branch that touches files outside its globs.
3. Merge order W3 → W4 → W5 → W1 → W2: the powers and dispatch first, then the enemies and boss that react to them, then the world they live in, and the story last. Because Phase 0 registers every cross-worker item (`M3Items`) and tag, recipes, loot and rewards resolve at every intermediate merge.
4. Fix compile errors on CI and send them back.
5. Run the server and client tests. After W1 merges, re-run the M1/M2 playthrough portion and confirm it stays at 41/41 (new structures can change terrain along its route).
6. PlaythroughTest extension (17.3).
7. Update the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `VFX_SFX_SPEC`, `AUDIO_BRIEF`, `DECISIONS`), including any cuts from section 1.

---

## 16. Shared identifiers

All ids are in the `oldrepublic` namespace. "Existing, reused" ids come from M1 or M2: they are not registered again, get no new lang key and are not in `events_m3.json` (`tools/check_m3_ids.py` enforces this, 15.1).

### Items, blocks, entities, data

| Kind | Id | Owner |
|---|---|---|
| Block | `veiled_stone`, `counterweight_stone`, `step_stone`, `echo_urn`, `urn_keeper`, `pull_anchor`, `collapsed_rubble`, `puzzle_seal`, `holocron_pedestal`, `echo_cairn`, `tomb_vent`, `bounty_terminal`, `site_spawner`, `lumen_moss`, `funerary_urn`, `wreck_salvage` | W1 |
| Block (decor) | `ruin_stone`, `ruin_stone_carved`, `ruin_stone_cracked`, `ruin_stone_mossy`, `tomb_stone`, `tomb_stone_carved`, `tomb_stone_cracked`, `wreck_plating`, `wreck_plating_scorched`, `wreck_conduit`, `republic_panel`, `imperial_panel` | W1 |
| Block | `resonance_pylon`, `reliquary_heart`, `hoard_plinth`, `reliquary_seal` | W5 |
| Item | `lumen_thread`, `grave_silk`, `durasteel_plate` | registered by the lead (`M3Items`); W1 art, lang and drops |
| Item | `republic_commendation`, `imperial_commendation` | registered by the lead (`M3Items`); W2 |
| Item | `archive_record`, `fence_book` | W2 |
| Item | `jedi_holocron`, `sith_holocron`, `wayfarer_holocron` | W3 |
| Item | `holocron_shard` | registered by the lead (`M3Items`); W3 |
| Item | `republic_trooper_{helmet,chestplate,leggings,boots}`, `sith_trooper_{helmet,chestplate,leggings,boots}`, `jedi_{hood,robe,trousers,boots}`, `jedi_knight_robe`, `sith_{hood,robe,trousers,boots}`, `sith_lord_robe` (18 pieces) | W4 |
| Item (spawn eggs) | `republic_trooper_spawn_egg`, `sith_trooper_spawn_egg` (W4); `ruin_sentinel_spawn_egg` (W5) | as listed |
| Item (render-only) | `blaster_rifle`: **existing, reused** (`EnemyItems.BLASTER_RIFLE`, M1); `blaster_pistol`: existing (M2) | W4 |
| Item | `pommel_archivist`, `emitter_reliquary`, `singing_crystal` | registered by the lead (the hilt parts in `SaberItems`, `singing_crystal` in `M3Items`); W5 stats, looks and the hum |
| Enum constants | `HiltParts.Pommel.ARCHIVIST`, `HiltParts.Emitter.RELIQUARY`, `BladeColor.AMBER` | appended by the lead; W5 |
| Entity | `republic_trooper`, `sith_trooper`, `frag_grenade` | W4 |
| Entity | `ruin_sentinel`, `orren_vasch`, `pylon_focus`, `archive_debris` | W5 |
| Item component | `holocron` (`HolocronStamp`: id, owner, serial) | W3 |
| Item component | `equipment_traits` (`EquipmentTraits`) | W4 |
| Attachment | `story` (adds `Chapter3`), `missions`, `identity` (adds Republic and `kill_rep`) | W2 |
| Attachment | `holocrons` | W3 |
| Attachment | `held` (transient, synced to its owner) | lead (`Holds`) |
| Global attachment | `world_sites` | lead skeleton, W1 |
| Structure | `jedi_ruin`, `sith_tomb`, `wreck_field`, `republic_outpost`, `imperial_outpost` | W1 |
| Structure set | `ruins` (`jedi_ruin`, `sith_tomb`, `wreck_field`), `outposts` (`republic_outpost`, `imperial_outpost`) | W1 |
| Template pool | `<structure>/start`, `<structure>/underground`, `<structure>/side`, `<structure>/path` | W1 |
| Processor list | `jedi_ruin_weathering`, `sith_tomb_weathering`, `wreck_weathering` | W1 |
| Pool element type | `adapted_pool_element` | W1 |
| Structure template | the 15.1 table | as listed |
| Site anchor sidecar | `data/oldrepublic/site_anchors/<template>.json`, one per template | the template's owner |
| POI type | `veiled_stone`, `echo_urn`, `urn_keeper`, `echo_cairn`, `holocron_pedestal`, `counterweight_stone`, `pull_anchor`, `bounty_terminal` | W1 |
| Banner pattern | `republic_chevron` | W1 |
| Portrait (GUI sprite `portrait/<id>`) | `officer_republic`, `officer_imperial` (W2); `gatekeeper_<id>` for the 12 world holocrons (W3); `orren_vasch` (W5; also used by W2's Collector dialogues and W3's Archive holocrons). Yield dialogues have no portrait, like M2's `yield.json`. | as listed |
| Dialogue | `officer_republic`, `officer_imperial`, `c3_collector_meet`, `c3_collector_yield`, `c3_collector_stance`, `c3_send_word`, `pathfinder`, `yield_intercept`, `yield_border`, `r1_leave_confirm`, `i1_burn_confirm`; `mentor_jedi`, `mentor_sith` (extended) | W2 |
| Dialogue | `holocron_<id>` (15), `archive_hoard` | W3 |
| Dialogue | `reliquary_heart` | W5 |
| Skill tree | `exploration` (new); `jedi`, `sith` (node additions) | W3 |
| Ability ids | `force_sense`, `force_lift` | W3 |
| Damage type | `tomb_miasma`, `urn_dust` (W1); `frag_grenade` (W4); `collector_surge`, `resonant_lash`, `archive_debris`, `archive_grip`, `repelling_palm` (W5) | as listed |
| Damage type tag additions | `minecraft:is_explosion` += `frag_grenade`; `minecraft:is_projectile` += `archive_debris`; `oldrepublic:force_harm` += `collector_surge`, `resonant_lash`, `archive_grip`, `repelling_palm` | lead writes in Phase 0 (15.2) |
| Loot table | `chests/jedi_ruin`, `chests/sith_tomb`, `chests/wreck_field`, `chests/force_cache`, block loot | W1 |
| Loot table | `gameplay/wayfarer_cache`, `gameplay/frontier_contract`, `gameplay/r1_supply` | W2 |
| Loot table | `entities/republic_trooper`, `entities/sith_trooper` | W4 |
| Loot table | `entities/ruin_sentinel`, `gameplay/echo_cache` | W5 |
| Particle types | the 13.2 table; `crystal_glint` and `force_mote` are **existing, reused** | as listed |
| Block tag | `force_reactive` (lead creates, W1 fills); `built_blocks` M3 additions (lead) | as listed |
| Item tag | `jedi_robe_cloth`, `sith_robe_cloth`, `repairs/trooper_armour`, `repairs/jedi_robes`, `repairs/sith_robes` | lead creates, W4 fills |
| Item tag additions | `minecraft:enchantable/{head_armor,chest_armor,leg_armor,foot_armor,armor,equippable,durability}` += the 18 pieces | lead |
| Entity type tag | `#push_resistant` += `ruin_sentinel`, `orren_vasch`; `c:bosses` += `orren_vasch` | lead |
| Structure tag | `jedi_ruins`, `sith_tombs`, `wreck_fields`, `republic_outposts`, `imperial_outposts` | lead (W1 fills) |
| Resonance milestone ids | `holocron_<id>`, `holocrons_3`, `holocrons_9`, `holocrons_12`, `archive_unmade` | W3 grants |
| Resonance milestone ids | `vasch_records`, `collector`, `chapter_three`, `second_rank` | W2 grants |
| Choice ids | `holocron.<id>.seal|listen|break`, `archive.return|keep|unmake`, `title.keeper_of_echoes` | W3 (recorded through `Identity.recordChoice`) |
| Choice ids | `mission.<mission>.<choice>` (for example `mission.r2.return`, `mission.i2.handed_over`), `collector.spare|execute|bind|release|bring_in`, `rep:republic:seed`, `title.pathfinder` | W2 |
| Entity NBT keys | `Role`, `Squad`, `Incident`, `Provoked`, `Engaged`, `BleedAt`, `Terminal`, `Variant`, `Bound` | frozen (15.1) |

### Payloads

| Id | Direction | Owner |
|---|---|---|
| `ability_input` (existing; slot 8 = Sense) | C2S | W3 |
| `sense_reveal` (points: kind, pos, entity id, attitude; duration) | S2C | W3 |
| `site_presence` (site kind ordinal or -1; template id) | S2C | W1 |
| `mission_board` (terminal, issuer, 2 offers, full-pay count, active id) | S2C | W2 |
| `mission_action` (terminal, ACCEPT / ABANDON, offer index) | C2S | W2 |
| `tracker_hint` (yaw to target, band FAR / NEAR / HERE / NONE, counter) | S2C | W2 |
| `boss_bar` (boss id, progress, phase, visible); **only** if the `ServerBossEvent` probe fails | S2C | W5 |

The held state travels through the synced `held` attachment, not a payload. Board hints come from the journal-hold tick (5.7), so there is no hint-request payload.

### Effect events

The full look-and-sound table is 13.3. By owner:

| Owner | Ids |
|---|---|
| W1 | `veil_reveal`, `veil_open`, `counterweight`, `step_stone`, `urn_echo`, `urn_wrong`, `anchor`, `rubble_crumble`, `seal_open`, `puzzle_solved`, `pedestal_ready`, `tomb_vent`, `terminal_use` |
| W2 | `site_discovered`; `calling` variants 4–8; `awakening_dream` variant 3; `lesson_complete` variants 4–7; `cairn_touch`, `rank_ceremony`, `mission_update` |
| W3 | `force_sense`, `force_lift`, `force_lift_target`, `holocron_attune`, `holocron_gatekeeper`, `holocron_choice`, `holocron_echo`, `archive_choice` |
| W4 | `trooper_charge`, `trooper_vent`, `kolto_heal`, `trooper_tend`, `trooper_rally`, `flare_call`, `grenade_tell`, `grenade_fuse`, `grenade_burst`, `grenade_return`, `trooper_bark`, `trooper_salute`, `armour_set`; extends `blaster_fire` (variant = bolt style) |
| W5 | `sentinel_wake`, `sentinel_slam`, `sentinel_core`, `sentinel_lance`, `sentinel_death`, `boss_hurl`, `boss_palm`, `boss_lash`, `boss_surge`, `boss_spent`, `boss_grip`, `boss_lunge`, `boss_ward`, `boss_phase`, `boss_yield`, `boss_leash`, `boss_echo`, `collector_resolve`, `pylon_state`, `npc_deflect`, `hoard_open` |

### Sounds

| Group | Ids | Recipe owner |
|---|---|---|
| world | `ambient.jedi_ruin`, `ambient.ruin_chime`, `ambient.sith_tomb`, `ambient.tomb_whisper`, `ambient.wreck`, `ambient.wreck_creak`, `ambient.outpost`, `puzzle.veil_reveal`, `puzzle.veil_crumble`, `puzzle.counterweight_sink`, `puzzle.counterweight_warn`, `puzzle.counterweight_rise`, `puzzle.step_extend`, `puzzle.step_retract`, `puzzle.urn_echo`, `puzzle.urn_wrong`, `puzzle.anchor_strain`, `puzzle.anchor_release`, `puzzle.rubble_crumble`, `puzzle.seal_open`, `puzzle.solved`, `puzzle.pedestal_ready`, `vent.hiss`, `vent.puff`, `terminal.use` | W1 (`sounds/world.py`) |
| story | `site.discovered`, `story.ones_that_sing`, `story.calling_holocron`, `story.calling_reliquary`, `story.calling_fall`, `story.collector_fallen`, `story.rank_three`, `story.cairn_touch`, `story.blade_touch`, `story.tablet_break`, `mission.accept`, `mission.update`, `mission.complete`. **Existing, reused:** `story.lesson_complete` (M2; the Chapter Three report banner). | W2 (`sounds/story.py`) |
| force | `force.sense.cast`, `force.sense.reveal`, `force.sense.danger`, `force.lift.start`, `force.lift.loop`, `force.lift.slam`, `force.lift.hurl` | W3 (`sounds/force.py`) |
| holocron | `holocron.rise`, `holocron.open`, `holocron.gatekeeper_form`, `holocron.gatekeeper_fade`, `holocron.hum`, `holocron.seal`, `holocron.break`, `holocron.listen`, `holocron.echo`, `holocron.archive_return`, `holocron.archive_keep`, `holocron.unmake` | W3 (`sounds/holocron.py`) |
| enemy | `trooper.republic.charge`, `trooper.republic.fire`, `trooper.imperial.charge`, `trooper.imperial.fire`, `trooper.vent`, `trooper.kolto`, `trooper.tend`, `trooper.stand`, `trooper.rally`, `trooper.flare`, `trooper.radio`, `trooper.hurt`, `trooper.death`, `grenade.arm`, `grenade.fuse`, `grenade.burst` | W4 (`sounds/enemy.py`) |
| equipment | `armour.equip_trooper`, `armour.equip_robe`, `armour.set_bonus` | W4 (`sounds/equipment.py`) |
| boss | `sentinel.wake`, `sentinel.slam_tell`, `sentinel.slam`, `sentinel.core_exposed`, `sentinel.lance_charge`, `sentinel.lance_fire`, `sentinel.hurt`, `sentinel.death`, `boss.collector.hurt`, `boss.collector.hurl_lift`, `boss.collector.hurl_throw`, `boss.collector.hurl_rip`, `boss.collector.hurl_return`, `boss.collector.palm_tell`, `boss.collector.lash_tell`, `boss.collector.lash_wave`, `boss.collector.surge_gather`, `boss.collector.surge_release`, `boss.collector.surge_clash`, `boss.collector.spent`, `boss.collector.grip`, `boss.collector.grip_hold`, `boss.collector.grip_break`, `boss.collector.lunge`, `boss.collector.ward_hum`, `boss.collector.unsung`, `boss.collector.transition`, `boss.collector.yield`, `boss.collector.leash`, `boss.collector.echo_form`, `boss.collector.echo_fade`, `boss.collector.binders`, `boss.collector.execute`, `boss.collector.walk_out`, `pylon.sing`, `pylon.crack`, `pylon.silence`, `pylon.regrow`, `pylon.shatter`, `music.collector.archivist`, `music.collector.singing_wall`, `music.collector.unkept`, `ambient.reliquary`, `hoard.open` | W5 (`sounds/boss.py`) |
| saber | `saber.hum.singing` | W5 (`sounds/saber.py`) |

The Palm release and clash reuse the existing M2 acolyte sounds, the NPC deflection reuses the M1 deflect sound, and poachers keep the M1 blaster sounds.

**Loops and their stop rules** (every loop also stops on death, logout, dimension change and `/kill`):

| Loop | Stops when |
|---|---|
| `ambient.jedi_ruin`, `ambient.sith_tomb`, `ambient.wreck`, `ambient.outpost`, `ambient.reliquary` (site beds, played by `WorldAmbience`) | the player leaves the site (`site_presence` changes) |
| `story.ones_that_sing` | the dream ends or is interrupted |
| `holocron.hum` | the gatekeeper folds, or the dialogue closes |
| `force.lift.loop` | the channel ends on any cancel path |
| `boss.collector.ward_hum`, `pylon.sing` | the ward drops, the pylon is silenced or shatters, the fight resets or ends, or the player leaves the hall |
| `music.collector.archivist`, `music.collector.singing_wall`, `music.collector.unkept` (streamed, `SoundSource.MUSIC`) | the next phase's loop crossfades in, `boss_phase` variant 6, yield, victory, reset, or leaving the hall |
| `saber.hum.singing` | the saber's own hum stops (retract, slot change, throw catch rules of M1) |

### Lang prefixes

Duplicate keys across parts fail the build: Phase 0 makes `mergeLang` throw on any duplicate key (15.1). The lead's Phase 0 lines for `M3Items` go into the owners' parts below.

| Part | Prefixes |
|---|---|
| `world.json` (W1) | Existing, plus `block.oldrepublic.<W1 blocks>`, `item.oldrepublic.{lumen_thread,grave_silk,durasteel_plate}`, `structure.oldrepublic.*`, `site.oldrepublic.*`, `message.oldrepublic.world.*`, `death.attack.oldrepublic.{tomb_miasma,urn_dust}*` |
| `quest.json` (W2) | Existing, plus `journal.oldrepublic.entry.<6.7 ids>`, `journal.oldrepublic.codex.<6.7 ids>` except `holocron_<id>`, `quest.oldrepublic.c3.*`, `dialogue.oldrepublic.{officer_*,c3_*,pathfinder,yield_intercept,yield_border,r1_leave_confirm,i1_burn_confirm}.*`, `npc.oldrepublic.*` (`officer.<army>.<0-5>`, Rell Okkar, Dovan Lisk, Aldo Brannick), `message.oldrepublic.story.*` |
| `mission.json` (W2, new) | `mission.oldrepublic.*`, `screen.oldrepublic.board.*`, `screen.oldrepublic.missions.*`, `item.oldrepublic.{republic_commendation,imperial_commendation,archive_record,fence_book}`, `message.oldrepublic.mission.*` |
| `identity.json` (W2) | Existing, plus `faction.oldrepublic.republic`, `title.oldrepublic.*`, `rank.oldrepublic.*` (rank 3) |
| `force.json` / `skill.json` (W3) | `ability.oldrepublic.{force_sense,force_lift}.*`, `message.oldrepublic.force.*`, `skill.oldrepublic.tree.exploration`, `skill.oldrepublic.node.<new ids>.*`, `key.oldrepublic.force_sense` |
| `holocron.json` (W3, new) | `holocron.oldrepublic.*`, `item.oldrepublic.{jedi,sith,wayfarer}_holocron`, `item.oldrepublic.holocron_shard`, `dialogue.oldrepublic.{holocron_*,archive_hoard}.*`, `journal.oldrepublic.codex.holocron_*`, `screen.oldrepublic.holocrons.*` |
| `enemy.json` (W4) | Existing (it already holds `item.oldrepublic.blaster_rifle`, which is not added again), plus `entity.oldrepublic.{republic_trooper,sith_trooper,frag_grenade}`, `item.oldrepublic.{republic,sith}_trooper_spawn_egg`, `trooper.oldrepublic.*` (roles, barks), `death.attack.oldrepublic.frag_grenade*` |
| `equipment.json` (W4, new) | `item.oldrepublic.<18 armour ids>`, `equipment.oldrepublic.*` (set names, traits, tooltips) |
| `boss.json` (W5, new) | `entity.oldrepublic.{ruin_sentinel,orren_vasch,pylon_focus,archive_debris}`, `item.oldrepublic.ruin_sentinel_spawn_egg`, `block.oldrepublic.<W5 blocks>`, `boss.oldrepublic.*` (bar, phase names, lines), `dialogue.oldrepublic.reliquary_heart.*`, `death.attack.oldrepublic.{collector_surge,resonant_lash,archive_debris,archive_grip,repelling_palm}*` |
| `saber.json` (W5) | Existing, plus `item.oldrepublic.{pommel_archivist,emitter_reliquary,singing_crystal}`, `saber.oldrepublic.part.*`, `color.oldrepublic.blade.amber` |
| `sounds.json` (lead) | `subtitles.oldrepublic.*` (generated) |

---

## 17. Test plan

Distinguish compile, automated checks and manual play, as in M1 and M2. Nothing counts as "played" until a person plays it. Every game test follows the test rules of 15.1: an explicit `maxTicks`, small fixtures, listed mock players for proximity, explicit days, no global state, and whole templates and worldgen only in client tests.

### 17.1 Server game tests (`./gradlew build`; each worker writes their own class, the lead registers it)

Each row lists the named test methods; each name states its assertion. Tests of long timers drive the pure `*Rules.step` functions.

| Class | Owner | Tests |
|---|---|---|
| `ContractsM3GameTests` (green before workers start) | lead | `allegianceFriendlySameNotNone`; `attitudeFallbackEnemyHostileElseNeutral`; `attitudeFirstNonNullRuleWins`; `provokedAloneNeverCountsAsHostile`; `shieldedFromAreaSkipsNeutralAndAllied`; `damageModifiersClampQuarterToDouble`; `forceModifiersClampCostAndRegen`; `refundCapsPerKillAndParry`; `forceBlockRegisterDuplicateThrows`; `findOnAimReturnsFirstReactiveBlock`; `guidanceTiesByRegistrationOrder`; `senseTargetsCapAt32`; `siteAnchorsRotated90` (`anchor_fixture`); `placeAtAndInjectedRings` (fixture); `adaptationNeverFillsBesideBuiltBlocks`; `locatorQueueChecksFourCellsPerTick` and `locatorNotFoundRadius768FinishesWithinBudget` (fake lookup); `locatorDropsResultsBeyondRadius`; `worldSitesDataCap512AndNearest`; `oldInterruptReachesHandlersAsUnknown`; `controlKindsReachDurationModifiers` (every switched call site); `boltParryFiresParryOnce`; `powerHitOnlyForLivingTargets`; `alliedOrProvokedKillPaysNoXp` and `hostileKillPaysXp` (test rule); `pushConeAndLightningChainSkipShielded`; `healMasteryNotGrantedBesideAllied`; `holdsReleaseOnEveryPathRemovesGravityModifier`; `abilityLocksRefuseWhileHeld`; `dangerTellTableMatches13_3`; `promoteRefusesIndependent` |
| `WorldM3GameTests` | W1 | `veilFloodFillStopsAt27`; `veilRevealThenOpenByPush`; `puzzleResetAfterIdleAge` (rules); `counterweightPowersStepStonesBackFaceOnly` (fixture); `stepStoneCollisionOnlyWhenPowered`; `urnOrderSeededByPosition` and `urnKeeperFoundThroughPoi` (`urn_fixture`); `wrongUrnDustAndWakeSkipsOwnSide`; `pullAnchorStrainThenRelease`; `rubbleClearsOnlyByLift`; `sealLatchesAndNeverClosesOnOccupant` (`seal_fixture`); `ventTellThenPuffDamageAndSlowKind` (rules); `puzzleBlocksUnbreakableInSurvival`; `pedestalReadyLatches`; `everyFrozenTemplateHasSidecarAnchorsInsideSize` (loads, never places); `outpostDecideMatrix` (WORLDGEN, REUSE, PLACE, CAP_NEAREST on a detached `Data`); `worldSitesCodecRoundTrip`; `terminalWaitsForLoadedEntities`, `terminalRespawnsAfterTwoFailedChecks`, `staleOfficerDiscardsItself` (`terminal_fixture`); `siteSpawnerSpawnsOnceWithFreshSquadEach` (two spawners, two squad ids); `blockEntityNbtRoundTrips` (terminal, urn keeper, cairn owner, pedestal `fixed_id`, spawner); `cairnRemovesItselfWhenOwnerExpired`; `echoShrinePlacementWithTestRings` |
| `StoryM3GameTests` | W2 | `stagesOnlyMoveForward`; `oldSaveAtCompleteEntersWaiting`; `chapter3CodecRoundTripAndCopyOnDeath`; `saveWithoutChapter3Loads`; `leadsCountOnceAndBeforeLeads`; `levelGateKeepsCallingOnAChamberOrBoard`; `firstEchoFallsBackToShrine` (stub locator returns null); `firstEchoAlreadyMetAdvancesAtOnce`; `extraShrineOncePerKind`; `lateJoinRefusedFromSummons`; `peacefulResolvesCombatObjectivesOnArrival`; `coopHelperJumpsToAftermathWithOwnStance` (two listed players); `rank3MatrixAndFailReasons` (including the confrontation as the trial); `pathfinderNeedsTrustedAndThreeHolocrons`; `hostileKillCostsTenAndPaysXp`; `nonHostileKillCostsFortyAndKilledSoldier`; `provokeThenKillCountsAsNotHostile`; `waryEngagedKillIsSelfDefence` (-10, no shift); `dailyCapCoversOnlyTenLosses` (kills past the cap still cost -40); `killsNeverGainReputation`; `independentReportAtWary`; `sendWordWhenBothArmiesHostile`; `collectorRewardsOncePerFlag` (first clear, rematch once per day, pity on the 4th clear); `otherHelperRewardOncePerDay`; `cameoFlagsChooseR2Chief` |
| `MissionGameTests` | W2 | `offerSeedingStable`; `usedOffersNotOfferedAgainThatDay` (accept, complete, abandon); `fullPayThreePerDayThenQuarterXpOnly`; `contractXpFormulaCapped900`; `ownSideCountingPerPath`; `contractLeadOnce`; `eachObjectiveCompletesOnItsTrigger`; `storyChoicesApplyOnce`; `jediOrderShareOnRepublicGains`; `yieldDialoguesMapToNewOutcomes` (`INTERROGATED`, `HANDED_OVER`); `handOverEscortsDespawn`; `missionDataCodecRoundTrip`; `trackerBandThresholds`; `guidancePriorities`; `republicSeedAtSummons`; `commendationsOf` |
| `HolocronGameTests`, `ForceGameTests` (extended) | W3 | `pedestalOrderPerKind`; `samePedestalTwiceGivesOneHolocron`; `couriersFallGivesOnlyCompass`; `pedestalRefusesWithHostileNearAndNeverWakesSentinels`; `closedDialogueOrDamageAttunesNothing`; `sealListenBreakMatrix` (shifts, dormant nodes, Break destroys the item but counts); `restoreBumpsSerialAndStaleCopyIsSilent` (the stash duplicate); `returnThenRestoreRefused`; `breakThenRestoreRefused`; `restoreOncePerDay`; `returnOncePerIdInOrder`; `carryBonusDistinctCurrentSerialMainInventoryCap3`; `spDripAndMilestones3_6_9_12`; `echoOncePerPedestalListCap64`; `shardExchangeAndAmends`; `archiveChoiceOnceStoredOnlyInHolocronData`; `archivePendingForHelpers`; `holocronDataAndStampCodecsAndCopyOnDeath`; `senseCostCooldownCapSlot8`; `senseProvidersCalledNoBlockScan`; `senseColdCastSendsRevealWithoutDirection` and `senseFollowUpPulseWithin40Ticks` (fake locator); `liftHeightDurationResistantHeight`; `liftHeightZeroRefuses`; `liftHoldUsesHoldKind`; `dispatchOrderPushPullLift` (entity, living, block, items); `lightningAndThrowReachReactiveEntities`; `healRevivesDownedThroughTend` (stub); `grantedNodeEffects` (`watchers_insight` Heal cost, `mending_breath`, `tomb_hunger`, `crackling_grasp`, `archivists_hunger`, `trailwise`) |
| `TrooperGameTests`, `EquipmentGameTests`, `EnemyGameTests` (extended) | W4 | `attitudeTableEveryTier`; `oneSquadAlliedToAWaryToB` (two listed players); `waryWarnsThenEngagesAfter120Ticks`; `neutralHitProvokesSquad`; `incidentModes` (ARMY, ARMY_AND_OWNER); `allyDefenceStartsIncident`; `peacefulNeverAttacksPlayers`; `neverAttackSettlers`; `coldPeaceShouldEngageAndStandoffStep` (pure); `provocationAndIncidentExpiry` (explicit game time); `trooperNbtRoundTrip` (`Role`, `Squad`, `Incident`, `Provoked`, `Engaged`, `BleedAt`, `Terminal`); `structureSpawnKeepsRole`; `downedNotTargetableAndBleedsOutOnLoad`; `dropsNeverArmourOrCommendations`; `boltStyleSyncedAndAllyPass`; `imperialBoltIsNormal`; `grenadePushTimes1_2AndPullBack`; `medicChannelInterruptedByHit4`; `rallyAndReinforcementCancelWindows`; `barkRateLimits`; `patrolCapsAndSiteClearance` and `patrolDespawnsAsWholeSquad` (a detached patrol state); `notRemovedWhenFarAway`; `officersSurvivePeacefulTick`; `troopersNeverBlockSleep`; `perSlotModifierIdsStack` (4 trooper pieces give 4 speed modifiers; removing one leaves 3); `perPieceAndSetTraits`; `workedExample9_1`; `ironDisciplineShortensEveryKind`; `calmAndHungerIncludingBoltParries`; `recipesFitShapesAndCostCommendations`; `repairTagsResolve`; `equipmentTraitsCodecRoundTrip` |
| `BossGameTests`, `SentinelGameTests`, `SaberGameTests` (extended) | W5 | Fight tests use the lead's `arena_24x7x24` fixture with pylon stand-ins, never the full hall. `heartStateMachine` (rules); `heartNbtRoundTripYieldedResolvedAttempts`; `resetAfter200TicksMovesItemsToAntechamber`; `healthScalesWithTwoParticipants` and `midPhaseOneJoinKeepsFraction`; `nonParticipantDamageRefused`; `leashReturnsToDais`; `phaseThresholds`; `interruptsOnlyFrom8_4Causes` (`unknown` ignored); `pylonSilencedByEverySource` (including the Lift channel and a parried lance); `resingAtMostOncePerPhaseWhileAnotherSings`; `unsungDoublesDamageWindow`; `surgeCoverByServerRays`; `gripUsesHoldsAndBreaksOn8OrPush`; `liftOnlyWhileSpent`; `peacefulRefusesFight`; `resolveCallsHookWithOutcome`; `rematchHookOncePerDayWithEcho`; `unchainedValues`; `reliquaryEvaluateRejects` (worldgen piece through the injected predicate, built blocks, unloaded chunks); `sentinelVariantsAndNbt`; `sentinelDormantForOwnSideAt15`; `wakeNearNeverWakesOwnSide`; `boundAlwaysHostile`; `lanceReturnedOnlyByParry`; `parriedLanceAimsAtCrosshairInArena`; `heavyBoltAndDebrisAgainstGuardAndParry`; `parryWindowOrderAndCap11`; `hiltPartStats`; `amberCrystalHumLayer`; `acolyteDeflects`; `duelistProfileCompatibilityConstructor`; `knightsVigilAndLordsDominion` |

### 17.2 Client game tests (screenshots reviewed)

- `ExplorationClientTest` (lead) calls each worker's `m3/<Feature>ClientSteps.run(context, server)`.
- **Worldgen step** (lead driver, W1 assertions). A seeded, non-superflat world (`setUseConsistentSettings(false)`, as `PlaythroughTest` does; consistent client worlds have structures off) runs `/oldrepublic site place <structure> <pos>` (the vanilla `/place structure` path) for each of the five structures. On the server it asserts, through `structureManager().getStructureWithPieceAt`, that every expected template id is present (including the mandatory pilgrim's niche in each ruin and tomb), that every `PEDESTAL_*` sidecar cell holds a pedestal and no anchor cell holds stray terrain, and that a processional vent reaches TELL within 1,200 ticks with the player beside it. W1's screenshots come from this world.
- **`ExplorationPersistenceTest`** (lead): create a world; write a `WorldSites` record, an active mission, attuned holocrons with serials and a YIELDED heart; close it; reopen the save (`TestWorldSave`, the Fabric `PersistenceGametest` pattern); assert all of them.
- **Dedicated server** (`ClientSmokeTest`, lead): on `context.worldBuilder().createServer()` with default properties (`allow-flight=false`), join, cast Sense with H, grant and cast Lift by command, then hold the test player for 80 ticks with `/oldrepublic hold` and assert they are still connected and the hold cleared. Client-class isolation is not a client-test check (the in-process server shares the client's JVM); `splitEnvironmentSourceSets` and the server game-test job in `./gradlew build` cover it.
- A check fails the test if two mod keys share a default binding, now including H.
- Screenshots are named `oldrepublic_m3_<nn>_<what>`, numbered by band: W1 10–19, W2 20–29, W3 30–39, W4 40–49, W5 50–59.

| Worker | Screenshots |
|---|---|
| W1 | (from the worldgen world) Watchers' Rest courtyard; Listening Hall with ledges extended; Sith Tomb gate and processional vent tell; burial chamber urns; wreck hull; Republic and Imperial outposts; an Echo Shrine; veiled stone before and after Sense |
| W2 | Chapter Three journal page with the lead list; board screen; Missions tab; tracker in each band; officer dialogue at Wary with "Make amends"; Standing tab with 4 rows; rank 3 banner; the Pathfinder at Courier's Fall |
| W3 | Sense reveal (dust streams and mote clusters, no outline); Danger Sense edge tick; Lift on a zombie and on rubble; gatekeeper projecting from the seams; Holocrons tab; Exploration tree tab |
| W4 | Each trooper role at 8 blocks; a squad firing (style colours); a downed trooper; grenade tell and fuse; flare; the 4 armour sets and the two rank 3 robes on a player, plain and with robe models; set tooltip |
| W5 | Sentinel wake and slam tell; Reliquary from the threshold; each boss phase; Unsung; surge gather with cover; pylon silenced; yield; a custody scene; hoard open; the fallback boss bar if used |

### 17.3 PlaythroughTest extension (lead)

The playthrough world uses a fixed seed with natural generation. The Phase 0 seed survey (15.1) says which sites are near the route.

After Chapter Two, the same world continues with real inputs where possible:

1. Wait out WAITING and sleep for the "Ones That Sing" dream.
2. Jedi: talk to Saelen, accept the lesson, cast Sense with H and touch the 3 cairns.
3. **First echo.** If the survey found a Watchers' Rest within 768 blocks, follow the Calling there (a teleport is logged if over 128 blocks). Otherwise place one with `/oldrepublic site place jedi_ruin <pos>` at a forest spot from the survey (logged). Solve the stair and the Listening Hall with real Sense and Push presses, and attune `jedi_morrane`. The Echo Shrine fallback is covered separately (`StoryM3GameTests` and a W1 screenshot).
4. Take R1 at the Republic outpost (placed at runtime if the survey found none, logged), fight the sentinels, tend the wounded. Take R2.
5. Reach 4 leads and level 9 (the XP top-up is logged), enter the Reliquary and fight Vasch with these tactics:
   - phase 1: heavy attacks into his guard; Pull during the Hurl tell;
   - phase 2: Pull P1 and P2 from the dais, Force Jump to P3 and P4, then attack while Unsung;
   - phase 3: break line of sight behind a column for each Surge; Push to break the Grip.

   Each phase has a 2,400-tick timeout. On a timeout the bot logs it and uses `/oldrepublic boss phase <player> <n>` so the yield and hoard steps still run; such a run reports PASS WITH SHORTCUT, not PASS.
6. Yield and **Spare**; choose **Return** at the hoard and report to Saelen. Assert COMPLETE, Republic Trusted or better, `force_sense` owned, 2 holocrons.
7. Rank 3 by command shortcuts (logged), then the Knighting through the dialogue.

M2 has no Sith or Independent bot run (`KNOWN_ISSUES.md`). The second, shorter **Sith run** therefore starts from a logged `/oldrepublic story` shortcut to Chapter Two COMPLETE on the Sith path, places a Sith Tomb if needed, solves the urns with Sense and Push, takes I1 and executes Vasch. The third, **Independent** run (Courier's Fall, `wayfarers_compass`, Lift on `collapsed_rubble`, the Pathfinder title) starts the same way and is item 7 on the cut list.

Every shortcut is logged, as in M1 and M2.

### 17.4 Manual checks (owner or a tester)

- Whether Sense reads as "the Force", not a scanner, at all four presets.
- Trooper fights at Wary and Hostile: do the barks and tells give enough warning? Does self-defence after a warning feel fair?
- The full boss fight solo at level 10, and with 2 players. Target: phase 2 solo in under 120 s at level 10 without Pull (saber, Push and Force Jump only), and the whole fight in 6 to 10 minutes.
- The Independent run with Lift in the boss fight: does Lift feel like a real answer to the pylons and the Hurl?
- Puzzle readability with sound off (subtitles only).
- Armour looks in first and third person, with and without robe models.
- Audio loops stopping on death, logout and dimension change during the boss fight.
- A dedicated server with two players at different Chapter Three stages, and sleeping beside an Allied garrison.
- A pass in Peaceful: officers stay, missions resolve, the Reliquary talks instead of fighting.

---

## 18. Lore notes for `DECISIONS.md` (lead adds these)

- **The cold peace, a few years after the Treaty of Coruscant (about 3,650 to 3,645 BBY).** The Republic and the Empire are not at war. Their troopers patrol a treaty line across this frontier and do not fire first. This is why both armies can share a region with the player and why an "incident" is a story event, not a battle. The M2 note "about 3,640 BBY" may fall after open war resumed in most timelines, so the lead corrects it in `DECISIONS.md` and checks the range against the canon wiki. No in-game text shows the date.
- **The Watch Archive and the Archivists.** The Watch enclave on this frontier and its Archivists are original. They fit the canon idea of Jedi keeping records and holocrons, without naming any canon place or order. The Order mints no money: Vasch pays in Republic credit chits stamped with the Archive's vault seal (relief funds the Watch held).
- **Holocrons and gatekeepers** are canon concepts: a holocron holds a recorded teacher, the gatekeeper, who answers the worthy, projected from the device. Jedi and Sith holocron shapes (cube and pyramid) follow canon convention, but every model and texture is original. **Wayfarer holocrons** (brass spheres of no Order) are original to this mod.
- **Original characters:** Orren Vasch; the gatekeepers Tessaly Arn, Pell of the Long Road, Osk Brevan, Maelis Dunn, Aveth Morrane, Ione Sarrow, Ruan Ketterin, Sela Vanterre, Hadrek Zarvos, Ysolde the Unbowed, Calix Thrane and Ilvane Torr; the twelve outpost officers (3.5); Rell Okkar, Dovan Lisk and Aldo Brannick. The Sith founder was renamed from "Veskar Mor" so no two key names (Morrane, Vasch) are confusable in subtitles. The lead checks every name against the canon wikis before release and renames any clash.
- **Trooper looks** are original: Republic off-white plate with navy trim, Imperial charcoal plate with crimson trim. No helmet copies a film or game design.
- **Knight and Lord.** The Knighting and the Naming are adaptations for an adult player, as with the M2 ranks. In canon, Knighthood is conferred by the Council; here a single Master acts for the Watch on a distant frontier. An Overseer ranks below a Lord, so Sorn does not make the player a Lord himself: he reads a sealed writ from an unnamed Darth.
- **Rank 3 trial.** The brief asks that ranks need missions and trials. The Collector fight is the rank 3 trial, and the contracts and story missions are the missions (5.8). There is no separate trial duel.
- **Telekinetic Lift** is listed under Universal powers in the brief. M3 makes it the Independent signature (granted by `wayfarer_pell` to Independents only), so the Independent path has a power of its own; the rubble caches are an Independent perk. Jedi and Sith may get Lift in M4 when the Universal set grows (open question 4).
- **Bolt colours:** Republic blue-white, Imperial red, poachers orange, sentinel lance amber-white. These are chosen for readability.

---

## 19. Open questions

Only the decisions that block development. Each has a recommended default, so work can go ahead without waiting. The lead records the owner's answer in `DECISIONS.md`.

1. **Who is the first major boss?** The roadmap asks for "the first major boss". A fallen Jedi Archivist ties the ruins, tombs, holocrons and both armies together, but the brief also allows a Sith Lord.
   **Default:** Orren Vasch, the Collector (8). A Sith Lord boss is kept for the M4 enclave and academy.
2. **Redemption and corruption.** The brief wants a substantial path-change questline. M2 deferred it to M3, but M3 is already full.
   **Default:** defer to M4, when there are enclave and academy locations to set it in. In M3, opposed-side holocrons (4.5) and the boss ending are the alignment choices, and the operator command stays the only path change. A late mentor join is refused once Chapter Three has begun (6.9).
3. **Rank 3 in M3, with the Collector fight as the trial.** Knight, Lord and Pathfinder by ceremony make a strong close to the chapter. The brief asks for trials as well as missions; the spec treats the boss fight as the rank 3 trial instead of adding a third duel.
   **Default:** ship rank 3 in M3 with the 5.8 requirements and the Collector fight as the trial. If the playtest shows the bar is too high, the lead lowers the `chapter_three.rank3*` values in config rather than cutting the ceremony.
4. **Telekinetic Lift for Independents only.** The brief lists Lift as a Universal power; M3 makes it the Independent signature so that path has a power of its own in the boss fight.
   **Default:** Independent only in M3 (10.2), recorded in `DECISIONS.md`. Jedi and Sith can be given Lift in M4 without changing its code (one node requirement).
