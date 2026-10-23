# Milestone 4: Broader Content (design spec)

This is the working spec for Milestone 4. It turns the fourth roadmap entry of `DESIGN_BRIEF.md` section 17 into concrete, testable features that build on Milestones 1 to 3:

- Additional weapons and armour.
- More crafting and specialisation.
- Planet-themed dimensions.
- Transport ships.

Milestone 3 deferred these items to Milestone 4 (`MILESTONE_3.md` 1, "Left for later milestones"); this spec takes the ones that fit the roadmap entry and moves the rest on (section 1, "Left for later"):

- armour beyond the four M3 sets, and armour upgrades and mods;
- player blasters and vibroweapons;
- crafting specialisation;
- planet-themed dimensions and transport ships;
- inhabited Jedi enclave and Sith academy hubs (they become the planets' hubs);
- vendors and reputation shops;
- more of the brief's powers (M4 takes Force Speed, Force Barrier and Force Choke);
- double-bladed sabers.

All numbers here are defaults. As before, live values go into `config/oldrepublic/balance.json` through `BalanceConfig` sections; section 14 lists every new key. Where this spec and the brief disagree, the brief wins and the difference goes into `DECISIONS.md`.

Contents:

1. Scope, cut order, and what is left for later
2. Player journey
3. Planets and dimensions
4. Ships and travel
5. Weapons
6. Armour and mods
7. Crafting, disciplines, schematics, credits and vendors
8. Chapter Four: "Stars to Steer By"
9. Force powers and skills
10. Enemies and the boss
11. Controls and HUD
12. Persistence, multiplayer, dedicated server and config
13. Effects and sounds
14. Balance
15. Architecture and work split
16. Shared identifiers
17. Test plan
18. Lore notes for `DECISIONS.md`
19. Open questions
20. Revision notes

---

## 1. Scope

### In Milestone 4

| Area | What ships |
|---|---|
| Story | **Chapter Four, "Stars to Steer By".** A price is put on the player's head. A Mandalorian hunter's seeker droid finds them, and the mentor (or, for an Independent, the journal) says it is time to leave this world for a while. The player repairs a grounded courier shuttle, learns a crafting discipline on the way, and flies to their path's world: **Selvaren** for a Jedi, **Varkhast** for a Sith, either for an Independent. Each world has an arc that teaches the path's new power and a crystal expedition that yields a second crystal. The hunter, **Torvin Kaast**, stalks the player across worlds and ends the chapter in a duel at his hunting camp. |
| Planets | Two planet dimensions, each with three biomes, its own sky, fog, weather and ambience, two harvestable resources, one new creature, a hub, and two kinds of exploration site. **Selvaren** is a highland forest world with the half-ruined **Greenwatch Enclave**, Watch waystones and the **Singing Hollow** crystal grotto. **Varkhast** is a red-rock and ash valley world with **the Proving** (an Imperial academy outpost), greater tombs and a duskglass rift. The home world (the overworld) stays the frontier. |
| Ships | One ship class, the **courier shuttle**: a large, owned entity that parks on a landing pad. It is repaired from a hulk (hull, power core, hyperdrive motivator, nav computer), fuelled with **hyperfuel cells**, worn by each jump and repaired with durasteel. Travel goes between unlocked **destinations** (home pad, Greenwatch pad, Proving pad) with a take-off, a hyperspace transition and a landing, all with camera, VFX and SFX. Up to three crew ride along. No free flight in M4. |
| Weapons | Player **blaster pistol**, **blaster rifle** and **heavy repeater** (the pistol and rifle reuse the M2 and M1 ids) with a heat mechanic, aimed fire and two mod slots; **vibroblade** (reused id) and **vibrosword**; **electrostaff**; **training saber**; the **double-bladed saber** (saberstaff), built at the Lightsaber Workbench from two crystals. |
| Armour | Four new sets: **Watch battle armour** (Jedi), **Sith war armour**, **Mandalorian armour** and **spacer's gear**. Every armour piece and blaster from M3 and M4 gets **mod slots**; mods are crafted and fitted at the Engineering Bench. The **jet pack** is a chest mod. |
| Crafting | The **Engineering Bench** (craft, modify, salvage). Four **disciplines**: Armstech, Armourcraft, Artifice and Medtech. A player keeps **two**, levels each from 1 to 5, learns **schematics**, and crafts at Standard, Fine or Masterwork quality with bad-luck protection. Medtech adds medpacs, stims, a fusion cutter and a scanner; Armstech adds grenades. Artifice adds the saber **lens** slot. |
| Economy | **Credits** as an item. Contracts, missions and loot pay them. **Vendors** (quartermasters and discipline trainers) at the outposts and both hubs sell schematics, mods, components and cosmetics, gated by reputation tier. |
| Powers | **Force Speed** (Universal; granted free to Independents), **Force Barrier** (Jedi signature, taught on Selvaren) and **Force Choke** (Sith signature, taught on Varkhast). Each has branches, mastery, counterplay, animation, VFX and SFX. |
| Enemies | **Mandalorian hunters** (jet pack hops, wrist flamer, wrist rocket, cover), the **Mk IV security droid** (resistances and weaknesses), the **hollowhorn** (Selvaren, territorial charger) and the **ash hound** (Varkhast, pack lunger). Planet hubs have their own NPCs. |
| Boss | **Torvin Kaast, the hunter**, fought in three phases at his hunting camp, with an ending choice. |
| Systems work | Story, missions, sites, guidance and saved positions become **dimension-aware**. A shared **TravelService** moves players, ships and owned entities between dimensions safely. |
| Controls and HUD | A heat gauge, an aim reticle, the **Weapon Action** key (vent, charge, mode), the ship screen and its flight HUD, the Engineering Bench and vendor screens, a Crafting journal tab, and a destination map. |

### Left for later milestones (on purpose)

- **M5 (Advanced Features):**
  - free flight, ship combat and space exploration (the brief's "later" and "finally" ship stages);
  - ship classes beyond the courier, and ship interiors players walk around in;
  - more planets: the Dromund Kaas-like storm jungle and the Tatooine-like wasteland port;
  - redemption and corruption questlines that change path (moved from M4: they are faction stories);
  - ranks above 3 (Master, Darth);
  - the Buyer's story, Mandalorian clans and Hutt cartels as faction stories;
  - the remaining powers: Stasis, Protect, Valor, mind influence, Drain, Fear, Rage, Crush;
  - more saber forms, dual-wielding and blade-length variants;
  - crystal tuning (cut from M4: crystals stay cosmetic, the M1 rule, until a design for crystal effects exists);
  - endgame encounters, raid-size bosses, legendary crafting, a personal enclave.
- **Not in M4, by design:**
  - voice acting (barks stay subtitled);
  - companions or hired crew NPCs;
  - PvP faction warfare;
  - player-built or player-moved landing pads other than one home **landing beacon** per player;
  - a ship that can be damaged by other players or mobs (4.1).

### Cut order (if the milestone runs long)

Nothing from the roadmap entry itself is on this list. The lead cuts from the top and records each cut in `DECISIONS.md` and `KNOWN_ISSUES.md`.

1. Selvaren's second moon (3.1).
2. The electrostaff (5.3). The vibrosword covers the heavy melee slot.
3. The jet pack mod (6.4). Mandalorian armour keeps its set bonus.
4. Spacer's gear (6.2). Independents use Mandalorian armour or the M3 sets.
5. Player grenades (7.6). The frag grenade stays an enemy entity, with no player item.
6. The training saber (5.5). The Greenwatch sparring and the `selvaren_spar` contract then use the player's own saber under the M2 `Spar` rules.
7. Ship upgrades beyond seats and hold (4.7). Hull and fuel stay.
8. The destination map screen (11.3). The ship's Nav tab list stays.
9. The Independent bot run (17.3). The Independent path is checked by hand.

---

## 2. Player journey

1. **The mark.** Chapter Three is complete. One in-game day later, or the next sleep, at a safe moment, a **seeker droid** drops out of the sky near the player, sweeps them with a red scan fan, chirps and flies off. A journal entry follows: someone has put a price on them. Saves that finished Chapter Three in M3 enter Chapter Four on their next join.
2. **A way off-world.** The mentor speaks plainly: the hunter is a Mandalorian, and a frontier world is a small place to hide.
   - **Jedi:** Master Saelen gives the player the Watch's grounded courier, the *Patient Star*, which has sat under a tarp near the camp since before the Treaty. She also gives the navigation coordinates of **Selvaren**, the world the Watch came from, read from the Archive (or, if the Archive was kept or unmade, from her own memory).
   - **Sith:** Overseer Sorn arranges an Imperial courier written off after a hard landing and gives the coordinates of **Varkhast**, where the Empire trains acolytes among the tombs.
   - **Independent:** the journal points back to **Courier's Fall**. The wreck's nav log names two worlds and the place nearby where Vasch laid up a spare courier, which can fly again.
   - Everyone receives the **Engineering Bench** schematic and chooses their first **discipline** at the bench (7.2).
3. **Repairs.** The hulk needs four things, each a short task that teaches a system:
   - **hull plating:** 12 durasteel plates (the M3 resource);
   - **a power core:** crafted at the Engineering Bench from the free schematic;
   - **a hyperdrive motivator:** salvaged from a Wreck Field, bought from an outpost quartermaster, or taken from a mission;
   - **a nav computer:** crafted, then loaded with the coordinates.

   Then **hyperfuel cells** (crafted, or bought) go in the tank. The ship rises off its supports and becomes a real ship.
4. **First flight.** The player boards, picks the destination on the nav console, and watches the take-off, the jump and the landing on their world's pad. They meet the hub: the Greenwatch's keeper and her few apprentices, or the Proving's Overseer and his acolytes. Independents choose either world first and are welcomed at both.
5. **The world arc** (path):
   - **Jedi on Selvaren:** the keeper asks for help restoring the Greenwatch. The player clears a corrupted **waystone** with the Keeper's teaching and learns **Force Barrier**. The **Singing Hollow** expedition yields a second crystal. Hollowhorns guard their meadows.
   - **Sith on Varkhast:** the Overseer sets the **Proving**: survive the tomb trial in **Zarvos's Sepulchre**, where Lord Hadrek Zarvos's echo teaches **Force Choke**. The **duskglass rift** yields a second crystal, bled to red. Ash hounds hunt the valleys.
   - **Independent:** Tessaly Arn's echo, speaking from the Compass, leads them to a Wayfarer cache on whichever world they land on first, which teaches **Force Speed** and its Wayfarer branch. They may take either crystal expedition.
   - Second crystal in hand, any player can build a **saberstaff** at the Lightsaber Workbench.
6. **The hunt.** Torvin Kaast is never far behind. The first time the player's ship lands back home, a charge wedged in a landing strut blows (hull at 10) and the ship must be patched. Mandalorian hunters ambush them twice: first on a planet surface, then on another world (back home, or on the other planet). Each time, the player learns more: Kaast is no clan bounty hunter but a hired gun carrying a sealed contract from someone called only **the Buyer**.
7. **The duel.** At level 14 and with the world arc done, Kaast leaves a marker beacon: a challenge. It points to his **hunting camp** on the player's path world (an Independent's first world). The camp is a ring of scrap walls, gun nests and a central pit. Kaast fights in three phases: **The Hunt** (jet pack, rifle, rockets), **The Trap** (mines, a net, flame and his droid) and **The Last Stand** (a vibro-axe and close fury). At 10% he yields.
8. **The choice.** Spare him (he leaves his armour pattern and the name of the Buyer's broker as a debt paid), take his armour and send him off, or end him. Each ending has a short scene. The player reports to the mentor (Independents: to the Compass), and every destination unlocks.
9. **The ongoing loop:**
   - discipline levels, schematics and Masterwork crafting;
   - planet contracts at both hubs, with the daily soft cap from M3;
   - the second world and its expedition;
   - the full Mandalorian set from Kaast's pattern and hunter drops;
   - hunter bounties: rare hunters with a contract on the player, at most one a day (they begin with the hunt, 8.7, and continue after the chapter);
   - ship upgrades.

A solo player at a normal pace finishes Chapter Four in about 6 to 8 hours, from level 11 to about 15 (section 14 has the XP budget).

---

## 3. Planets and dimensions

Ownership:

- **W1** owns the two dimensions, their biomes, noise and features, the planet blocks and resources, the worldgen structures (waystones, greater tombs), the hub, expedition and camp templates, and the terrain palette.
- **W2** owns everything that lives in a hub (keepers, vendors, terminals, dialogue) and every story use of a planet site.
- **W3** owns the landing pads as travel endpoints (4.5) and `TravelService`, which every cross-dimension move goes through.

Every new template is generated by Python, as in M3: `tools/assets/gen_structures_m4.py` (W1: waystones, greater tombs, hubs, expeditions, the hunting camp) and `gen_structures_ship_m4.py` (W3: the hulk cradle and the landing beacon pad). Both import `Template`, `hash01`, `value_noise` and `DATA_VERSION` from `gen_structures.py` and write sidecar anchors. The M3 art rule holds on both worlds: no floor circles, sigils or glowing runes.

### 3.1 How a planet is built

A planet is a data-driven dimension. No Fabric dimension API exists in 0.161 (`notes_api.md`), and none is needed: the world loads `data/<ns>/dimension/<id>.json` like any data pack dimension, and `fabric-dimensions-v1` (already on the classpath) makes `level.dat` fail soft if the mod is later removed. Phase 0 shipped placeholder files for both planets (15.1); W1 replaces them with the files below.

| File | Contents |
|---|---|
| `data/oldrepublic/dimension_type/selvaren.json`, `varkhast.json` | A copy of `minecraft:overworld` (dumped verbatim by the `m4_dims` probe): `has_skylight` true, `has_ceiling` false, `min_y` -64, `height` and `logical_height` 384, `default_clock` `minecraft:overworld`, `timelines` `#minecraft:in_overworld`, no `skybox` key (the overworld sky). Its `attributes` map sets the planet's `minecraft:visual/sky_color`, `fog_color` and `cloud_color`, `minecraft:audio/ambient_sounds` and `background_music` (the planet's music, 13.4), `minecraft:gameplay/bed_rule` (beds set spawn and sleep when dark, as in the overworld) and `respawn_anchor_works` false. 26.2 has no `effects` id or `fixed_time`; everything visual is an attribute. |
| `data/oldrepublic/dimension/selvaren.json`, `varkhast.json` | `type` = the matching dimension type; `generator` = `minecraft:noise` with a `minecraft:multi_noise` biome source listing the planet's three biomes with explicit climate parameters, and `settings` = the planet's noise settings |
| `data/oldrepublic/worldgen/noise_settings/selvaren.json`, `varkhast.json` | Derived from `minecraft:overworld` (dumped by the probe). Selvaren: sea level 58, deeper valleys, default block stone. Varkhast: sea level -64 (no oceans; dry lakebeds), default block stone with terracotta and red sandstone bands from the surface rule, aquifers off, lava lakes rare. |
| `data/oldrepublic/worldgen/biome/<id>.json` | Six biomes (3.2, 3.3). 26.2 biome JSON carries visual attributes (`minecraft:visual/sky_color`, `fog_color`, `water_fog_color`), `effects` (water, grass and foliage colours), `spawners`, `spawn_costs`, `features`, `carvers`, `temperature`, `downfall` and `has_precipitation` |
| `data/oldrepublic/worldgen/{configured_feature,placed_feature}/*.json` | Planet trees, plants, ores and rock spires, built from vanilla feature types (tree, random_patch, ore, disk, block_column) |

**Custom biomes on purpose.** Structures are chosen by vanilla biome tags (`has_structure/*`), so a planet that reused vanilla biomes would grow M3 ruins, villages and outposts. Planet biomes are in no vanilla `has_structure` tag and not in `#minecraft:is_overworld`. They have tags of their own (`oldrepublic:is_selvaren`, `oldrepublic:is_varkhast`, `oldrepublic:is_planet`), which only the M4 planet structures (3.4) use. A planet therefore has vanilla ores, caves, plants and ambient creatures, and no vanilla or M3 structures.

**Creature spawns.** W1's biome `spawners` list vanilla mobs only. W6 adds the mod creatures (hollowhorns on Selvaren, ash hounds on Varkhast) with Fabric's `BiomeModifications` on the biome tags `oldrepublic:is_selvaren` and `oldrepublic:is_varkhast`, so W1's worlds load before W6's entity types exist (15.1). The creature columns below list both.

**Sky and light.** There is no sky registry in 0.161. Sky, fog, cloud and water colours are environment attributes, set per biome and per dimension type, so the sky changes as the player crosses biomes. Planets use the overworld clock (`DimensionType#defaultClock`), so day and night match the home world and the story's day counter (3.6) needs no conversion. The sun and moon stay vanilla; a second moon on Selvaren, drawn in `LevelRenderEvents` after the sky, is optional (W1; first on the cut list).

**Weather.** Both planets use vanilla rain on their own level. Selvaren rains often (biome `downfall` 0.8 in the highwood and tarns); Varkhast never rains (`has_precipitation` false) and instead has **ashfall**: falling grey flakes through the ambient particle environment attribute (`EnvironmentAttributes.AMBIENT_PARTICLES`, a `List<AmbientParticle>`, confirmed by the follow-up probe; the fields of `AmbientParticle` are `// API-CHECK`, and if they cannot carry the flake, W1 falls back to a client `ClientTickEvents` emitter bounded by `Vfx.count`) and wind beds, thicker in the ashfall biome and during the planet's "storm" (the level's rain flag, which on Varkhast never wets anything).

**Portals and other dimensions.** Nether portals do not light on a planet, and an End gateway or `/execute in` remains an operator tool. The ship is the only way on or off a planet for a survival player (4). No mod code is needed for the portals: the follow-up probe shows that vanilla `BaseFireBlock#inPortalDimension` lights portals only in the overworld and the Nether. A portal block placed by a command is an operator's choice.

### 3.2 Selvaren

The world the Watch came from: cool highland forest, standing stones and mist, old enough that its stones still hum. Since the war the Watch has dwindled to a keeper and a few apprentices.

| Biome | Look | Ground and plants | Creatures (`spawners`) |
|---|---|---|---|
| `selvaren_highwood` | Tall silver-barked trees (birch logs with pale leaves tinted by the biome), ferns, moss carpets, light shafts | grass, podzol patches, mossy cobble boulders; **silverleaf** under the trees | foxes, rabbits; hollowhorns rarely; vanilla monsters at night at 50% weight, no creepers |
| `selvaren_meadow` | Rolling heather uplands, wind, standing stones, wide skies | grass and coarse dirt, tall grass, heather (pink tinted short grass), stone outcrops | **hollowhorn** herds (3–5), rabbits, bees |
| `selvaren_tarn` | Misty lakes in stone cups, reeds, cliffs, heavy fog | gravel shores, clay, sugar cane, lily pads; **aurelite** veins in exposed cliffs | frogs, salmon; drowned never |

| | Selvaren |
|---|---|
| Sky | pale teal (`#8fc6c9`) by day; fog `#b7d3d0`, heavier in the tarns; clouds white |
| Ambience | wind through leaves, distant chimes from the waystones (a low, occasional bell-like hum), birdsong; at night, owls and the hum louder |
| Resources | **silverleaf** (`silverleaf` plant block → `silverleaf` item; Medtech), **aurelite** (`aurelite_ore` in stone and deepslate → `aurelite_shard`; Artifice and Armourcraft) |
| Hub | **The Greenwatch Enclave** (3.5) |
| Sites | **Watch waystones** (worldgen, common), the **Heartstone** (shared, beside the hub), the **Singing Hollow** (per player, crystal expedition), the **hunting camp** for Jedi (per player, 10.5) |

### 3.3 Varkhast

A red-rock valley world where the Empire trains acolytes among the tombs of old Sith lords. The air is thin, the sun dull, and ash comes down from somewhere beyond the ridges.

| Biome | Look | Ground and plants | Creatures |
|---|---|---|---|
| `varkhast_redrock` | Mesas, hoodoos and wind-cut gullies in banded red stone | red sand, terracotta bands, red sandstone; dead bushes; **ashroot** in gully shade | **ash hounds** at dusk and night; husks |
| `varkhast_ashfall` | Grey plains under a constant fall of ash, basalt spires, dead trees | coarse dirt, gravel, basalt columns, tuff; ash particles | ash hounds (more), skeletons; no creepers |
| `varkhast_tomb_valley` | Deep valleys lined with carved cliff faces and old obelisks | smooth sandstone and blackstone in the walls; **duskglass** clusters in shaded walls | few spawns; tomb sentinels inside the greater tombs |

| | Varkhast |
|---|---|
| Sky | rust orange (`#c27a4e`) by day, deep red dusk; fog `#8a5a44`, grey in the ashfall; no clouds |
| Ambience | dry wind, grit, distant thunder with no rain, far-off Imperial loudspeakers near the Proving |
| Resources | **ashroot** (`ashroot` plant → `ashroot`; Medtech), **duskglass** (`duskglass_cluster`, a crystal block like amethyst → `duskglass_shard`; Artifice and Armourcraft) |
| Hub | **The Proving** (3.5) |
| Sites | **greater tombs** (worldgen, uncommon), **Zarvos's Sepulchre** (shared, below the Proving), the **duskglass rift** (per player, crystal expedition), the **hunting camp** for Sith (per player) |

### 3.4 Planet sites

| Site | `SiteKind` (appended) | Placement | Size (x × y × z) | Owner | Purpose |
|---|---|---|---|---|---|
| Watch waystone | `WAYSTONE` | worldgen, Selvaren (`oldrepublic:waystones` set, spacing 20, separation 8, salt 905117), all three biomes | 11×9×11, bottom 2 layers buried (Milestone 6: now 21×18×21) | W1 | A standing stone in a ring of fallen stones; Sense reveals a waystone cache; 1 in 4 is **corrupted** (dark veins in the stone, wilting plants, hollowhorns avoid it) and is a Jedi arc target or a contract |
| Greater tomb | `GREATER_TOMB` | worldgen, Varkhast tomb valley and redrock (`oldrepublic:greater_tombs`, spacing 36, separation 14, salt 905123) | gate 21×14×21 (start, adapted; Milestone 6: now 35×22×35), descent 11×18×27, two crypts 21×12×21 | W1 | A bigger M3 tomb: Imperial dig camp, Mk IV droids, sentinels, an urn puzzle, a sealed crypt opened with the fusion cutter (7.6) or Force Push, `chests/greater_tomb` |
| Greenwatch Enclave | `PLANET_HUB` | runtime, once per server, on first arrival (3.5) | 63×24×63 (Milestone 6: now 95×51×95) | W1 template, W2 contents | Hub: landing pad, keeper, vendors, terminal, medbay |
| The Proving | `PLANET_HUB` | same | 63×28×63 (Milestone 6: now 95×59×95) | W1 template, W2 contents | Hub: landing pad, Overseer, vendors, terminal, medbay; the stair to Zarvos's Sepulchre |
| Heartstone | `HEARTSTONE` | runtime, once per server, 80–120 blocks from the Greenwatch (Milestone 6: 250–400, by far placement); placed by `PlanetSites.ensure` the first time any player's "The Heartstone" step begins | 19×12×19 (Milestone 6: now 35×26×35) | W1 | The Jedi arc's corrupted great waystone (8.4) |
| Zarvos's Sepulchre | `SEPULCHRE` | runtime, once per server, part of the Proving's placement, underground | 33×16×41 (Milestone 6: now 55×16×51) | W1 | The Sith arc's tomb trial (8.4) |
| Singing Hollow | `EXPEDITION` | runtime, per player, Selvaren, 300–450 blocks from the hub (Milestone 6: 400–650) | surface 13×10×13 (Milestone 6: now 27×22×27); grotto 31×16×31 | W1 | Crystal expedition (8.4): an aurelite-lit cave where the crystals answer in tones |
| Duskglass rift | `EXPEDITION` | runtime, per player, Varkhast, 300–450 blocks from the hub (Milestone 6: 400–650) | surface 15×8×15 (Milestone 6: now 27×12×27); rift 23×28×31 | W1 | Crystal expedition: a fissure of black glass that bleeds a crystal red |
| Wayfarer cache | `WAYFARER_CACHE` | runtime, per player, on the Independent's first world, 150–250 blocks from the hub (Milestone 6: 350–550) | 13×9×13, 4 layers buried (Milestone 6: now 27×25×27, 15 layers buried) | W1 | Tessaly Arn's cache: teaches Force Speed (8.4) |
| Hunting camp | `HUNTING_CAMP` | runtime, per player, on the path world (an Independent's first world), 400–600 blocks from the hub | 41×14×41 (Milestone 6: now 57×16×57) | W1 template, W6 fight | Kaast's arena (10.5) |
| Ambush sites | `MISSION_SITE` (M3, reused) | runtime, per player | 13×6×13 (Milestone 6: now 23×12×23) | W1 | The two hunter ambushes (8.5) |
| Ship cradle | `SHIP_CRADLE` | runtime, per player, on the home world, at the mentor camp, Sorn's camp or Courier's Fall (4.2) | 17×7×21 (Milestone 6: now 33×18×37) | W3 | The hulk under a tarp |

**Placement rules.** Runtime planet sites use `RuntimeSites.placeSurface` with the M3 rings and `MeetingSites.Rules`, but in the planet's level and with the planet's terrain palette (3.6). They record into `WorldSites` with their dimension. Because chunks near a fresh hub may not exist yet, a per-player planet site is placed by `PlanetSites.ensure` when its owner is within 160 blocks (`planets.siteEnsureRange`) of the ring distance or when its stage begins, whichever is later; the Calling points to the ring until then (the M3 rule, now with the dimension). The Heartstone and Zarvos's Sepulchre are shared sites: for them `PlanetSites.ensure` and `PlanetSites.find` return the one server-wide position, whoever the owner is (15.1).

**Spawn overrides.** Every structure piece box sets `monster: []` (the M3 rule). The hubs also clear creature spawns within their box, and ash hounds never spawn within 64 blocks of the Proving (`creatures.houndHubExclusion`; a spawn rule in W6's `BiomeModifications` entry).

### 3.5 Hubs

Hubs are shared and placed once per server. The first time any ship sets course for a planet (4.6), `PlanetHubs.ensure(server, planet)` loads the 11×11 chunks around (0, 0) on that planet with a temporary chunk ticket (radius 5, `planets.hubTicketRadius`, through `ServerChunkCache#addTicketWithRadius` and the ticket type of 4.6), finds the surface with `MeetingSites.find` from (0, 0) (rings 0, 16, 32; footprint 63×63; max slope 6), places the hub with `RuntimeSites.placeSurface`, and records it as `PLANET_HUB` in `WorldSites`. The radius covers the farthest ring, half the footprint and a chunk of margin (32 + 32 + 16 = 80 blocks). (Milestone 6: the hubs are 95 blocks square, the search uses each template's own size, and the ticket is as wide as the search and the fit need plus one chunk: 15×15 chunks for the Greenwatch and the Proving.) The Proving's placement includes Zarvos's Sepulchre under it; the Heartstone is placed later, when it is first needed (3.4). It runs during the take-off and the jump (4.6), so the landing never waits on world generation. If no ring fits, it places the hub at (0, 0) anyway, on a raised platform with stilts to the ground (the M3 fallback idea), and logs it.

| | Greenwatch Enclave (Selvaren) | The Proving (Varkhast) |
|---|---|---|
| Look | A half-ruined Watch enclave in a forest clearing: a stone hall with a broken dome, overgrown cloisters, a garden, apprentices' tents and a restored landing pad of moss-cracked stone | An Imperial academy outpost against a cliff: prefab barracks, a drill yard, banners, searchlights, a pad with blast walls, and a carved stair down into the rock |
| Host (W2) | **Keeper Ansa Veylin**, last keeper of the Greenwatch, patient and tired | **Overseer Vael Kaddor**, who runs the Proving's trials and wants results |
| People | 3 apprentices (ambient barks, a sparring partner for the training saber, 5.5) | 4 acolytes, 2 Imperial guards, a Mk IV droid on patrol (never hostile to a Sith) |
| Vendors (7.8) | Watch quartermaster; Medtech and Artifice trainers; a spacer (fuel and ship parts) | Imperial quartermaster; Armstech and Armourcraft trainers; a spacer |
| Services | contract terminal (`contract_terminal`, planet contracts, 8.7); medbay bed (respawn, 12.2); pad terminal (`pad_terminal`, recall, 4.8) | the same |
| Reception | Jedi and Independents: welcome. Sith: the Watch's cold peace (Wary), no service except the spacer and the discipline trainers (7.8) | Sith and Independents: welcome. Jedi: Wary, no service except the spacer and the discipline trainers |

The hub's NPCs are persistent, unbreakable as a site (the M3 terminal rules), and restore from the hub's `site_spawner` anchors if killed (one check per in-game day). The hub template's pad has **four bays**; a fifth ship lands in a ring around the hub (4.5).

### 3.6 Making the existing systems dimension-aware

M1 to M3 story, missions and sites assume the overworld (`notes_world.md` B, C, F, G, H). Phase 0 changed these in place (15.1); workers build on the result.

| What | Change | Owner |
|---|---|---|
| `StoryService.inOverworld` and the ~25 callers; direct `OVERWORLD` checks in `Crossing`, `PlacementRules`, `EchoShrines`, `Outposts`, `SiteCommands`, `MissionService`, `MissionSites`, `PatrolSpawner`, `ReliquarySites` | New `Planets.isStoryLevel(Level)`: true for the overworld and both planets, for M4 story and missions. The existing overworld checks are already correct home-world checks and stay as they are, so a Chapter Three cairn or a Republic patrol never appears on Selvaren; new code that means "home world only" uses `Planets.isHomeWorld(Level)` | lead (Phase 0) |
| Mentors found through `server.overworld()` | Unchanged: mentors live on the home world. Chapter Four dialogues on a planet go through the hub hosts or the journal | – |
| Story day counters (`server.overworld().getGameTime()`, ~11 sites) | Unchanged. New code uses `Clocks.day(server)` and `Clocks.time(server)` (lead). `StoryService.now(p)` (the player's own level time) is also unchanged: a data-pack dimension's level data derives from the overworld's, so its game time is the overworld's and values do not jump | lead |
| Saved positions without a dimension | New fields store `GlobalPos`. Old fields (Chapter Three positions, `StoryData` camp and encounter, `MissionData.Active` site and board, `surveyed`, `TerminalBinding`) are read as overworld | lead codecs; owners use them |
| `MissionData.Active` (14 fields) | Gains a nested `where` record holding the site's and the board's dimensions (both default overworld), so it stays under 16 fields. The `BlockPos` site and board fields stay; `siteGlobal()` and `boardGlobal()` combine them with `where`. Old saves decode with `where` absent = overworld | lead |
| `Guidance.Request` | Gains a nullable `dimension` (the M3 four-argument constructor is kept and means the player's level). When the best request's dimension differs from the player's level, the Calling uses the **off-world** variant (`QuestEffects.CALLING_OFFWORLD` = 9): the cue points at the player's ship body if it is loaded in the player's level, else at their home pad if that is in the player's level, else it is the faint cue (`CALLING_NONE`). The action bar says "The Calling comes from Selvaren." (`message.oldrepublic.calling.offworld` with `dimension.oldrepublic.<planet id>` from W1's `planet.json`) | lead |
| `WorldSites` caps (`count(kind)` across all dimensions; 512 records) | `count(kind, dimension)`; `WorldSites.Data.MAX_RECORDS` rises to 1,024 (the M3 cap test is written against `MAX_RECORDS`) | lead |
| `adaptTerrain`, `CampPoolElement`, generator `ground_layers` (always grass, dirt, stone) | `TerrainPalette.at(ServerLevel, BlockPos)` returns top, fill and base blocks from the biome (lead: overworld biomes keep grass, dirt and stone; W1 adds the six planet biomes). Runtime placement and adaptation use it | lead, W1 |
| Things cancelled on `AFTER_PLAYER_CHANGE_LEVEL` (holds, dialogues, attunement, story forget, site tracker resend, saber throw and spar, skill cancels, Force forget, trials) | Unchanged: a jump is a dimension change. The ship's own flight is not cancelled by it (4.6) | – |
| `SiteTracker`, `LocatorQueue`, `StructureLocator` | Already keyed by dimension. `StructureLocator.find` gains planet `SiteKind`s through structure tags `oldrepublic:waystones` and `oldrepublic:greater_tombs` | W1 |

---

## 4. Ships and travel

Ownership: **W3** owns the ship entity, its model and screens, the flight sequence, the landing beacon, the pad terminal and its dialogue, the ship cradle template, and the `travel` package except the lead's contract files (`ShipState`, `Destination`, `ShipRecord`, `TravelData`, `TravelAttachments`, `TravelLocks`): W3 owns `TravelService`, `ShipRegistry`, `Ships` and everything it adds. `TravelService.moveAcross` is the lead's real code inside W3's file; it is frozen, and W3 does not change its body (15.1). W2 owns the story steps that unlock destinations; W5 owns the schematics for ship parts.

The brief asks for ships in stages and warns against promising seamless space travel. M4 delivers the first two stages: **a repairable crashed shuttle**, and **a ship that travels between unlocked destinations**. Flight is a scripted sequence, not player control.

### 4.1 The courier shuttle

| | |
|---|---|
| Entity | `oldrepublic:courier_shuttle`, category MISC, hitbox 7 × 4 × 7 (one box; the model is about 9 × 4 × 11 and overhangs it), tracking range 10 chunks, persistent |
| Look | A blunt-nosed courier with swept-down wings, a ramp under the tail, landing struts, twin engine nacelles. Off-white hull with faded paint: Watch green on the *Patient Star*, Imperial grey on Sorn's courier, Vasch's rust-red on the Independent's. Each player's ship carries a short hull code (the owner's initials and a seeded number) on the nose |
| Seats | Pilot (the owner) plus 2 passengers (`passengerAttachments`), 3 with the passenger seat upgrade. Players board by using the ramp (interact) and leave with sneak, or automatically at Settle (4.6) |
| Hold | 27 slots (36 with the cargo upgrade), opened from the ship screen by the owner only |
| Health | None. The ship takes no damage from mobs, players, fire, explosions or lava (`isInvulnerableTo` returns true for every source except `/kill`, which removes only the body, 4.9). Its **hull integrity** (0–100) is a separate number worn by jumps and sabotage |
| State (`ShipState`, by ordinal, append only) | `HULK`, `GROUNDED` (repaired, no fuel), `READY`, `LAUNCHING`, `IN_TRANSIT`, `LANDING`, `DISABLED` (hull below the jump minimum) |
| Damage effects | Below 40 hull: sparks from one nacelle and a thin smoke trail; below 20: a dead running light, smoke from the hull seam, a stutter in the engine hum. The effects say why the ship will not jump |

**Movement and collision.** On the ground the ship is still. During take-off and landing the server moves it along a fixed path (4.6) and the client interpolates (`InterpolationHandler`). The ship is a plain `Entity` (not a `VehicleEntity`, which brings damage and drops, and not a `LivingEntity`). It is collidable like a boat (players can stand on its back), with its riders excepted.

**Nobody steers it.** `getControllingPassenger()` returns null, so no client sends vehicle movement for it and the server's `handleMoveVehicle` never applies. That also keeps every rider safe from both floating kicks: the personal kick skips anyone who is a passenger, and the vehicle kick only counts the controlling passenger of the root vehicle (`m4_ship` digest, sections 14–15; confirmed by the follow-up probe, which also shows `handleMoveVehicle` acts only for the controlling passenger). This is why the flight sequence keeps everyone seated.

### 4.2 The hulk and the repair

Each player gets one hulk in Chapter Four (8.2), placed with a **ship cradle** (`SHIP_CRADLE`, 17×7×21 (Milestone 6: now 33×18×37, placed 250 to 450 blocks from the giver by far placement): timber supports, a patched tarp, tool crates, a work lamp) next to the giver:

- **Jedi:** the *Patient Star*, 32–64 blocks from Saelen's camp;
- **Sith:** an Imperial courier written off after a hard landing, 32–64 blocks from Sorn's camp;
- **Independent:** Vasch's spare courier, laid up 32–64 blocks from the wreck at Courier's Fall (the wreck itself is the M3 site and stays as it is).

The ship screen's **Repairs** tab shows four component sockets. Each takes the part from the player's inventory with one click, plays its fitting effect and saves at once:

| Socket | Needs | Where it comes from |
|---|---|---|
| Hull plating | 12 `durasteel_plate` (may be fitted in any number of steps) | the M3 resource: Wreck Fields, salvage, troopers, tomb loot |
| Power core | `power_core` | Engineering Bench, common schematic (7.3): 4 durasteel, 1 aurelite shard **or** 4 redstone and 2 copper ingots (the first core needs no planet resource), 1 `singing_crystal` **or** 1 gold ingot |
| Hyperdrive motivator | `hyperdrive_motivator` | three routes, any one: salvage at any Wreck Field (Sense near its `HEART` anchor finds a motivator still in its cradle, once per player, 8.2); an outpost quartermaster (via the officer's Requisition option, 400 credits, or 360 at Honoured); or the story mission **"A Part Off the Books"** (8.2) |
| Nav computer | `nav_computer` (with `nav_data`) | Engineering Bench, common schematic: 2 durasteel, 4 redstone, 1 glass pane, 1 `holocron_shard` or 1 emerald. Crafted blank; using it on the mentor (or, for an Independent, the wreck's console) loads the coordinates (`nav_data` lists the planets) |

When all four are fitted, the hulk becomes `GROUNDED`: the tarp is pulled back, the supports retract with a hiss and the running lights come on. **Fuel** turns it `READY`.

### 4.3 Fuel

- `hyperfuel_cell`: crafted at any Engineering Bench from the common schematic (1 durasteel, 4 redstone, 1 glowstone dust → 2 cells) or bought from a spacer (40 credits) or an outpost quartermaster.
- The tank holds 4 cells (6 with the fuel tank upgrade). The owner fuels from the ship screen or by using a cell on the ship.
- Every jump costs **1 cell**. A ship with an empty tank is `GROUNDED`.
- The first cell is free: the mentor (or Vasch's wreck) leaves two cells in the ship's hold when the hulk is placed.

### 4.4 Hull wear and repair

- Every jump costs **5 hull** (3 with reinforced plating).
- Below 20 hull the ship is `DISABLED` and refuses to jump; the nav console says why.
- **Repair:** using a `durasteel_plate` on the ship adds 5 hull (one plate per use, 10-tick cooldown, welding sparks). An Armourcraft or Armstech player at discipline level 2 adds 8 per plate. A spacer repairs to full for 6 credits per point.
- **Sabotage** (8.5) sets the hull to 10, once.

### 4.5 Destinations and pads

A destination is a frozen id with a dimension and a pad:

| Id | Name | Dimension | Pad |
|---|---|---|---|
| `home` | "Home" (the frontier) | overworld | the owner's **landing beacon** if placed (4.8), else the ship cradle |
| `greenwatch` | "Greenwatch, Selvaren" | `oldrepublic:selvaren` | the Greenwatch Enclave pad (3.5) |
| `proving` | "The Proving, Varkhast" | `oldrepublic:varkhast` | the Proving pad |
| `hunting_camp` | "The hunter's beacon" | the path world | a clearing 48 blocks outside the camp; unlocked at CHALLENGED and never locked again, so the daily rematch (10.6) stays in reach (8.6) |

- Unlocked per player in `TravelData` (12.1). `home` is always unlocked. Jedi unlock `greenwatch` and Sith `proving` in Chapter Four (8.2); Independents unlock both. Every destination unlocks when the chapter ends (8.6).
- A hub pad has 4 bays. A landing ship takes the first free bay; if all four are taken it lands at the nearest flat spot found by `MeetingSites.find` on rings 40, 48 and 56 blocks from the hub centre (`ship.overflowRings`; the hub's own footprint reaches 32). (Milestone 6: 64, 72 and 80, for hubs up to 95 blocks square.) The bay is freed when the ship leaves or after 1 in-game day unattended (the ship stays; only its bay claim lapses).
- Landing at a pad whose surface is blocked (a player built on it) shifts to the nearest clear spot with the same search.

### 4.6 The flight

The owner opens the ship screen, picks a destination on the **Nav** tab (or the destination map, 11.3) and presses **Launch**. `TravelService.launch` checks (through `canLaunch`): owner, state `READY`, fuel ≥ 1, hull ≥ 20, destination unlocked, not in combat (no hostile within 16 blocks that has damaged a crew member in the last 100 ticks). Every rider is crew already: only crew can board (4.9), and removing someone on the Crew tab puts them off the ship. It then:

| Phase | Ticks | Server | What the riders see and hear |
|---|---|---|---|
| Spool | 40 | State `LAUNCHING`. The launch adds a chunk ticket at the destination pad (radius 3) with `ServerChunkCache#addTicketWithRadius`, using W3's own `oldrepublic:ship_pad` ticket type (`// API-CHECK` the ticket type registry; fallback: the vanilla `PORTAL` type), and, for a planet's first visit, calls `PlanetHubs.ensure` (3.5) | engine whine rising, ground dust blown outward in irregular puffs, the ramp closes, struts retract |
| Climb | 60 | Rises 40 blocks on an ease-in curve, turning to face the nose's heading | rumble, nacelle exhaust, camera shake (scaled by the shake setting) |
| Jump | 10 | At the top, the flight code calls `TravelService.moveAcross` to move the ship and its riders to the destination dimension, 40 blocks above the pad, then the state becomes `IN_TRANSIT` | a pressure thump and the stars stretching into lines |
| Hyperspace | 80 | Waits for the destination chunks to be loaded and ticking (up to 200 more ticks; if still not ready, the ship lands where it is above the pad's column) | the **hyperspace overlay** (11.2): streaking light, a blue-white tunnel and the engine's deep hum, covering client chunk loading |
| Descent | 80 | State `LANDING`; descends to the pad on an ease-out curve; struts deploy at 6 blocks | dust kicked up in uneven gusts, a repulsor whine falling in pitch, landing thump |
| Settle | 20 | State `READY`; `GROUNDED` if the tank is now empty; `DISABLED` if the hull is now below 20 (`DISABLED` wins over `GROUNDED`). Every rider is dismounted and placed beside the ramp. The chunk ticket is released after 200 ticks | the ramp lowers, a hiss; the destination's name as a banner |

About 15 seconds in all. The sequence cannot be cancelled once it starts. The fuel and hull are taken at the jump. The nav console warns before launch when the jump will leave the hull below 20 ("The ship will need repairs on arrival").

**How the jump moves riders.** `TravelService.moveAcross(ship, level, pos, yaw)` (the lead's code, 15.1) teleports the **ship**, the root vehicle, with `Entity#teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, xRot, DO_NOTHING))`. Vanilla's cross-dimension path (`m4_dims` digest, section 9) ejects the riders, teleports each one (players through `ServerPlayer#teleport`, which keeps the same `ServerPlayer` and sends the client a respawn into the new level), re-creates the ship in the new level with `restoreFrom` (same UUID, a new Java object), and remounts every rider. So the ship's type must be creatable with `EntitySpawnReason.DIMENSION_TRAVEL`, no code may keep a reference to the old body across a jump, and everything the ship needs survives because its state lives in `ShipRegistry` (4.9) and its save data. Vanilla already remounts the passengers; `moveAcross` then remounts any rider it recorded that is not aboard, and returns the new body. It finds those riders by UUID, which works only in tracked chunks, so every caller holds a chunk ticket on the destination first (the Spool ticket here). Fabric's `FakePlayer` refuses to ride, so tests use `M3TestPlayers.listed`.

**Who travels.** Only riders. A crew member standing beside the ship at launch is left behind, and the nav console warns the owner: "Crew aboard: 1 of 2".

**Camera.** Riders keep their own camera. If `ClientConfig.shipCamera` is on (default), the client switches a first-person rider to third person behind the ship for the climb and descent and restores their camera at Settle; it never changes a camera the player chose themselves mid-flight. Vanilla only backs the third-person camera off for a vehicle that is a `LivingEntity`, so a rider of the ship gets a transient `CAMERA_DISTANCE` attribute modifier (`oldrepublic:ship_camera`, +8) while aboard, removed on dismount, death, disconnect and landing. The follow-up probe confirms `Attributes.CAMERA_DISTANCE` exists but shows that `Player#createAttributes` does not add it; whether `LivingEntity#createLivingAttributes` does was not probed, so this stays `// API-CHECK` (`getAttribute` may return null). Fallback: a W3 camera mixin in `mixin/travel`, modelled on the existing `CameraMixin`.

### 4.7 Upgrades (cut item 7 beyond seats and hold)

Fitted on the ship screen's **Status** tab from items made at the Engineering Bench:

| Upgrade | Effect | Schematic (7.3) |
|---|---|---|
| `passenger_seat` | +1 seat (3 passengers) | Armstech 2 |
| `cargo_bay_extension` | Hold 27 → 36 | Armourcraft 2 |
| `reinforced_plating` | Wear per jump 5 → 3 | Armourcraft 3 |
| `fuel_tank_extension` | Tank 4 → 6 | Artifice 2 |

Each upgrade fits once; removing it returns the item.

### 4.8 Landing beacon and recall

- `landing_beacon` (block): a squat post with a blinking light. Crafted from the common schematic (2 durasteel, 1 redstone lamp, 1 iron ingot). **One per player:** placing a second one moves the home pad and drops the first as an item. Only the owner can break it (the M3 unbreakable-in-survival rule for others).
- It must stand in the overworld at the edge of a clear 11×11 area with sky above (the `MeetingSites.Rules` check: max slope 2, no built blocks within the box). If the check fails, the beacon says why and is not set.
- **Recall:** the owner uses the beacon (or a hub's pad terminal) with a fuel cell in hand while their ship is somewhere else and no one is aboard. The ship flies itself to that pad: it vanishes from its current place with the take-off effects (if the chunk is loaded) and appears with the landing sequence. Recall costs 1 cell and the normal wear.
- **Tow.** A recall of a `DISABLED` ship is a tow: the ship arrives with 20 hull (the tow covers the jump's wear). One tow per in-game day is free (`TravelData.tow_day`); a second one that day is refused ("Towing again tomorrow"). The spacer at either hub also tows (dialogue action `ship:tow`): a ship `DISABLED` anywhere on that planet is brought to the pad with 20 hull, on the same daily allowance.
- **Never stranded.** Wherever the owner is, the ship can come back: the beacon at home or a pad terminal on a planet recalls it for a cell, and tows it if it is `DISABLED`; a spacer sells cells for credits; and once per in-game day the hub host recalls it for free, with no cell ("We've all been stuck somewhere"; dialogue action `ship:recall_free`). The worst case is a one-day wait for the next tow.

### 4.9 Ownership, crew and persistence

- **One ship per player.** Its state lives in the global `ShipRegistry` (owner UUID → body UUID, dimension, position, yaw, state, hull, fuel, upgrades, hold contents, crew list, hull code). The entity is a body that reads and writes the registry. A body whose UUID is not the registry's current one discards itself on load, so no copy of a ship or its hold can exist twice.
- If the body is missing (removed by `/kill`, a lost chunk, a datapack edit), the beacon, the pad terminal and `/oldrepublic ship restore <player>` place a new body from the registry at its last pad, with its hold intact.
- **Crew:** the owner adds up to 2 players (3 with the seat upgrade) on the **Crew** tab, from players within 16 blocks. Crew may board and ride; a player removed from the crew while aboard is put off the ship at once. Only the owner opens the hold, repairs, fuels, upgrades and launches. Crew lists persist. Anyone else using the ramp is told whose ship it is.
- The ship is never saved into a player's data. Vanilla's `PlayerList#remove` saves the player first and then removes a root vehicle whose only player passenger is leaving (the follow-up probe). W3 dismounts every rider in Fabric's `ServerPlayConnectionEvents.DISCONNECT`, which fires before `PlayerList#remove`, and places them beside the ship, so the ship is never saved with a rider. As built, W3 uses Fabric's `ServerPlayerEvents.LEAVE`, which fires at the head of `PlayerList#remove`, before the player is saved.
- Riders log in on the ground beside the ship. A server restart during a flight is settled at load from the record's `flight`: a ship in `IN_TRANSIT` or `LANDING` (after the jump) is placed at its destination pad, with the fuel and wear already taken; a ship in `LAUNCHING` (before the jump) goes back to its origin pad, and nothing is taken. Either way its state is then set as at Settle: `READY`, `GROUNDED` with an empty tank, or `DISABLED` below 20 hull.

### 4.10 Ship screen

Opened by the owner using the ship (crew get a read-only **Status**). Tabs: **Status** (hull, fuel, state, upgrades), **Repairs** (in `HULK` only; afterwards the tab shows the hull repair hint), **Nav** (destination list with Launch; greyed rows say why: "Locked", "Not enough fuel", "Hull below 20"), **Hold** (owner) and **Crew** (owner). The menu is a container menu with the hold as its slots; every button sends the `ship_action` payload (16.2), whose button ids are W3's own. The screen follows the M3 workbench style and size (`LightsaberWorkbenchScreen`, 272 × 204).

### 4.11 Commands

All take a single player (`@p` style) and need permission level 2:

- `/oldrepublic ship give <player>` — a repaired, fuelled ship beside the player (replaces the existing body; hold kept);
- `/oldrepublic ship hulk <player>` — place the Chapter Four hulk and cradle now;
- `/oldrepublic ship repair|fuel <player> [amount]`, `ship hull <player> <value>`;
- `/oldrepublic ship travel <player> <destination>` — run the full flight sequence (the player must be aboard), or with `instant` the jump alone;
- `/oldrepublic ship recall|restore <player>`, `ship unlock <player> <destination|all>`, `ship info <player>`.

---

## 5. Weapons

Ownership: **W4** owns blasters, vibroweapons, the electrostaff, weapon mods (item side), the heat gauge, reticle and arm poses, and the Weapon Action handling. **W5** owns the training saber, the saberstaff and the saber lens, because they live in the saber package.

The brief wants equipment to support different builds. The M4 rule: **a lightsaber stays the Force user's best weapon**, because it blocks, parries and deflects and its hits refund Force Energy. Blasters give range and steady damage but no defence; vibroweapons give a saberless or Independent melee build; the electrostaff gives reach and control.

### 5.1 Blasters: shared rules (`equipment.blaster`)

- **Ids.** `blaster_pistol` (M2) and `blaster_rifle` (M1) exist as render-only items used by NPC renderers (`notes_gear.md` A). Phase 0 swapped their class in place in `enemy/EnemyItems` to `equipment.blaster.BlasterItem` (kinds `PISTOL` and `RIFLE`) with the same ids, so the NPC renderers, held-item models and game tests keep working. `heavy_repeater` is new (W4 registers it).
- **Firing.** Use (right-click) fires; holding fires again at the weapon's rate (`onUseTick`, use duration 72000, `ItemUseAnimation.NONE` with the mod's own arm pose, 5.8). The first bolt leaves on the tick of the press, so a tap is a single shot.
- **Bolts.** The player's bolts are `BlasterBolt`s (`BlasterBolt.create(level, owner, pos, damage, life, style, heavy)`), damage type `oldrepublic:blaster`. Sabers deflect them; Push redirects them; enemies that deflect (acolytes, Kaast's last stand) can send them back. Phase 0 appended the bolt styles `PLAYER` (green-white) and `MANDALORIAN` (gold) with their renderer palettes. `enemy/BlasterBolt.java` stays a lead file; the lead adds the player-bolt rules below (the repeater's piercing, passing the shooter's allies) on W4's request.
- **Aim.** Spread is the cone half-angle: hip fire uses the weapon's spread; **sneaking steadies aim** (spread × 0.35 and the reticle tightens) at the vanilla sneak speed. Moving faster than walking doubles the spread; airborne triples it.
- **Heat.** Each shot adds heat (0–100). Heat falls by the weapon's cooling rate once 16 ticks have passed since the last shot. At 100 the blaster **overheats**: it cannot fire for the overheat lock, vents steam, and its heat falls to 0 over that time.
- **Vent (Weapon Action key, 11.1).** At 30 heat or more, pressing Weapon Action vents to 0 with a short lock (the vent lock) and a hiss; below 30 it does nothing. Venting at 70–90 heat is a **perfect vent**: the lock is halved and the next 3 shots add no heat (a small skill reward with a clear tell: the gauge's band glows white between 70 and 90).
- **Heat storage.** Heat, the last shot tick and the lock end are an item component (`oldrepublic:blaster_heat`), so swapping guns is a choice. To stop the hand re-equip bob each time the component changes, `BlasterItem` returns false from Fabric's `FabricItem#allowComponentsUpdateAnimation` (confirmed by the follow-up probe).
- **No Force refund.** Blaster hits and kills never refund Force Energy and never count as a "power hit" or "technique".
- **Allies and PvP.** Player bolts pass through the shooter's crew and allied entities (`Allegiances.friendly`); against players they follow server PvP and team rules (the M2 rule).
- **Melee.** Attacking with a blaster is a weak bash (2 damage, attack speed 1.0) that staggers for 6 ticks; a pistol whip lets a gunslinger answer a melee rush.

| | Blaster pistol | Blaster rifle | Heavy repeater |
|---|---|---|---|
| Hands | one (offhand free; the offhand may hold a torch, a shield or a medpac) | two (offhand item is hidden and unusable while aiming) | two |
| Damage per bolt | 4.0 | 5.0 | 2.5 |
| Rate (ticks between shots) | 8 | 10 | 3, after a 12-tick spin-up (again after each vent) |
| Speed (blocks per tick) / life | 2.2 / 40 | 2.6 / 60 | 2.4 / 50 |
| Hip spread / steadied | 2.0° / 0.7° | 2.5° / 0.9° | 4.0° / 1.4° |
| Heat per shot / cooling per tick | 15 / 2.5 | 14 / 2.0 | 7 / 1.5 |
| Overheat lock / vent lock | 50 / 20 | 60 / 25 | 80 / 30 |
| Movement while firing | normal | −10% | −30%, no sprinting |
| Special | Quick draw: the first shot after switching to it fires 4 ticks sooner | **Charged shot**: hold Weapon Action for 30 ticks (no vent) to load one heavy bolt (12 damage, `heavy` true, so only a parry turns it; 35 heat) | Bolts pierce one target; suppresses: a mob hit 4 times in 40 ticks is slowed (`ControlEffects.SLOW`, 30 ticks) |
| Durability | 600 | 800 | 900 |
| Source | Armstech 1 schematic, outpost quartermasters (300 credits), Mandalorian hunter drops (rare) | Armstech 2, quartermasters (500 at Trusted) | Armstech 4 only |

Sustained output with perfect vents is about 9.7 damage per second for the pistol, 9.8 for the rifle and 10.3 for the repeater (about 7.5 with plain vents), against 11.2 for an Unformed saber at level 12 (7 × 1.6); 14.2 gives the model. The repeater's burst is higher (16.7 until the first vent), but over a fight the saber stays ahead. Bolts travel and miss, and a blaster has no guard, so a saber is also safer in close combat.

### 5.2 Vibroweapons (`equipment.melee`)

| | Vibroblade | Vibrosword |
|---|---|---|
| Id | `vibroblade` (existing; class swapped to `VibroWeaponItem` like the blasters) | `vibrosword` (new) |
| Damage / attack speed | 6 / 1.8 | 8.5 / 1.2 |
| Reach | vanilla | +0.5 (the attack range component, set with `Item.Properties#component`; the probe shows no `attackRange` builder, and the `DataComponents` field name is `// API-CHECK`) |
| Guard | Right-click raises a **guard**: melee damage from the front −50%, consuming the M2 guard stamina; no projectile block, no parry | the same, −65% |
| Special | **Flurry**: every third consecutive hit within 30 ticks deals +3 and does not reset the attack cooldown | **Cleave**: a fully charged attack also hits up to 2 more enemies within 2 blocks in front (60% damage) |
| Against sabers | A saber parry against a vibroweapon costs it 5 extra durability ("the blade notches") | same |
| Durability | 500 | 700 |
| Source | Armstech 1; dropped by M2 Poacher Captains (now usable; their loot table gains it, 12.5) | Armstech 3 |

### 5.3 Electrostaff (cut item 2)

`electrostaff`: two-handed staff with shock emitters at both ends.

- Damage 6.5, attack speed 1.1, reach +1.0. Attacks sweep (the vanilla sweep pattern, with its own swing animation through the `SwingAnimation` component, confirmed by the probe and set with `Item.Properties#component`).
- **Charge (Weapon Action):** 40 ticks of rising crackle; the next hit within 100 ticks shocks (`ControlEffects.SHOCK`, 20 ticks) and arcs to one more enemy within 3 blocks. Droids take ×1.5 from shocks.
- **Guard:** like the vibrosword (−60% from the front). It is the one non-saber weapon that can **block a saber** without losing durability (the staff's emitters hold the blade).
- Source: Armstech 3 schematic; the Proving's quartermaster at Honoured.

### 5.4 Weapon quality and mods

Crafted weapons have a quality (7.4): Standard, Fine or Masterwork. Quality multiplies damage by 1.00 / 1.06 / 1.12 and durability by 1.0 / 1.25 / 1.5. Bought and dropped weapons are Standard, except named drops.

Blasters have **two mod slots**: **barrel** and **cell**. Vibroweapons and the electrostaff have one: **edge**. Mods are items crafted by Armstech (6.3 has the table) and fitted at the Engineering Bench (7.1).

### 5.5 Training saber (cut item 6)

- `training_saber`: a `LightsaberItem` subclass with a fixed config (no crystal; a low amber training emitter) that cannot be taken apart.
- Damage 2.5; it never kills a player (stops them at 1 health) and never sets fire. It blocks, parries and deflects like a lightsaber and uses the same guard, at the same costs.
- Purpose: a saber for players who have not built one or who duel friends, and the apprentices' sparring at the Greenwatch (a short "Spar with the apprentices" duty, through the M2 `Spar` rules, 8.7).
- Source: the Watch quartermaster (150 credits); the Proving sells a red-tinted one.

### 5.6 The saberstaff

A double-bladed saber, built at the **Lightsaber Workbench** from a second crystal (8.4).

- **Item:** `lightsaber_staff`, a `SaberStaffItem` (a `LightsaberItem` subclass, so the ~20 `instanceof LightsaberItem` checks keep working, `notes_gear.md` B). It uses the frozen `SaberConfig` unchanged: the optional `second` holds the second crystal (Phase 0 added it with `optionalFieldOf("second")`; existing sabers decode unchanged).
- **Build:** the workbench gets a **Staff** toggle when a `grip_staff` (the staff grip, `Grip.STAFF`; Artifice 2, or bought from the Watch or Imperial quartermaster at Trusted) is in the grip slot. The staff takes two crystals (`crystal` and `second`), one emitter part, which is fitted at both ends, and the grip. The pommel slot is locked; the config keeps its pommel, which the staff does not render. Both blades show the build's chosen `color` (the free M1 workbench choice); there is no split colour in M4.
- **Stats (relative to a saber with the same parts):** damage −10% per hit; attack speed +0.2; reach +0.5; guard stamina drain +20% per block; parry window −1 tick; deflection cone +15° behind the player (the back blade).
- **Techniques:**
  - **Whirl** (replaces the saber heavy attack while a staff is held): a spinning strike that hits every enemy within 2.5 blocks for 75% damage each, 14-tick wind-up, 20-tick recovery;
  - **Single blade** (Weapon Action): ignites or retracts the second blade, for a normal saber's damage and parry window;
  - the saber throw throws the staff on a wider, slower arc (hits up to 3 targets).
- Forms (Soresu, Ataru) apply on top. The M2 and M3 saber nodes all work with it.

### 5.7 Saber lens (Artifice)

`SaberConfig` gains an optional `lens`. A lens slot appears in the workbench when the player has Artifice 2 (or holds a lens). Lenses are made by Artifice (7.6):

| Lens | Effect while ignited |
|---|---|
| `lens_honed` | +6% saber damage (Fine +9%, Masterwork +12%) |
| `lens_steady` | parry window +1 tick (Masterwork +2, still capped at 11) |
| `lens_resonant` | Force power damage +5% (Masterwork +8%) |

Crystals stay cosmetic (the M1 rule), so M4 has no crystal tuning; it is left for later (1).

### 5.8 Poses, reticle and hit feedback (W4, client)

- **Arm poses** (through the existing `AvatarRenderer` mixin): pistol, one arm raised while firing and for 20 ticks after; rifle and repeater, both arms, stock to shoulder; relaxed otherwise. First person: a small recoil kick and a muzzle flash sprite at the barrel (no screen flash).
- **Reticle:** while a blaster is held, the vanilla crosshair is replaced by four short ticks whose gap shows the current spread, with a hit marker (a brief X) on a confirmed hit and a different marker on a deflected shot.
- **Heat gauge:** 11.2.

---

## 6. Armour and mods

Ownership: **W4** owns the sets, their traits and rendering, the mod items' effects and the jet pack. **W5** owns the schematics that make them and the bench that fits them.

### 6.1 How M4 armour extends M3

These three changes are W4's, made with its sets, not Phase 0's: appending to `ArmourSet` breaks every exhaustive switch over it, and those switches are in W4's own files (`ArmourItem`, `ArmourSets`, `EquipmentItems`, `SetBonusToast`, `EquipmentEffectsClient`, `EquipmentGameTests`).

- `ArmourSet` (by ordinal, append only) gains `WATCH_BATTLE`, `SITH_WAR`, `MANDALORIAN`, `SPACER`.
- `SetBonusRules.Worn` is a fixed record of four counts. W4 replaces its body with a count per `ArmourSet` ordinal (an `int[]` sized from the enum) and keeps the four M3 accessors, so M3 set bonuses compile unchanged (test `setBonusWornCountsEveryOrdinal`, in `GearGameTests`).
- Traits are the M3 `EquipmentTraits` record (cost, regen, max energy, blaster and melee resist) plus an optional nested `Extra` record for M4 traits: `speedPct`, `controlPct` (shortens control effects on the wearer), `fallPct`. Old stacks decode with `Extra` absent.
- Plate sets render as vanilla equipment layers (64 × 32); the Watch battle armour's tabard and the Sith war armour's cape use the M3 robe model path (`RobeArmorRenderer`), so `ClientConfig.robeModels` covers them.

### 6.2 The sets

Defence values are per piece (helmet, chest, legs, boots). Toughness and knockback resistance are per piece. Traits are per piece unless marked "set".

| | Watch battle armour | Sith war armour | Mandalorian armour | Spacer's gear (cut item 4) |
|---|---|---|---|---|
| Look | Watch green and bone plate over a hooded tabard, leaf-cut pauldrons, a face-open helm with a high collar | Black-red plate with bronze edging, a torn cape, a horned half-helm | Hunter's plate with a T-visor helmet (original shape, not a copy of a film helmet), worn paint, a range-finder on the helmet, kama on the belt | Padded jacket, belt pouches, a flight cap with goggles, boots with buckles |
| Defence | 2 / 5 / 4 / 2 (13) | 3 / 6 / 5 / 2 (16) | 3 / 7 / 6 / 3 (19) | 1 / 4 / 3 / 1 (9) |
| Toughness / knockback | 1 / 0 | 1 / 0.05 | 2 / 0.1 | 0 / 0 |
| Traits | Force cost −3%, melee resist +4% | Max energy +4, melee resist +5%, regen −3% | Blaster resist +6%, Force cost +5%, speed −3% | Speed +2%, Force regen +4%, fall damage −10% |
| Set bonus (4 pieces) | **Warden's Resolve** (not "Guardian's Resolve", which is an M2 Jedi node): after a parry, melee resist +15% and Barrier costs −20% for 80 ticks | **Wrath Unbound:** below half health, Choke and Lightning cost −20% and deal +10% | **Hunter's Tenacity:** control effects on the wearer are 30% shorter; the jet pack has one more charge; Force Speed costs +10% | **Light on Your Feet:** Force Speed lasts +30%; fall damage −50% (with the pieces); never slowed by ash or soul sand |
| Alignment rule | The M3 gate (`SetBonusRules.jediGate`): alignment ≥ +15 (`setAlignment`), or an Independent in the Balanced band. A wearer without Barrier still gets the melee resist | The M3 gate (`sithGate`): alignment ≤ −15, or an Independent in the Balanced band. A wearer without Choke or Lightning gets nothing from those parts | none | none |
| Durability multiplier / enchantability | 16 / 12 | 17 / 10 | 22 / 8 | 9 / 16 |
| Source | Armourcraft 3 schematic item (Watch quartermaster, Trusted with the Jedi Order); finished Standard pieces from the same vendor; or a trainer commission (7.8) | Armourcraft 3 schematic item (Imperial quartermaster, Trusted with the Sith Empire); finished pieces; or a commission | Kaast's pattern (the spare ending, 8.6) or a schematic from rare hunter drops (8% per hunter, guaranteed by the 10th); Armourcraft 4, or a commission | Armourcraft 1; finished pieces from spacers at both hubs |
| Materials | `alloy_plate`, `aurelite_shard`, `lumen_thread` | `alloy_plate`, `duskglass_shard`, `grave_silk` | `alloy_plate` ×2 per piece, `hollowhorn_hide` or `ash_hound_fang` | leather, `hollowhorn_hide`, string |

`alloy_plate` is an Armourcraft 2 intermediate: 2 durasteel and 1 aurelite or duskglass shard make 2 plates. It ties both planets into the late sets without forcing a second world.

**Heavy armour and the Force.** The brief asks that heavy armour cost mobility or Force efficiency. The Mandalorian set does both and the Sith war armour costs regen; the Watch set is the Jedi's compromise (light enough to cast, plated where a blade lands).

### 6.3 Mods

Every armour piece from M3 and M4 has **one mod slot**; chest pieces have **two** (one of them the **pack** slot, which only the jet pack uses). Blasters have **barrel** and **cell** slots; melee weapons one **edge** slot. Fitted mods live in the item component `oldrepublic:mods` (slot → mod item stack, at most 3 entries). Taking a mod out returns it.

| Mod | Slot | Standard / Fine / Masterwork | Discipline and level |
|---|---|---|---|
| `mod_plating` | armour | +1 armour; blaster resist +2 / +3 / +4% | Armourcraft 1 |
| `mod_weave` | armour | melee resist +2 / +3 / +4% | Armourcraft 1 |
| `mod_attunement` | armour | Force cost −2 / −3 / −4% | Artifice 2 |
| `mod_conduit` | armour | Force regen +3 / +4.5 / +6% | Artifice 2 |
| `mod_stabiliser` | armour | control effects −8 / −12 / −16% | Armourcraft 3 |
| `jet_pack` | pack (chest only) | 6.4 | Armourcraft 3 (Mandalorian hunter drop at 5%) |
| `mod_focused_barrel` | barrel | spread −20 / −30 / −40% | Armstech 1 |
| `mod_heavy_barrel` | barrel | damage +6 / +9 / +12%, rate +1 tick (slower) | Armstech 2 |
| `mod_cooling_cell` | cell | heat per shot −15 / −22 / −30% | Armstech 1 |
| `mod_overcharged_cell` | cell | damage +4 / +6 / +8%, heat per shot +10% | Armstech 3 |
| `mod_keen_edge` | edge | damage +5 / +7.5 / +10% | Armstech 1 |
| `mod_shock_edge` | edge | 8 / 12 / 16% chance to shock (20 ticks) | Armstech 3 |

All mod numbers go through the existing `DamageModifiers` and `ForceModifiers` registries and their clamps (cost 0.70–1.50, regen 0.50–1.50, damage 0.25–2.0). Mods never stack beyond those clamps.

### 6.4 The jet pack (cut item 3)

- A chest mod. Pressing **jump while airborne** fires a burst: upward velocity 0.85 plus 0.3 along the look direction, with a gout of flame and smoke at the back. 2 charges (3 with Hunter's Tenacity), each refilling 100 ticks after the wearer is back on the ground.
- If a Force Jump leap is in progress, jump goes to the leap's glide instead, so the two never fight over the key.
- A burst in the 20 ticks before landing halves fall damage.
- Floating kick: a burst is a velocity change, not flight. The longest airtime of 3 chained bursts is about 70 ticks, under the server's 80-tick floating limit; W4's client test on a dedicated server with `allow-flight=false` chains all three charges and asserts the player stays connected (17.2).
- Does not work underwater or while riding. Not usable while held (`Holds`).
- Sound: a short roar per burst, a click when empty.

---

## 7. Crafting, disciplines, schematics, credits and vendors

Ownership: **W5** owns the Engineering Bench, disciplines, schematics, quality, salvage, the Medtech, Armstech and Artifice consumables and tools, and the `crafting` attachment. **W2** owns credits, vendors, stock files and the vendor screen. Each item's effect belongs to the worker who owns that item (W4 for weapons, armour and mods).

M3 crafting is vanilla shaped recipes plus the Lightsaber Workbench. M4 adds specialisation without replacing either: vanilla recipes stay for basic items (the bench itself, durasteel blocks), and everything with a discipline or a quality goes through the bench, including the common schematics such as the landing beacon (7.2).

### 7.1 The Engineering Bench

- Block `engineering_bench` (a worktop with a vice, a welding arm and a parts rack). Vanilla shaped recipe: 3 `durasteel_plate`, 1 crafting table, 2 iron ingots, 1 redstone. The bench schematic in Chapter Four (8.2) is a journal unlock that also gives the recipe in the recipe book.
- Screen (`EngineeringBenchScreen`, 272 × 204, the size and style of the M3 `LightsaberWorkbenchScreen`) with four tabs:
  - **Craft:** the list of known schematics the player can make here, filtered by discipline, with inputs (have / need), output, quality odds and discipline XP. Craft takes the inputs from the player's inventory and the bench's 9-slot parts tray, runs for the schematic's `craft_ticks` (the arm welds; moving away more than 6 blocks cancels with no loss) and puts the result in the output slot.
  - **Modify:** one gear slot and its mod sockets (6.3). Fitting or removing is instant.
  - **Salvage:** one input slot; shows what salvaging returns (7.5).
  - **Disciplines:** the player's two disciplines, levels, XP bars and the next unlocks. Choosing a discipline, the first and the second, happens here and nowhere else.
- Shared: anyone can use any bench. The parts tray belongs to the bench (like a chest); the output slot is per use and empties back to the player if the screen closes.

### 7.2 Disciplines

| Discipline | Makes | Trainer |
|---|---|---|
| **Armstech** | blasters, vibroweapons, the electrostaff, weapon mods, player grenades, ship seats | Proving |
| **Armourcraft** | the M4 armour sets, armour plating and weave mods, `alloy_plate`, the jet pack, ship hull upgrades | Proving |
| **Artifice** | saber lenses, `grip_staff` and hilt parts, attunement and conduit mods, the fuel tank upgrade | Greenwatch |
| **Medtech** | medpacs, stims, kolto, the fusion cutter, the scanner | Greenwatch |

Trainers serve players of every path (7.8). A Jedi reaches the Proving's trainers once Varkhast unlocks at the end of the chapter, and a Sith the Greenwatch's; nothing in the chapter needs a trainer.

- A player has **two** disciplines. The first is chosen at the bench in Chapter Four. The second opens at discipline level 2 in the first, or at player level 13, whichever comes first, and is also chosen at a bench.
- **Common schematics** need no discipline: the power core, nav computer, hyperfuel cells, landing beacon, `credit_bundle` exchange (7.7) and the repair kit.
- Levels 1 to 5. Discipline XP to reach each level: 0, 120, 360, 800, 1,600. Each craft gives the schematic's XP (Standard 1.0×; schematics two or more levels below the player's give 0.25×, so grinding low items stalls).
- **First craft of each schematic** also gives player XP: 30 × schematic level (14.1).
- **Unlearning:** a trainer resets one discipline for 500 credits. Its level and XP are lost, its learned schematics are kept but locked until the discipline is chosen again, which starts again at level 1. This is the only way to change disciplines, and it is a clear choice with a cost, so it is never accidental.

### 7.3 Schematics

- Data files `data/oldrepublic/schematic/<id>.json`, loaded by a server reload listener (W5) and synced to clients on join and reload (the M2 skill-tree pattern). The comments below explain the fields; real files have none:

```json
{
  "discipline": "armstech",        // or "common"
  "level": 2,                      // discipline level needed
  "inputs": [ { "item": "oldrepublic:durasteel_plate", "count": 4 }, { "tag": "c:ingots/iron", "count": 2 } ],
  "output": { "item": "oldrepublic:blaster_rifle", "count": 1 },
  "quality": true,                 // rolls Standard / Fine / Masterwork
  "craft_ticks": 60,
  "xp": 40,
  "learned": "auto"                // "auto" at the level, "item" from a schematic item, or "trainer"
}
```

- **Learning:** `auto` schematics are known at their discipline level. `item` schematics come from `schematic` items (one item id with a component naming the schematic): loot, hunter drops, story rewards. Using one teaches it if the player has the discipline (otherwise it says which discipline it needs and is kept). `trainer` schematics are sold by trainers (7.8). Known schematics are stored per player (12.1).
- Section 16 lists every schematic id with its discipline, level and learning kind. W5 sets the inputs in the data files from the materials named in sections 4 to 7. Two creature drops feed named schematics: `hollowhorn_horn` goes into `lens_resonant`, and `droid_actuator` into `heavy_repeater` and `mod_overcharged_cell`.
- The schematic, vendor stock and mission loaders skip an entry whose item id is not registered, with a warning, so the shared tree loads while another worker's items do not exist yet (15.1).

### 7.4 Quality and bad-luck protection

For schematics with `quality: true`:

- Odds at the schematic's level: Fine 18%, Masterwork 3%. Each discipline level above the schematic's adds Fine +6% and Masterwork +3% (so a level 5 crafter making a level 2 item has Fine 36%, Masterwork 12%).
- **Bad-luck protection:** every quality craft below Masterwork adds 1 to a per-discipline pity counter; at 10 the next quality craft is Masterwork and the counter resets. The Craft tab shows "Masterwork guaranteed in N crafts".
- Quality is an item component (`oldrepublic:quality`, 0, 1, 2) and shows in the name colour and tooltip. Quality multipliers: weapons 5.4; armour traits and defence ×1.00 / 1.15 / 1.30 (defence rounded down; durability ×1.0 / 1.25 / 1.5); mods per their table (6.3).
- Quality never changes a registered value. The craft writes the stack's own `ATTRIBUTE_MODIFIERS` (damage, defence) and `MAX_DAMAGE` components from the multipliers. The multipliers live in the `Quality` enum (`weapon()`, `armour()`, `durability()`), which W5's `CraftingBalance` fills on load through `Quality.configure`.

### 7.5 Salvage

- Salvaging a crafted or bought weapon, armour piece or mod returns 50% of its schematic's inputs (rounded down, at least 1 of the main input), plus any fitted mods unchanged. A Masterwork item also returns 1 `masterwork_fragment` (a component used in level 5 schematics).
- Unrepairable story items, named drops and M3 rank 3 robes cannot be salvaged.
- Salvage gives a quarter of the schematic's discipline XP if the player has that discipline.

### 7.6 Consumables and tools

| Item | Discipline | Effect |
|---|---|---|
| `medpac` | Medtech 1 | Use (16 ticks): heal 8 over 40 ticks. Shared cooldown with every healing item: 200 ticks. Interrupted by a hit of 4 or more while using |
| `kolto_injector` | Medtech 3 | Use (10 ticks): heal 6 at once and remove poison and wither; cooldown 300 |
| `combat_stim` | Medtech 2 | 1,200 ticks: melee and blaster damage +8%. One stim at a time; a stim crash afterwards (Weakness I, 200 ticks) |
| `focus_stim` | Medtech 2 | 1,200 ticks: Force regen +20% (through `ForceModifiers`); crash: regen −20% for 200 ticks |
| `fusion_cutter` | Medtech 1 (also sold by spacers) | A tool: mines `wreck_salvage` and durasteel blocks quickly; cuts **sealed plating** (W1's greater tomb crypts, 3.4) in 60 ticks of use; 250 durability |
| `scanner` | Medtech 2 | Use: a 48-block survey pulse that marks planet resources and creatures for 200 ticks (outline-free: small hovering dust motes and an arrow on the HUD edge); a contract's `survey` objective counts it. Cooldown 400 |
| `frag_grenade` (a new item; M3 has only the `frag_grenade` entity and damage type, which it throws) | Armstech 2 | Throw (use): spawns the M3 `frag_grenade` entity with the M3 fuse and push rules, but player damage 7 and radius 3; 4 per stack; 60-tick cooldown. `enemy/trooper/FragGrenade.java` stays a lead file; the lead adds the player-owned throw on W5's request |
| `stun_grenade` | Armstech 3 | Throw: no damage; staggers everything within 3 blocks for 30 ticks (droids 60); reveals cloaked or hiding targets |
| `repair_kit` | common | Use on a held weapon or worn armour piece: restores 25% durability; 3 uses |
| Saber lenses (5.7), `grip_staff` | Artifice 2 | |

### 7.7 Credits

- **Items:** `credit_chit` (1 credit, stacks to 99) and `credit_bundle` (100 credits, stacks to 64), both registered in Phase 0's `registry/M4Items`. A bench or any vendor changes 100 chits into a bundle and back.
- Vendors take payment from the player's inventory (main inventory, hotbar and the ship hold if the ship's body is loaded within 32 blocks, `Credits.HOLD_RANGE`) and give exact change. The vendor screen shows the player's total.
- **Earning credits (defaults, 14):**
  - planet and outpost contracts: 25 + 5 × level credits each, at full pay (the M3 daily soft cap applies; capped contracts pay no credits);
  - story missions: 150 for each arc mission, 100 for "A Part Off the Books" (8.2, 8.4);
  - enemies: troopers, Mandalorians and droids drop 1–6 chits at 50% (`vendors.creditDropChance`, `vendors.creditDrop`; W2 adds the drop on death), never from natural patrols (the M3 anti-farm rule);
  - selling to vendors (7.8) at 20% of the buy price;
  - Kaast's camp and the Chapter Four rewards (8.6).
- **Converted "credits".** Earlier loot used iron nuggets as credits in poacher drops (`entities/crystal_poacher`, `entities/poacher_captain`). The lead changes those loot tables to `credit_chit`s (they are lead files, 12.5 and 15.2); nuggets already in inventories stay nuggets.

### 7.8 Vendors

| Vendor | Where | Sells | Gate |
|---|---|---|---|
| Watch quartermaster | Greenwatch | Watch battle armour pieces and their schematic item, training saber, `grip_staff`, Medtech and Artifice components, silverleaf seeds | Jedi Order tier (Trusted for armour, its schematic and `grip_staff`); Sith refused |
| Imperial quartermaster | Proving | Sith war armour pieces and their schematic item, red-tinted training saber, `grip_staff`, the electrostaff (Honoured), Armstech and Armourcraft components, ashroot | Sith Empire tier (Trusted for armour, its schematic and `grip_staff`); Jedi refused |
| Discipline trainers (Armstech, Armourcraft at the Proving; Medtech, Artifice at the Greenwatch) | hubs | `trainer` schematics of their discipline (its level 5 recipes), unlearning, **commissions** | none: trainers teach a trade, not a side, and serve every path |
| Spacer | both hubs | fuel cells, ship parts and upgrades (finished items, at a premium), repairs, the tow (4.8), spacer's gear, the fusion cutter | none |
| Outpost requisition (M3 officers' new dialogue option, no new entity) | M3 outposts | blaster pistol and rifle, medpacs, fuel cells, hyperdrive motivator, frag grenades | the army's tier (the M3 attitude table; Wary refuses) |

- **Stock** is data: `data/oldrepublic/vendor/<id>.json` lists entries `{item, price, tier, discipline?, level?, daily_limit?}` (the `VendorEntry` record). Prices scale ×0.9 at Honoured with the vendor's faction, the highest tier (M3 14.1). This includes the motivator at outpost requisition: 400 credits, 360 at Honoured.
- **Commissions:** a trainer crafts any schematic of their discipline that the player knows or carries as a schematic item or pattern (`kaast_pattern` counts), at Standard quality, for the inputs plus a fee (50 × schematic level credits). This lets a player without the discipline still get the gear, at a cost and without the quality roll.
- The vendor screen (W2): a scrolling stock list with icon, price, gate reason when locked ("Needs Trusted with the Jedi Order"), a Buy button with a quantity, and a Sell tab for salvage-type items. All purchases are server-validated (price, tier, stock, inventory space) through the `vendor_buy` payload (16.2).
- Vendors are persistent hub NPCs (3.5): invulnerable to players and mobs, never targeted by area powers (`Attitudes.shieldedFromArea`), and Peaceful-safe.

---

## 8. Chapter Four: "Stars to Steer By"

The quest worker (W2) owns this chapter. Its state lives in a new `Chapter4` record inside `StoryData` (`optionalFieldOf("chapter4")`; nested records keep every codec under 16 fields). Other workers read it only through the frozen accessor `quest.story.ChapterFour` (`stage(p)`, `atLeast(p, stage)`, `arcPlanet(p)`, `clues(p)`, `hasClue(p, n)`, and the one write, `addClue(p, n)`; 15.1). Planet sites are found with `PlanetSites.find` (W1). `ChapterThreeStage` stays at `COMPLETE`. Stages only move forward, and every reward is protected by a one-time flag or by the stage change.

### 8.1 Stages (`ChapterFourStage`, stored by ordinal, append only)

| Stage | Enter when | Objective (journal) | Leaves when |
|---|---|---|---|
| `NOT_STARTED` | default | | Chapter Three `COMPLETE` (old saves on join included) |
| `MARKED` | above | (none shown) | 24,000 ticks after entering, or waking from sleep, at a safe moment (the M2 rule), on the home world, with no hostile within 24 blocks |
| `SEEKER` | the seeker droid scene plays (8.2) | "Someone has put a price on you" | the mentor talk is done (Independent: the journal is read at Courier's Fall) |
| `REPAIRS` | the hulk and cradle are placed (4.2); the bench schematic is unlocked | "Make the ship fly (n/4)", with the four parts listed | all four parts fitted and at least 1 fuel cell in the tank |
| `FIRST_FLIGHT` | the ship becomes `READY` | Jedi: "Fly to Selvaren"; Sith: "Fly to Varkhast"; Independent: "Choose a world" | the owner lands anywhere on their path world (Independent: either), as reported by `QuestHooks.onPlanetArrived`; a bay, an overflow spot or a hyperspace fallback all count |
| `WORLD_ARC` | arrival | the arc's objectives (8.4) | the arc's power is learned **and** its second-crystal expedition is done or skipped by choice (8.4) |
| `HUNTED` | on leaving `WORLD_ARC`, and only then | "Find out who hired the hunter (n/3)", counting clues already held | 3 clues (8.5) **and** level ≥ 14 (`duelLevel`); below the level the journal says "You are not ready (level 14)" |
| `CHALLENGED` | from `HUNTED` (so the world arc is done) | "Answer the hunter's challenge (recommended level 15)" | the owner enters the hunting camp's ring |
| `DUEL` | | "Face Torvin Kaast" | Kaast yields and the owner chooses (8.6) |
| `AFTERMATH` | | "Report to …" | the report is done |
| `COMPLETE` | | the ongoing loop (8.7) | |

Steps that can happen out of order are flags, not stages, so nothing is stuck and the stage never moves backwards. From `FIRST_FLIGHT` on, whatever the stage, the sabotage, both ambushes and the three clues fire on their own triggers (8.5) and are recorded in `Chapter4.hunt` (`sabotage_done`, `ambush_day`, `ambushes`, the `clues` mask). `HUNTED` begins only when `WORLD_ARC` ends and counts the clues already held; if all three are held and the player is level 14, it passes straight on to `CHALLENGED`. While `HUNTED` has no clue yet, the Calling points home, where the sabotage waits. A power or crystal already owned when its step begins completes that step at once (the M3 rule).

### 8.2 The mark, the briefing and the repairs

**The seeker droid.** A small flying droid (`seeker_droid`, invulnerable, `noSave`, never targetable) descends 20 blocks from the player, hovers, sweeps them with a red scan fan for 40 ticks (a thin beam of dust motes lit red, no outline), chirps twice, and climbs away fast. If the player hits it, it dodges and leaves sooner; it never fights. Journal entry `a_price`, codex `bounty_hunters`.

| Path | Briefing | Rewards |
|---|---|---|
| Jedi | Saelen (`c4_hub_saelen`, "A droid scanned me."): she knows the make, a Mandalorian hunter's. "A frontier is a small place to hide, and a smaller one to fight in." The Watch's courier, the *Patient Star*, has sat under a tarp since before the Treaty; she gives the player Selvaren's coordinates from the Archive (or from her memory if the Archive was kept or unmade, a line that changes) | 300 XP, the hulk and cradle, the bench unlock, entry `the_patient_star` |
| Sith | Sorn: "Someone thinks you are worth money. That is either an insult or a compliment." He has requisitioned a written-off Imperial courier and the coordinates of Varkhast, "where the Empire keeps its oldest dead" | 300 XP, the same, entry `a_written_off_courier` |
| Independent | The journal opens to Vasch's courier. At Courier's Fall the wreck's ramp is clear, and its nav log names two worlds and the spot nearby where Vasch laid up a spare courier under a tarp. Tessaly Arn's voice from the Compass says "You were always going to leave." | 300 XP, the same, entry `vaschs_courier` |

Everyone then chooses a first **discipline** at an Engineering Bench (the briefing gives one bench as an item, so no player has to find durasteel first). The choice gives entry `a_trade` and the discipline's first two `auto` schematics.

**The four parts** (4.2). Each fitted part gives 300 XP once. The motivator has three routes:

- **Salvage:** any Wreck Field. The first time a Chapter Four player in `REPAIRS` casts Sense within 8 blocks of a Wreck Field's `HEART` anchor (found with `StructureLocator.anchor`, the M3 `survey` check), the Sense reveals a motivator still in its cradle among the wreckage, and it drops at the player's feet (a per-player flag). This works in Wreck Fields that M3 worlds have already generated and looted, because nothing in the template changes.
- **Requisition:** an outpost officer above Wary sells one (400 credits; 360 at Honoured, 7.8). Players start Chapter Four with 100–300 credits of M3 back pay (8.8), so this usually needs a contract or two.
- **"A Part Off the Books"** (story mission, kind `story`, W2): a smuggler at either outpost knows where a crashed freighter's motivator is hidden; the player recovers it from a poacher-held camp (`recover` objective; the M3 `raider_camp` template). 1,200 XP, 100 credits.

**Lift-off.** When the ship stands up, the mentor's line plays (or Tessaly's), and the first fuel cells are already in the hold (4.3). Entry `the_ship_flies`.

### 8.3 First flight

The owner flies to their path world (4.6). On landing: banner "Selvaren" or "Varkhast" (planet subtitle: "The Watch's first home" / "Where the Empire keeps its dead"), discovery XP (400 for the planet, 200 for the hub), entry `selvaren` or `varkhast`, codex `selvaren` / `varkhast`.

The hub host meets the player at the pad (a scripted walk-up, then dialogue `c4_ansa_meet` or `c4_kaddor_meet`).

- **Jedi** meet Keeper Ansa Veylin, who knew Saelen as an apprentice and is surprised she is alive.
- **Sith** meet Overseer Vael Kaddor, who has heard of the Collector's end and wants to see what Sorn has sent.
- **Independents** choose a world on the nav console. The host there is guarded: Neutral treatment, full service, and a line about being "no one's student". An Independent may fly to the other world at any time; the second hub greets them the same way.

### 8.4 The world arcs

Each arc is two story missions (data JSON, W2) and an expedition.

**Jedi on Selvaren**

| Step | Task | Rewards |
|---|---|---|
| "The Greenwatch" (mission) | Ansa asks for help: corruption is spreading from the **Heartstone**, the great waystone the enclave was built to keep. Visit 2 corrupted Watch waystones (worldgen, the Calling points to the nearest two) and cleanse them: clear their twisted hollowhorns (`clear`) and touch the stone with Sense (a `survey` objective: Sense cast within 8 blocks of the stone's anchor, the M3 objective with `WAYSTONE` added to its site kinds). | 1,500 XP, 150 credits, Jedi Order +40, entry `the_greenwatch` |
| "The Heartstone" (mission) | At the Heartstone, a **cleansing**: the player stands in the stone's ring and keeps the corruption back for 90 seconds (`secure`) while dark-veined hollowhorns (W6's corrupted hollowhorn variant) and M3 Watcher sentinels the corruption has woken (`ruin_sentinel`, `WATCHER` variant, unchanged) come in three waves, at 0, 30 and 60 seconds. Ansa's teaching comes through the stone: at the 45-second mark, between the second and third waves, the player is granted **Force Barrier** (slotted by the rule in 9) and the HUD says "Raise Barrier [key]". The cleansing completes when the 90 seconds are over **and** the player has raised Barrier at least once since the third wave began, for any length. If they have not, the stone waits, the hint stays, and the cleansing completes on the next raise. | 2,000 XP, 150 credits, `force_barrier` granted, trust +10, Jedi Order +60, entry `the_heartstone`, codex `force_barrier` |
| The Singing Hollow (expedition) | The Hollow is placed for the player 300–450 blocks out (3.4). Inside: a descending grotto where aurelite veins glow when crystals sing; a three-chime tone puzzle (Sense reveals which crystal clusters are humming; Push the matching hanging stones in order, using the M3 puzzle kit's counterweight block); two hollowhorn bulls at the mouth; and at the bottom a crystal that answers. | 2,000 XP, 1 SP, a second crystal (blue or green natural hue, the player's choice; attuned to the player, the M1 rule; the hue is cosmetic), entry `the_singing_hollow` |

**Sith on Varkhast**

| Step | Task | Rewards |
|---|---|---|
| "The Proving" (mission) | Kaddor sets the first trial: bring back a relic (`tomb_relic`) from a **greater tomb** (worldgen; `recover`), past Imperial diggers who are not told to let the player through (they are Wary, and the player chooses how to deal with them) and the tomb's sentinels. | 1,500 XP, 150 credits, Sith Empire +40, entry `the_proving` |
| "Zarvos's Sepulchre" (mission) | Below the Proving: the sepulchre of **Lord Hadrek Zarvos** (the M3 Sith gatekeeper, now met in his own tomb). A tomb trial: survive three rooms of awakened sentinels and ash hounds (`secure` in each), then face Zarvos's echo, which seizes the player in a choke (a `Holds` hold of 40 ticks, the M3 held state) and demands they "take it from me". As the grip ends, the player is granted **Force Choke** (slotted by the rule in 9), the echo lets go ("Then take it.") and waits, untouchable by anything but Choke. A Choke that lands on the echo breaks it and ends the trial; there is no time limit. Releasing the player before the grant keeps the M3 hold rule, which lets only Push through, untouched. | 2,000 XP, 150 credits, `force_choke` granted, trust +10, Sith Empire +60, entry `zarvos_sepulchre`, codex `force_choke` |
| The duskglass rift (expedition) | A fissure 300–450 blocks out: a climb down black-glass ledges over a drop (Force Jump and Lift help; ropes and handholds exist for players without them), duskglass clusters that cut (contact damage 2), ash hound packs, and at the bottom a crystal that has to be bled: the player holds it while the rift's whispers play, and its natural hue turns red. | 2,000 XP, 1 SP, a second crystal (red natural hue; cosmetic, the M1 rule), entry `the_rift` |

**Independent (either world, first landing)**

| Step | Task | Rewards |
|---|---|---|
| "Tessaly's Road" (mission) | The Compass hums near the hub. Tessaly Arn's echo leads to a **Wayfarer cache** 150–250 blocks out (3.4), a buried brass vault with a pilgrim's mark. Inside, a short traversal: three platforms over a drop that only stay up while the player moves quickly (they sink 60 ticks after being stood on). At the centre, Tessaly teaches **Force Speed** ("Walk the long road quickly") and its Wayfarer branch. A player who already bought `force_speed` gets the SP they spent on it back before the grant (W2 reads the node's spent points and calls `Progression.grantSkillPoints`; `SkillService.grant` itself never refunds). | 2,000 XP, 150 credits, `force_speed` and `speed_wayfarer` granted, Frontier +60, entry `tessalys_road`, codex `force_speed` |
| "Both Doors" (mission) | A favour for standing, not a condition of service (Independents are served at both hubs): the other hub's host asks for help with one corrupted waystone (the Greenwatch) or one tomb relic (the Proving); the player picks at the host. | 1,500 XP, 150 credits, +40 with that faction, entry `both_doors` |
| Expedition | Either expedition, on either world (the journal offers both). | 2,000 XP, 1 SP, the crystal |

**Skipping the expedition.** A player who does not want a second crystal can tell the host "Not now" after the arc's power is learned: `WORLD_ARC` ends without it, and the expedition stays in the journal for later (it then gives its XP, SP and crystal as normal). Every expedition gives its SP once per player (`chapter_four.expeditionSp`), so the second expedition in the loop pays it too. **Saberstaff:** with two crystals, the workbench offers the staff (5.6); entry `two_blades` on first build.

### 8.5 The hunt (clues 1 to 3)

All three run on their own triggers from `FIRST_FLIGHT` on, whatever the stage (8.1).

- **Sabotage.** The first time the owner's ship lands at `home` after FIRST_FLIGHT (flown or recalled), a charge wedged in a landing strut blows as it settles: the hull drops to 10 and a hunter's mark (a scorched sigil-free symbol: three claw scratches burned into the hull paint) is left on the ramp. Only the ship's own landing triggers it, so a ship left on a planet is never sabotaged from afar. The ship cannot jump until repaired to 20 (4.4). Clue 1: Kaast's name, from a datapad beside the scorched strut. 300 XP, entry `sabotage`.
- **Ambush one.** After the sabotage, the next time the owner is on a planet surface 96 blocks or more from the hub and any site, 3 Mandalorian hunters (4 at level 14) drop in on jet packs from 24–32 blocks away (`MISSION_SITE` with `ambush_rocks`, W1). Owned by the player; despawned if they move 160 blocks away (`ambushLeaveRange`); they retry 1 in-game day later (`ambushRetryDays`) if the player fled. On defeat, clue 2: the last hunter, at 20% health, yields and says the contract is "not ours; Kaast took it from a buyer who pays in Core credits". 800 XP, entry `first_ambush`.
- **Ambush two.** At least 2 in-game days after the first, on another world than the first ambush: the other planet, or the home world (for Jedi and Sith the home world in practice, since their second planet unlocks only after the chapter): 2 hunters and a Mk IV security droid escort. Clue 3 is counted when the droid dies (`ChapterFour.addClue`, W6); it drops a `sealed_contract` signed only "the Buyer", a keepsake whose loss changes nothing. 800 XP, entry `the_buyer`, codex `the_buyer`.

The Buyer is a hook for M5; nothing in M4 names who they are.

### 8.6 The challenge, the duel and the report

- On entering `CHALLENGED` (the world arc done, 3 clues and level 14; below the level the journal says "You are not ready (level 14)", with the fastest XP sources), a **marker beacon** lands near the player with a pulse of red light and Kaast's recorded challenge. The hunting camp is placed on the path world (`HuntingCamps.place`, W6), and the `hunting_camp` destination unlocks for good (4.5). Entry `the_challenge`.
- The duel (10.5). Kaast yields at 10%.
- **The choice** (dialogue `c4_kaast_yield`, owner only):

| Choice | Scene | Rewards and costs |
|---|---|---|
| **Spare him** | He takes off his helmet, names the Buyer's broker ("a Hutt's factor in the Core, no more than that"), and leaves his armour pattern "as a debt paid". | +10 light, `kaast_pattern` (teaches the Mandalorian armour schematics to an Armourcraft player and is kept after use; for anyone it is a pattern for trainer commissions, 7.8), entry `kaast_spared` |
| **Take his armour** | He strips the plate, "Wear it better than I did," and walks into the waste. | Kaast's helmet and chest (`kaast_helmet`, `kaast_chest`: Mandalorian set, Masterwork, named), entry `kaast_stripped`. No alignment change |
| **End him** | A short, unshowy end. His rifle is left on the ground. | −10 dark, *Kaast's Longshot* (`kaast_longshot`, a named Masterwork blaster rifle with a fixed heavy barrel), entry `kaast_ended`; Jedi trust −15, Sith trust +10 |

The fight's own reward (4,000 XP, 2 SP, credits) is paid once, by 10.6, whatever the choice.

- **Report** to the mentor (Jedi: Saelen; Sith: Sorn; Independent: the Compass, from the journal). Mentor lines react to the choice. 800 XP, trust +10, Resonance `chapter_four` +300, banner `chapter4`, entry `report_c4`. Every destination unlocks.

### 8.7 The ongoing loop

- **Planet contracts** at each hub's terminal (6 templates per planet, the M3 contract rules and daily soft cap): cleanse a waystone, cull a hollowhorn herd or an ash hound pack, recover a relic, survey with the scanner, escort an apprentice or acolyte to a waystone or tomb mouth, and spar (Greenwatch: training sabers with an apprentice; Proving: a duel in the drill yard; both under the M2 `Spar` rules). Contract XP follows the M3 formula; credits 7.7. Cleanse, cull, recover and survey use M3 objective types (`survey` gains the `WAYSTONE` kind and accepts a scanner pulse, 7.6); **`escort` and `spar` are new objective types**, which W2 writes.
- **Hunter bounties:** at most once per in-game day, while the player is on a planet surface far from a hub, a lone **Mandalorian hunter** with a contract on them may appear (10% chance per 1,200 ticks in the open, from the start of Chapter Four's `HUNTED`, and after the chapter). It drops credits, a chance at the jet pack mod and a Mandalorian armour schematic piece (bad-luck protection at 10).
- Discipline levels and Masterwork crafting; the second world's expedition; ship upgrades.

### 8.8 Old saves and edge cases

- M3 saves at Chapter Three `COMPLETE` enter `MARKED` on join.
- On their first join in M4, every player's iron nuggets are not touched, but each M3 milestone they hold (boss cleared, rank 3, each attuned holocron) pays a one-time **back pay** in credits (100 per milestone, at most 300), so nobody starts Chapter Four broke.
- If the hulk cannot be placed near the mentor (no fitting ground), it retries every 1,200 ticks on fallback rings, then places at the forward fallback 12 blocks ahead (the M2 rule).
- If a hub NPC is missing, it respawns from the hub anchors (3.5).
- Peaceful: ambushes and hunters do not spawn; their clues come by journal ("A hunter's drone left this…") on the same triggers. The duel becomes a conversation: Kaast arrives, talks, and yields at once. Tomb trials and the cleansing complete when the player reaches the end.
- A player who loses their ship or is stranded: 4.8.
- Co-op: a helper in the owner's duel gets 1,000 XP (`kaast.helperXp`) and a hunter's schematic roll, once per day (the M3 helper rule; 10.6). Other players' story steps are their own.
- Lost one-time items: 12.2.

### 8.9 Journal (Chapter Four)

Phase 0 appended these ids and the chapter header `chapter_header.4` to `JournalEntries` (append only) and added `CHAPTER_FOUR_FIRST = A_PRICE` to `CHAPTER_FIRSTS`.

- **Entries:** `a_price`, `the_patient_star`, `a_written_off_courier`, `vaschs_courier`, `a_trade`, `a_part_off_the_books`, `the_ship_flies`, `selvaren`, `varkhast`, `the_greenwatch`, `the_heartstone`, `the_singing_hollow`, `the_proving`, `zarvos_sepulchre`, `the_rift`, `tessalys_road`, `both_doors`, `two_blades`, `sabotage`, `first_ambush`, `the_buyer`, `the_challenge`, `kaast_spared`, `kaast_stripped`, `kaast_ended`, `report_c4`.
- **Codex:** `bounty_hunters`, `selvaren`, `varkhast`, `the_watch_on_selvaren`, `the_proving_codex`, `force_speed`, `force_barrier`, `force_choke`, `mandalorians`, `security_droids`, `hollowhorns`, `ash_hounds`, `disciplines`, `credits`, `starships`, `the_buyer`, `torvin_kaast`.
- The journal gains a **Crafting** tab (W5, `JournalTabs`): the two disciplines, known schematics by discipline (locked rows show where to learn them), and the Masterwork pity count.

### 8.10 Dialogue conditions and actions (registered by their owners)

| Kind | Id (argument) | Owner | Meaning |
|---|---|---|---|
| condition | `c4:stage` (stage id) | W2 | at least this stage |
| condition | `c4:clues` (n) | W2 | |
| condition | `discipline:has` (discipline id or `any`), `discipline:level` (id, n) | W5 | |
| condition | `credits` (n) | W2 | |
| condition | `ship:state` (state id), `ship:aboard` | W3 | |
| condition | `planet:in` (`selvaren` / `varkhast` / `home`) | W1 | |
| action | `c4:brief`, `c4:report`, `c4:not_now`, `c4:both_doors` (`greenwatch` / `proving`) | W2 | |
| action | `kaast:resolve` (`spare` / `strip` / `end`) | W2 | calls `KaastFight.resolve` (W6) |
| action | `vendor:open` (vendor id) | W2 | |
| action | `discipline:unlearn` (id), `trainer:commission` | W5 | the trainers' services (choosing a discipline is the bench's, 7.1) |
| action | `ship:recall_free`, `ship:tow` | W3 | the hub host's free recall and the spacer's tow, once a day each (4.8) |
| action | `nav:load` | W3 | loads the coordinates into a held nav computer |

**Dialogues added in M4** (W2 unless noted): `c4_hub_saelen`, `c4_hub_sorn`, `c4_ansa_meet`, `c4_ansa`, `c4_kaddor_meet`, `c4_kaddor`, `c4_kaast_challenge`, `c4_kaast_yield`, `c4_hunter_yield`, `c4_smuggler`, `vendor_*` (one per vendor), `trainer_*` (W2 writes the files; W5 supplies the discipline lines by request and owns their actions), `pad_terminal` (W3), `apprentice`, `acolyte` (barks), `officer_requisition` (an option added to the M3 officer dialogues).

---

## 9. Force powers and skills

Ownership: **W6** owns the three powers, their trees' nodes, mastery, effects and client visuals (the `force.speed`, `force.barrier`, `force.choke` packages, their mixins, and the M4 node additions to `skill_tree/*.json`). Three small additions land in M2 files that stay the lead's (`tools/ownership_m4.json`): the `chokeTicks` default on `ForceReactions.Handler` (9.3), already added in Phase 0 with `ForceReactions.chokeTicks(target, requested)` combining the handlers by minimum, and the `BARRIER` and `CHOKE` poses in `client/force/PowerAnimations.java` and `mixin/force/HumanoidModelMixin.java`, which the lead makes on W6's request. W6's own mixins go in `mixin/foes` (`oldrepublic-foes.mixins.json`). The M2 and M3 ability rules carry over unchanged (M3 10: the server decides; costs through `AbilityCosts` and `ForceModifiers`; cancels on death, disconnect, dimension change, a screen, a slot change; one effect event per moment; `Vfx` budgets; tells never hidden). **No M4 power changes terrain.**

**New locks** (`AbilityLocks`; Phase 0's `travel.TravelLocks`, lead): every power is refused while riding a ship that is not `READY` ("Not while flying"), and while in a vendor, bench or ship screen (already covered by the screen rule).

**Granted powers and slots.** The story grants Barrier, Choke and Speed with `SkillService.grant`, which puts a new ability in the first free slot (the M2 rule) and replaces nothing. If every open slot is full, the player gets the usual toast and an action-bar hint, "No free slot: assign Force Barrier on the Force screen [K]", and the story step that needs the power waits for it without a time limit (8.4).

### 9.1 Force Speed (`force_speed`, Universal)

| Field | Value |
|---|---|
| Purpose | Movement: cross a planet, close on a gunner, escape a pack. The brief's Universal power, and the Independent's second signature. |
| Unlock | Universal tree node at level 12, 2 SP, with no other requirement. Granted free to Independents by Tessaly's Road (8.4), with `speed_wayfarer`; the node carries `taught_by: ["independent"]`, so its tooltip warns them, and Tessaly's Road refunds an Independent who bought it anyway. |
| Input | TAP. Can be slotted. |
| Cost / cooldown | 20 FE / 600 ticks from the end |
| Effect | 160 ticks of movement speed +40% (an `ADD_MULTIPLIED_BASE` modifier `oldrepublic:force_speed`), step height +0.5, sprint jumps carry 20% further, hunger from sprinting halved. Ends early on taking a stagger, a hold, a net or a slow (`ControlEffects` kinds STAGGER, HOLD, SLOW), on entering water or riding, or on casting it again (which refunds nothing). |
| Branches (group `speed_shape`, level 13, 1 SP) | **Burst** (`speed_burst`): the first 20 ticks are +100% and the cast dashes 6 blocks along the look direction (stopped by walls; no damage). **Long Road** (`speed_long_road`): duration 300 ticks, cost +5. |
| Wayfarer branch (`speed_wayfarer`, granted, Independents only) | While Speed is up, projectiles aimed at the player miss 25% of the time (a server-side deflection of the bolt's path by up to 1.5°, never through walls), and the player takes no fall damage under 8 blocks. It is not in `speed_shape`, so an Independent can also take Burst or Long Road. |
| Mastery | +1 per 200 blocks travelled while Speed is up (counted on the server, horizontal only). Thresholds 50 / 150 / 400. Each rank: −2 FE and +20 ticks. |
| Counterplay | Nets and slows end it; Mandalorian hunters lead their shots against fast targets. |
| Animation | A lean into the stride; arms pumping slightly faster (a running pose multiplier, not a new pose). |
| VFX | Dust kicked from each footfall in a short trailing plume; faint motion streaks of bent air at the ankles and wrists; a puff of displaced air at the start and end. No glow and no after-images. |
| SFX | `force.speed.start` (a sharp breath and a whoosh), `force.speed.loop` (soft wind at the ears, caster only, stops on every cancel path), `force.speed.end` |
| Multiplayer | Effect `force_speed` (variants start, end, burst). Others see the dust and streaks. |

### 9.2 Force Barrier (`force_barrier`, Jedi signature)

| Field | Value |
|---|---|
| Purpose | Defence for Jedi: hold the line against bolts and blows, protect allies (branch), and answer heavy fire that a saber cannot (rockets, repeaters). |
| Unlock | Granted by the Heartstone (8.4). Jedi tree node, `granted_only`, `path: jedi`. |
| Input | **Hold (channel).** Can be slotted. Cannot attack or use items while held; movement slowed to walking. |
| Cost / cooldown | 15 FE to start + 6 FE per second; up to 120 ticks; cooldown 400 ticks from release |
| Effect | A sphere of bent air of radius 2 around the caster. **Bolts** (any `DeflectableProjectile`, heavy ones included) that enter it stop and drop, costing 3 FE each (heavy: 10). Other projectiles (arrows, rockets, grenades) are stopped too (rockets and grenades burst at the surface and deal 30% of their damage inside). **Melee** damage to the caster −50%; flame −50%. Releasing after at least 40 ticks sends a **pulse** that pushes enemies within 3 blocks 2 blocks away and staggers them for 10 ticks. |
| Branches (group `barrier_shape`, level 13, 1 SP) | **Shelter** (`barrier_shelter`): radius 3.5; allied players and non-hostile mobs inside are protected the same way (each bolt still costs the caster). **Return** (`barrier_return`): stopped bolts are sent back at their shooters at 60% damage instead of dropping; heavy bolts are not. |
| Mastery | +1 per 5 projectiles stopped (or 1 per 30 melee damage absorbed). Thresholds 50 / 150 / 400. Each rank: −1 FE per second and +10 ticks. |
| Counterplay | It drains fast under heavy fire; the caster cannot strike back; a Mk IV droid's sustained fire or Kaast's flamer forces it down; a hit of 10 or more in one blow breaks it (stagger 20). |
| Animation | Both arms out from the sides, palms forward, then drawn in slightly as if bracing (pose `BARRIER`, both arms, appended to `PowerAnimations.Kind` by the lead on W6's request). |
| VFX | The sphere is shown only by what it does: dust and leaves held at its surface, drifting slowly around it; bolts slowing and fading into sparks where they stop; a bent-air ripple at the point of each impact; on the pulse, the held dust thrown outward. **No dome, no shell texture, no glow.** At Reduced, impact ripples only. |
| SFX | `force.barrier.start` (a deep inhale and a pressure swell), `force.barrier.loop` (a low hum that strains as FE runs low; stops on every cancel path), `force.barrier.impact`, `force.barrier.pulse`, `force.barrier.break` |
| Multiplayer | Effects `force_barrier` (variants start, end, pulse, break) and `force_barrier_impact` (position). PvP: stops other players' bolts always (it is defence); the pulse pushes players only with PvP on. |

### 9.3 Force Choke (`force_choke`, Sith signature)

| Field | Value |
|---|---|
| Purpose | Single-target control and damage for Sith: lift a gunner out of cover, silence a caster, break a charge. The power M2 reserved. |
| Unlock | Granted by Zarvos's Sepulchre (8.4). Sith tree node, `granted_only`, `path: sith`. |
| Input | **Hold (channel).** Can be slotted. |
| Cost / cooldown | 25 FE to start + 6 FE per second; up to 60 ticks; cooldown 360 ticks from release |
| Targeting | 12 blocks, the crosshair ray then a 6° cone, line of sight; living, breathing targets (not droids, constructs or `#oldrepublic:choke_immune`) |
| Effect | The target is lifted 0.6 blocks and held (`ControlEffects.diminished(..., "hold")`, with diminishing returns), takes 2 damage every 10 ticks (force-harm damage type `force_choke`), and cannot attack or move. On release or the end, it drops and is staggered for 15 ticks. **Bosses** decide through a new default method on `ForceReactions.Handler`, `int chokeTicks(LivingEntity target, int requested)` (the default returns `requested`; Phase 0 added it, and W6 calls `ForceReactions.chokeTicks(target, requested)`, the minimum over all handlers). Kaast returns 0 in phases 1 and 2 and 20 once in phase 3, after which his flamer burns it off (10.5). **Players** (PvP option only): through `Holds` (the M3 held state), never velocity. The channel stops if the caster takes 6 or more damage in one hit or loses sight. |
| Alignment | A Choke on a hostile target: no shift. On a non-hostile or yielded target: −3 (MINOR), at most once per target per day. Jedi cannot learn it. |
| Branches (group `choke_shape`, level 13, 1 SP) | **Crush** (`choke_crush`): a hold of 40 ticks or more ends with 6 extra damage. **Dread** (`choke_dread`): non-boss enemies within 6 blocks of the target are frightened for 40 ticks (they stop attacking and back away; troopers' morale retreat). |
| Mastery | +1 per target held 20 ticks or more. Thresholds 50 / 150 / 400. Each rank: −2 FE and +1 block range. |
| Counterplay | Line of sight, one target, droids immune, interrupted by damage; Barrier does not stop it (it is not a projectile) but a Jedi ally's Push on the caster breaks it. |
| Animation | The free arm outstretched, fingers closing slowly into a claw (free-arm pose `CHOKE`, appended to `PowerAnimations.Kind` and drawn in `HumanoidModelMixin` by the lead on W6's request; no M2 pose exists for it). The target's hands go to its throat and its feet hang. |
| VFX | Dust and grit lifted from the ground under the target; a slight bent-air tremor around its neck and shoulders; on release, a puff of dust as it drops. No glow or tendrils. |
| SFX | `force.choke.start` (a tight, low pressure), `force.choke.loop` (a strained creak; the target's own hurt sound is muffled; stops on every cancel path), `force.choke.release`, `force.choke.crush` |
| Multiplayer | Effects `force_choke` (variants start, end, crush) and `force_choke_target` (entity). |

### 9.4 Telekinetic Lift for Jedi and Sith

M3 made Lift the Independent signature and noted that Jedi and Sith could get it in M4 with one node requirement (M3 open question 4). A node's `path` names one path (`SkillTreeLoader`), so M4 adds one purchasable node in each path tree: `lift_learned_jedi` (Jedi tree, `path: jedi`) and `lift_learned_sith` (Sith tree, `path: sith`), each level 13, 2 SP, requiring `force_push` (as built; a requirement names a node, so `force_push:2` was dropped). Buying either grants the `force_lift` node (W6, from the node-learned hook), which opens its `lift_shape` branches. Independents keep Lift free from `wayfarer_pell`. W6 also removes `path: independent` from the granted-only `force_lift` node in the Exploration tree, so a Jedi or Sith who owns it sees it as their own. No loader change is needed.

### 9.5 Tree additions (W6)

| Tree | Node | Kind | Cost | Lv | Req | Effect |
|---|---|---|---|---|---|---|
| universal | `force_speed` | ability | 2 | 12 | none (`taught_by: ["independent"]`) | 9.1 (granted to Independents) |
| universal | `speed_burst` / `speed_long_road` | branch, group `speed_shape` | 1 | 13 | force_speed | 9.1 |
| jedi | `lift_learned_jedi` | ability (grants `force_lift`) | 2 | 13 | force_push; `path: jedi` | 9.4 |
| sith | `lift_learned_sith` | ability (grants `force_lift`) | 2 | 13 | force_push; `path: sith` | 9.4 |
| exploration | `speed_wayfarer` | passive, granted, path Independent (as built: a branch needs an exclusive group of two or more, and this one excludes nothing) | – | – | force_speed | 9.1 |
| jedi | `force_barrier` | ability, granted | – | – | | 9.2 |
| jedi | `barrier_shelter` / `barrier_return` | branch, group `barrier_shape` | 1 | 13 | force_barrier | 9.2 |
| jedi | `guardians_patience` | passive | 2 | 14 | force_barrier | Parries during the 40 ticks after a Barrier ends restore 2 FE |
| sith | `force_choke` | ability, granted | – | – | | 9.3 |
| sith | `choke_crush` / `choke_dread` | branch, group `choke_shape` | 1 | 13 | force_choke | 9.3 |
| sith | `cruel_focus` | passive | 2 | 14 | force_choke | Lightning on a target released from the caster's own Choke within the last 40 ticks deals +20% (Choke and Lightning are both channels, and only one channel runs at a time) |
| saber | `staff_whirl` | technique | 1 | 12 | none | Whirl (5.6) also staggers for 10 ticks; it does nothing until the player holds a saberstaff, which the tooltip says |
| saber | `lens_mastery` | passive | 1 | 13 | none | Lens effects +50%; it does nothing without a fitted lens, which the tooltip says |

Every requirement above is one `SkillTreeLoader` already reads (node ranks, one `path`, `taught_by`, `min_level`, `granted_only`); M4 adds no loader field.

SP in M4 (14.1): about 4 from levels 11 to 15, 2 from Kaast, and 1 for each of the two expeditions. Purchasable M4 nodes cost 10 SP for a Jedi or a Sith (8, plus 2 for Lift) and 3 for an Independent (a `speed_shape` branch, `staff_whirl` and `lens_mastery`; Speed, `speed_wayfarer` and Lift are granted), so Jedi and Sith choose, and Independents spend the rest in the M2 and M3 trees.

---

## 10. Enemies and the boss

Ownership: **W6** owns every M4 enemy, the seeker droid, the hunting camp fight and Kaast (`com.oldrepublic.enemy.{mandalorian,droid,creature}`, `com.oldrepublic.boss.kaast`, their client renderers and models), and registers their entity types. W6 adds the creatures' natural spawns with `BiomeModifications` (3.1); W1's `site_spawner` groups place enemies in sites; W2 owns their story uses and rewards.

The brief asks for enemies with different behaviours. Each M4 enemy has one thing the player must learn: hunters fly and burn, droids shrug off blasters and the Force grip, hollowhorns charge in a straight line, ash hounds flank in packs.

### 10.1 Mandalorian hunter (`mandalorian_hunter`)

| | |
|---|---|
| Role | Bounty hunter with gadgets (the brief's "bounty hunters with gadgets and traps"); the Chapter Four ambushers and the loop's hunter bounties |
| Look | Hunter's plate in varied worn colours (seeded), T-visor helmet, jet pack, wrist gauntlets, a rifle |
| Health / armour / speed | 40 / 10 / 0.28; knockback resistance 0.2 |
| Rifle | Gold bolts (`MANDALORIAN` style), 6 damage, bursts of 2, 40-tick cooldown, leads moving targets (the poacher lead prediction, stronger against Force Speed) |
| Jet hop | Every 200 ticks or when the player closes within 5 blocks: 20-tick tell (a rising whine, flame flicker at the pack), then a hop of 6–10 blocks to high ground or a flank. Pushed in the air: falls and is staggered on landing |
| Wrist flamer | Under 4 blocks: 14-tick tell (wrist raised, a pilot flame and a hiss), then a 3-block cone for 30 ticks (2 damage every 5 ticks and fire). Barrier halves it; a parry does nothing |
| Wrist rocket | Every 300 ticks at 8–20 blocks: 24-tick tell (a targeting beep and a small red dot on the target, the only red light on the field), then a slow rocket (heavy, not deflectable by a saber; Push sends it back; Barrier stops it with a burst) exploding in 2 blocks for 7 |
| Cover | The M3 trooper cover-seeking logic |
| Yield | The last hunter of an ambush yields at 20% (dialogue `c4_hunter_yield`, the M2 `Yieldable` flow: Spare / Interrogate / Execute) |
| Drops | 1–6 credit chits (50%; added on death by W2 with the `vendors` keys, 7.7), `durasteel_plate` (30%), Mandalorian armour schematic piece (8%, pity at 10), `jet_pack` mod (5%); never full armour |

### 10.2 Mk IV security droid (`security_droid`)

| | |
|---|---|
| Role | "Droids with specialised resistances": Imperial dig guards in greater tombs, Proving patrols (not hostile to Sith), the second ambush's escort (clue 3 counts when that droid dies, 8.5) |
| Look | A tall, narrow-shouldered sentry droid with twin arm blasters and a single sensor eye that sweeps |
| Health / armour / speed | 50 / 12 / 0.2 |
| Resistances | Blaster bolts −50%; immune to Choke, Lift (too heavy: refuses), poison, wither, fire; Push −50% distance; immune to the Mandalorian flamer |
| Weaknesses | Shock ×1.5 (Lightning, the electrostaff, shock edges); stun grenades stagger for 60 ticks; a saber parry of its bolts returns them at ×1.5 |
| Fire | 20-tick spin tell (arm barrels rotate, a rising whir), then 8 bolts over 40 ticks (4 damage each), 80-tick cooldown |
| Shield | Below 50% health, once: a frontal plate shield for 100 ticks that stops bolts from the front (shown by sparks where they strike); flank it or wait |
| Death | Sparks, a falling hum and a small burst (2 damage within 1.5 blocks, 20-tick tell of sparking) |
| Drops | `durasteel_plate` 1–2, redstone 2–4, a droid part (`droid_actuator`, an Armstech component for `heavy_repeater` and `mod_overcharged_cell`, 7.3) 25% |

### 10.3 Hollowhorn (`hollowhorn`, Selvaren)

| | |
|---|---|
| Role | "Beasts with lunges and territorial behaviour" |
| Look | A shaggy, elk-like grazer the size of a horse with two curling horns; calves are smaller |
| Health / speed | 30 (bulls 45) / 0.25 |
| Behaviour | Herds of 3–5 graze. Neutral; hostile for 400 ticks after being hit, or when a player comes within 6 blocks of a calf |
| Charge | 20-tick tell (head lowered, a snort and a hoof scrape kicking dirt), then a straight charge of up to 14 blocks at speed 0.6: 8 damage and strong knockback. Hitting a solid block stuns it for 50 ticks (it takes ×1.5 damage meanwhile). Sidestep and punish |
| Drops | `hollowhorn_hide` 1–2, `raw_hollowhorn` 1–3 (smelts to `cooked_hollowhorn`: food 7); `hollowhorn_horn` 20% (Artifice: `lens_resonant`, 7.3) |
| Corrupted variant | Dark-veined hollowhorns for the Heartstone waves only (8.4): the same entity with a `corrupted` flag set by the wave spawner, always hostile, no calves, no drops. They do not spawn naturally |

### 10.4 Ash hound (`ash_hound`, Varkhast)

| | |
|---|---|
| Role | "Beasts with lunges" in packs: the planet's night threat |
| Look | A lean, grey, hairless hound with ash-dusted hide and pale eyes |
| Health / speed | 18 / 0.35; fire immune |
| Behaviour | Packs of 3–4 at dusk, night and in ashfall. One howls on spotting a player (a call that brings the pack); two circle to flank while one holds in front |
| Lunge | 10-tick tell (crouch and a growl), then a leap of up to 6 blocks: 5 damage. A parry or a Barrier pulse turns it and staggers the hound for 30 ticks |
| Drops | `ash_hound_fang` 1 (60%), bone |

### 10.5 The boss: Torvin Kaast, the hunter

**Identity.** Torvin Kaast is a Mandalorian who left his clan and hunts for pay: precise, patient, a little tired of it. He took the Buyer's contract because the price was absurd and the target interesting. He wants a fair fight more than he wants the money.

**Arena: the hunting camp** (`boss/hunting_camp`, 41×14×41 (Milestone 6: now 57×16×57, with the same fight layout), per player, W1 template, W6 fight). A ring of scrap walls 4 blocks high with four gun nests on scaffold towers (N, E, S, W) and a central pit 15 blocks across and 3 deep with ramps. Crates and wreck parts give cover. The ring's gate closes behind the owner (a sliding scrap door, the M3 seal rules), and opens again on yield, death or reset.

| Phase | Health | Kaast | Answers |
|---|---|---|---|
| 1. **The Hunt** | 100–65% | Moves between the four nests by jet hop (30-tick tell at the pack). Rifle bursts from the nests. Every 300 ticks a wrist rocket (24-tick red-dot tell). His droid **Rattle** (20 health, flying, untargetable by Choke) sweeps the player with a scan line: while scanned, Kaast's bolts deal +25% | Deflect bolts back at the nest (a parried bolt staggers him 20 ticks); Push a rocket back; Push or a returned heavy bolt knocks him off a nest (he falls and is staggered 40 ticks, then hops back); destroy Rattle to end the mark (Rattle returns in phase 3 if not destroyed) |
| 2. **The Trap** | 65–30% | Drops into the pit. Arms 6 mines around the ring (each blinks and beeps for 40 ticks before it is live; a live mine triggers at 1.5 blocks for 8 damage and knockback). Throws a **net** every 400 ticks (20-tick tell, a gauntlet click): a caught player is rooted for 60 ticks and Speed, Barrier and Jump end; Push, a saber (3 hits) or a teammate frees them. Flamer sweeps at close range. Calls 2 hunters once (at 50%) | Sense shows live mines (dust settles on them); Lift or Push sets mines off from range; heavy attacks while he is arming a mine (30-tick kneel) interrupt it and stagger him; fight the hunters away from the mines |
| 3. **The Last Stand** | 30–10% | Helmet cracked. Draws a vibro-axe of Mandalorian iron that can **block a saber** (his guard: 120). Fast three-hit combos (8 / 8 / 12, 10-tick wind-up on the third), a shoulder charge (14-tick tell), and one last jet slam (40-tick tell: he rises high and comes down where the player stood; 12 damage in 3 blocks) | Parry the third combo hit (stagger 30, his guard −40); dodge the slam; Choke holds him for 20 ticks once (then his flamer burns it off, staggering the caster); Barrier stops his rifle but not the axe |

- **Yield** at 10%: he kneels, takes his helmet off and waits (dialogue `c4_kaast_yield`, 8.6).
- **Scaling:** health 500, +60% per extra participant up to 4 (the M3 formula); mines +2 (`kaast.minesPerParticipant`) and the hunters +1 (`kaast.huntersPerParticipant`) per extra participant.
- **Resistances:** knockback 0.6, control effects shortened by 40% (`kaast.controlShorten`; Kaast's own rule, not the M3 diminishing returns), Lift refused except for 1 block (`kaast.liftFallBlocks`) during the 40-tick fall stagger in phase 1. Choke works only as phase 3 says: his `ForceReactions.Handler.chokeTicks` returns 0 in phases 1 and 2, and 20 once in phase 3 (9.3).
- **Interrupts** (only these, the M3 rule): a parried bolt or combo hit, a Push off a nest, a heavy attack during the mine kneel, a returned heavy bolt.
- **Reset:** if every participant leaves the ring or dies, after 200 ticks the camp resets (mines cleared, health restored, Rattle respawned). The owner can come back any time.
- **Peaceful:** 8.8.
- **Music:** three boss tracks, `music.kaast.hunt`, `music.kaast.trap` and `music.kaast.last_stand`, one per phase (13.4), following the vanilla music slider.
- **Commands** (W6, registered in `boss.kaast`, operator level 2): `/oldrepublic kaast camp <player>` places the player's camp now (`HuntingCamps.place`; the only way when `kaast.placeCamp` is false); `/oldrepublic kaast phase <player> <1|2|3>` jumps that player's running fight to a phase (tests and 17.3); `/oldrepublic kaast reset <player>` resets the camp as the 200-tick rule does.
- **Boss bar:** "Torvin Kaast, the hunter", with phase names as banners.

### 10.6 Rewards (guaranteed; flag-guarded per player)

- Owner, first clear: 4,000 XP, 2 SP, 300 credits, Resonance `kaast` +500, plus the choice rewards (8.6). This is the only place the fight's XP is paid; the choice rows add none.
- The camp's **stash** (a locked crate that opens after the choice): 2 `alloy_plate`, 4 hyperfuel cells, a random Armstech schematic the player lacks (or 200 credits).
- Helpers: 1,000 XP (`kaast.helperXp`) and a schematic roll, once per in-game day.
- **Rematch:** after COMPLETE, the owner calls a "hunter's echo" of Kaast at the camp gate once per in-game day (the M3 rematch rules): 800 XP, a Mandalorian schematic piece (pity at 4), 100 credits (`kaast.rematchCredits`). The camp stays in reach by ship, because the `hunting_camp` destination is never locked again (4.5).

### 10.7 The seeker droid

`seeker_droid`: a small flying probe, 4 health, invulnerable during its scene, `noSave`, never hostile. It only exists for the Chapter Four opening (8.2) and as Rattle's look (Rattle is a separate entity, `kaast_droid`, with the same model and a different paint).

---

## 11. Controls and HUD

### 11.1 Key plan

| Action | Default | Category | Notes |
|---|---|---|---|
| Weapon Action | N | Echoes of the Old Republic | `key.oldrepublic.weapon_action` (lead, Phase 0: `client/combat/WeaponActionKey`). Blasters: vent (tap) and the rifle's charged shot (hold); electrostaff: charge; saberstaff: single blade. Sends `WeaponActionPayload(boolean pressed)` on press and on release; the server routes it with `WeaponActions.dispatch` to the handler for the main-hand stack (W4 and W5 register handlers). |
| Force Power slots 1–4, Force Sense, Force screen, Journal, Toggle Lightsaber, Saber Form | R, Z, V, B, H, K, J, G, unbound | same | Unchanged. Speed, Barrier and Choke go in slots. |
| Jet pack | jump, while airborne | vanilla | No new key (6.4) |

- The Phase 0 probe already showed N has no vanilla 26.2 default; the client test that checks for clashing defaults now includes N.
- All keys can be rebound.

### 11.2 HUD

- **Heat gauge** (W4): a thin arc to the right of the crosshair, filling as heat rises; the 70–90 band is marked; turns red-orange at 90 and flashes (a steady glow when flashing is off) on overheat. Shown only while a blaster is held, and for 40 ticks after.
- **Reticle and hit markers** (W4): 5.8.
- **Jet pack charges** (W4): two or three small pips under the heat gauge position while the jet pack is worn and the player is airborne or a charge is refilling.
- **Barrier and Choke** (W6): the existing channel bar under the ability sockets (M2), labelled.
- **Flight HUD** (W3): during the take-off and landing, a cockpit strip at the top of the screen: destination name, altitude to pad, hull and fuel bars. Hidden in the hyperspace overlay.
- **Hyperspace overlay** (W3): a full-screen effect of star streaks stretching into a blue-white tunnel with a slow pulse; vignette at the edges; the HUD fades. Flashing off: no pulse. Reduced: fewer streaks. It covers the client's chunk load in the new dimension and ends after the Hyperspace phase or when the client's level is ready, whichever is later.
- **Off-world Calling** (lead, done in Phase 0): when the guided target is in another dimension, the Calling cue points at the player's ship body if it is loaded in the player's level, else at their home pad if that is in the player's level, else it is the faint cue; the action bar says "The Calling comes from <world>." (3.6).
- **Toasts:** schematic learned; discipline level up; destination unlocked; ship repaired.
- **Banners:** planet arrival, Kaast's phase names, the Chapter Four report, Masterwork crafted (a small one).

### 11.3 Screens

- **Engineering Bench** (W5): 7.1.
- **Vendor** (W2): 7.8.
- **Ship** (W3): 4.10.
- **Destination map** (W3, cut item 8): opened from the Nav tab. A dark star chart with the frontier's star and the two planets' systems as soft lit points joined by faint lanes; locked destinations are dim with a reason; hovering shows the planet's description and the pad. It is a picker, not a navigable space: choosing a point is the same as choosing a row on the Nav tab.
- **Journal:** the Chapter Four page; the new **Crafting** tab (W5).
- **Force screen:** new nodes (9.5); no new tab.

---

## 12. Persistence, multiplayer, dedicated server and config

### 12.1 Saved state

All new player attachments follow the M2 pattern: persistent, kept on death (`copyOnDeath`), synced to their owner only (`targetOnly`) and **sealed** (`seal()`, `setAttached(data.copy().seal())`, re-`get` after commits). Codecs only add `optionalFieldOf` fields; enums are by ordinal, append only; a record that would pass 16 fields (the DFU limit) is split into nested records.

| Attachment / storage | Owner | Contents |
|---|---|---|
| `oldrepublic:story` | W2 | New record `Chapter4`, built in Phase 0 with `stage`, `wait_since`, `arc` (nested: `planet` id string, `mission_flags` mask, `expedition` 0 none / 1 done / 2 skipped) and `hunt` (nested: `clues` mask, `sabotage_done`, `ambush_day`, `ambushes`). W2 adds more nested records: `duel` (`camp` `GlobalPos`, `outcome` −1 or `KaastOutcome`, `boss` 0 / 1 yielded / 2 resolved), `sites` (≤ 8 `GlobalPos` by `Site`) and `back_pay` (done). New `flags` (`c4.*`) and `counters` (`c4.hunter_day`, `c4.hunter_pity`, `c4.helper_day`, and the re-issue days of 12.2) |
| `oldrepublic:crafting` (new, `CraftingData`, Phase 0) | W5 | `disciplines` (≤ 2: id, level, xp, pity), `unlearned` (≤ 4 discipline ids; their schematics stay in `known`), `known` (≤ 256 schematic ids), `first_crafts` (≤ 256), `chosen_at` (day) |
| `oldrepublic:travel` (new, `TravelData`) | W3 | `unlocked` (destination ids, ≤ 16), `home_pad` (`GlobalPos`, optional), `last_pad`, `recall_day`, `tow_day`, `crew_of` (the owner whose crew this player is, optional) |
| `oldrepublic:travel_visits` (new, as built) | W3 | an Integer bit mask of the planets this player has landed on, for the first-landing banner; persistent, kept on death, not synced |
| `oldrepublic:missions` | W2 | `active.where` (Phase 0, 3.6: nested `site` and `board` dimensions, default overworld; the `BlockPos` fields stay, and `siteGlobal()` and `boardGlobal()` combine them), planet contract offers and `used_offers` with dimension |
| `oldrepublic:identity` | W2 | `choices` gains the M4 choice ids. No new faction: the hubs use `JEDI_ORDER` and `SITH_EMPIRE`, and Independents use `FRONTIER` (8.4) |
| `oldrepublic:skills` | W6 | new nodes only (9.5) |
| Global `oldrepublic:ships` (new, `ShipRegistry`) | W3 | per owner: body UUID, dimension, position, yaw, state, hull, fuel, upgrades mask, hold (≤ 36 stacks), crew (≤ 3 UUIDs), hull code, last flight (origin, destination, phase). As built, the upgrades mask also holds the hulk's repair progress and the paint: the core, motivator and nav sockets are bits 8 to 10, the fitted plates bits 12 to 15 and the paint bits 16 and 17 (`ShipRules`) |
| Global `oldrepublic:world_sites` (`WorldSites`) | lead skeleton, W1 | records gain planet kinds; per-dimension caps; ≤ 1,024 records |
| Block entities | W1, W3, W5 | `engineering_bench` (parts tray, 9 slots), `landing_beacon` (owner), `waystone` (corrupted flag, cleansed-by set ≤ 64), hub `site_spawner` restores, `camp_gate` (owner, state) |
| Item components | lead (Phase 0, `registry/M4Components`); W3 | `oldrepublic:blaster_heat`, `oldrepublic:mods`, `oldrepublic:quality` (int 0–2), `oldrepublic:schematic` (`Identifier`), `oldrepublic:named` (`String`, a named drop's id) are the lead's; `oldrepublic:nav_data` (planet ids) is W3's; `saber_config` gained optional `second` and `lens` in Phase 0 |
| Entities | | Hub NPCs, vendors, Proving guards: persistent with hub tags. Hunters and droids in owned encounters: persistent with owner tags; hunter bounties: not persistent. The ship body: persistent (its truth is `ShipRegistry`). Seeker droid, Rattle, Kaast, mines and nets: `noSave` (Kaast respawns from the camp gate when the owner is near) |

### 12.2 Edge cases

| Event | Behaviour |
|---|---|
| Death on a planet | Respawn at the player's bed if it is on that planet (respawn anchors do not work there, 3.1); otherwise, if `planets.respawnAtHub` (default true), at the planet's hub medbay (an `AFTER_RESPAWN` teleport to the medbay anchor); otherwise vanilla (the home world spawn) and the pad terminal or beacon brings the ship back (4.8). Barrier, Choke and Speed end. The active mission continues |
| Death during a flight | Riders cannot take damage during `LAUNCHING` to `LANDING` (the ship's riders are invulnerable for the sequence, including suffocation and fall). `/kill` still works; the ship completes its flight without them |
| Disconnect aboard | Dismounted before save and placed beside the ship (4.9). Logging in mid-flight (the ship moved on): placed at the ship's current pad, or the origin pad if the jump did not happen |
| Disconnect of the owner mid-flight | The flight completes; crew are landed with the ship |
| Server restart | A ship in `IN_TRANSIT` or `LANDING` is placed at its destination; a ship in `LAUNCHING` goes back to its origin pad with nothing taken; either way its state is set as at Settle (4.9). Hubs, ships and sites are kept. Kaast and the camp's mines are respawned by the gate; a YIELDED duel stays yielded |
| Ship body missing | Restored from `ShipRegistry` by the beacon, pad terminal or command (4.9) |
| Landing beacon broken | `home` falls back to the ship cradle; if that is gone too, to the player's spawn point, with the pad search there |
| Two players' ships at one pad | 4.5 bays, then a ring search |
| Hub cannot fit | Raised platform at (0, 0) (3.5) |
| Planet missing (data pack removed, a modded server without the dimension) | Destinations to it are hidden; a ship whose registry says it is there is restored at `home`; players saved there are moved by vanilla to the home world spawn |
| Dimension change during a channel or a dialogue | Cancelled (the M3 list). A boss participant leaves the bar after 100 ticks |
| Vendor full inventory | The purchase is refused before payment ("No room") |
| Crafting with a full inventory | The output stays in the output slot until taken; closing the screen drops it at the player's feet for them only (the M2 rule for owned drops) |
| Lost schematic item before learning | Re-dropped by its source only if that source is a story reward (one re-issue a day at the giver) |
| Lost one-time chapter item | Re-issued once per in-game day while the step still needs it (the M3 `archive_record` rule, a `c4.*` counter each): the motivator during `REPAIRS`, by the route that gave it (Sense at a Wreck Field's `HEART` anchor again, or the smuggler of "A Part Off the Books"; a bought one can be bought again); the expedition crystal at its site while the player owns no second crystal; `kaast_pattern` from the camp stash. Clue 3 needs no rule: it counts when the droid dies, not on the `sealed_contract` item |
| Unlearning a discipline mid-craft | Refused while a craft is running |
| Griefing | Hub blocks, pads, waystones, the camp gate and the beacon (for non-owners) are unbreakable in survival; decor can be broken and does not regrow |
| Peaceful | 8.8 |

### 12.3 Multiplayer

- **Per player and owned:** the hulk, the ship and its hold, the expedition sites, the Wayfarer cache, the hunting camp, the ambushes, story choices and every reward. Kills in an owned encounter by anyone count for the owner (the M2 rule).
- **Shared:** both hubs and their NPCs, the Heartstone and Zarvos's Sepulchre (their trials are per player: a second player's cleansing spawns its own waves, owned by them, and the shared stone shows the state of whoever is closest; rewards are per player), worldgen waystones (cleansing is per player: a cleansed stone stays corrupted for others) and greater tombs.
- **Ships:** one per player; crew ride with permission; only the owner flies (4.9). Players can fly together by being one owner's crew.
- **Vendors:** stock limits are per player per day.
- **PvP:** blasters, grenades and the electrostaff follow server PvP and team rules; Barrier always stops bolts; Choke on players only with `force_choke.pvpEffects` and server PvP, through `Holds`.
- **Kaast co-op:** the M3 boss co-op rules (8.9 of M3): participants by session; helpers' rewards once a day.

### 12.4 Dedicated server

- No client classes in `src/main` (`splitEnvironmentSourceSets` and the server game-test boot guard it). New client code: ship renderer and model, hyperspace overlay, flight HUD, bench, vendor and ship screens, heat gauge, reticle, arm poses, planet ambience, creature and droid renderers.
- Dimensions are data, loaded by the server and synced to clients through the registry sync (dimension types and biomes are synced registries). Environment attributes are synced with the dimension type and biomes.
- Schematics and vendor stock load on the server only and are synced to clients as needed (schematics on join and reload; stock when the vendor screen opens).
- A client test connects to a dedicated server, flies to Selvaren and back, fires a blaster, and chains the jet pack (17.2).
- `allow-flight=false` is the test default; riders and jet pack users must never be kicked (4.1, 6.4).

### 12.5 Config

**Server (`balance.json`) new sections:** `planets`, `travel`, `ship`, `blasters`, `melee`, `armour_m4`, `mods`, `crafting`, `vendors`, `chapter_four`, `force_speed`, `force_barrier`, `force_choke`, `mandalorian`, `security_droid`, `creatures`, `kaast`. **Extended sections:** `saber` (W5: the training saber, the staff, `staff_whirl` and `lens_mastery`), `missions`, `equipment`.

**Notable server switches:**

| Key | Default | Effect |
|---|---|---|
| `planets.respawnAtHub` | true | 12.2 |
| `travel.enabled` | true | When false, ships cannot jump (repair and hulk still work); operators use `/oldrepublic ship travel` |
| `travel.combatLock` | true | Refuse launch while recently attacked (4.6) |
| `ship.wearPerJump` | 5 | |
| `chapter_four.hunterBounties` | true | The loop's hunter encounters |
| `kaast.placeCamp` | true | When false, an operator places the camp with `/oldrepublic kaast camp <player>` (10.5) |
| `force_choke.pvpEffects`, `force_barrier.pvpPulse` | false | |
| `vendors.priceMultiplier` | 1.0 | Server-wide price scale |

**Client (`ClientConfig`, lead):** `shipCamera` (on), `hyperspaceOverlay` (on; off shows a plain fade), `blasterReticle` (on), `planetAmbience` (on). Toggled with `/oldrepublicfx ship_camera|hyperspace|reticle|planet_ambience on|off`; done in Phase 0. Nether portals need no switch: vanilla lights them only in the overworld and the Nether (3.1).

**Data** (owner in brackets; section 16 lists every id):

- dimensions, dimension types, noise settings, biomes, features, the two worldgen structure sets and their pools (W1);
- `data/oldrepublic/schematic/*.json` (W5); `data/oldrepublic/vendor/*.json` (W2); `data/oldrepublic/mission/*.json` M4 additions (W2);
- site anchor sidecars for every M4 template;
- loot tables: `chests/greater_tomb`, `chests/kaast_stash`, `chests/wayfarer_cache_m4`, `entities/mandalorian_hunter`, `entities/security_droid`, `entities/hollowhorn`, `entities/ash_hound`, and block loot for each new breakable block. The lead changes the M2 poacher tables, which are lead files (15.2): `entities/crystal_poacher` and `entities/poacher_captain` drop `credit_chit`s in place of iron nuggets (7.7), and `entities/poacher_captain` gains a `vibroblade` drop at 10% (5.2);
- tags: biome `is_selvaren`, `is_varkhast`, `is_planet`, `has_structure/waystone`, `has_structure/greater_tomb`; entity `choke_immune`, `droids`, `c:bosses` (Kaast); item `blasters`, `vibroweapons`, `mods/*`, `credits`; damage type tags (`is_projectile` gains `mandalorian_rocket`; `is_fire` gains `wrist_flamer`; `oldrepublic:force_harm` gains `force_choke`). Files under `data/minecraft/tags/**` belong to the lead;
- damage types `force_choke` (W6), `wrist_flamer`, `mandalorian_rocket`, `camp_mine`, `jet_slam` (W6), `duskglass_cut` (W1).

---

## 13. Effects and sounds

Each worker writes the phase-by-phase treatment (anticipation, activation, travel, impact, aftermath) into their design doc; the lead merges it into `VFX_SFX_SPEC.md` as a "Milestone 4" section. The art direction holds (`VFX_SFX_SPEC.md`): bent air, dust, debris, pressure and light; **no ground circles, sigils, halos, glyphs or glow outlines**; anything that spreads along the floor is irregular dust and debris with gaps; tells are never hidden or thinned by presets (`Vfx.tellCount`); flashing off replaces flicker with steady glows; every loop stops on cancel, death, disconnect, dimension change, leaving the area, and item or slot change.

### 13.1 Particle budgets per active instance at Standard

| Effect | Budget |
|---|---|
| Blaster muzzle flash and bolt trail (player) | 4 per shot; trail uses the M1 bolt renderer (no particles) |
| Overheat vent | 12 steam puffs over 20 ticks |
| Ship engines (idle READY) | 2 per tick; climb and descent 8 per tick; ground dust at take-off and landing 60 over 40 ticks |
| Ship damage (below 40 hull) | sparks 1 per 10 ticks, smoke 1 per 4 ticks |
| Hyperspace overlay | screen-space streaks, not particles (40 at Standard, 15 at Reduced) |
| Force Speed | 2 per footfall, 6 streak motes per second |
| Force Barrier | 24 held dust motes, 6 per impact |
| Force Choke | 10 grit motes per second under the target |
| Jet pack burst | 14 flame and smoke over 10 ticks |
| Wrist flamer | 30 per second (a tell, never thinned) |
| Ashfall (ambient, client) | 40 visible flakes around the camera at Standard |
| Waystone corruption | 6 dark-vein motes per second within 8 blocks of the stone |

### 13.2 New particle types (each owner registers its own)

There is no shared particle class: each owner registers its types in its feature's `<Feature>Particles` class, as in M3 (`WorldParticles` and the others), and owns the particle JSON and texture.

| Owner | Types |
|---|---|
| W1 | `ash_flake`, `aurelite_glint`, `duskglass_shard` |
| W3 | `engine_exhaust`, `spark_shower` |
| W4 | `steam_vent`, `flame_jet` (the jet pack's; W6's hunter flamer and jet hop use it by id) |
| W6 | `held_dust` |

### 13.3 Effect events (look and sound)

| Id | Owner | Delivery (variant, intensity) | Look | Sound |
|---|---|---|---|---|
| `player_blaster_fire` | W4 | toTracking; entity; variant = weapon (0 pistol, 1 rifle, 2 repeater, 3 charged) | Muzzle flash sprite, a small bolt-coloured glow at the barrel | `blaster.pistol_fire` and `blaster.fire` (the M2 ids, reused for the pistol and the rifle), `blaster.repeater.fire`, `blaster.rifle.charged` |
| `blaster_heat` | W4 | toTracking; 0 overheat, 1 vent, 2 perfect vent | Steam from the vents; a perfect vent is a quick clean hiss with one bright puff | `blaster.overheat`, `blaster.vent`, `blaster.vent_perfect` |
| `repeater_spin` | W4 | toTracking; 0 up, 1 down | Barrel blur | `blaster.repeater.spin_up`, `blaster.repeater.spin_down` |
| `vibro_hit` | W4 | around; variant 0 flurry, 1 cleave | A short whine and a grit flick at the impact | `vibro.flurry`, `vibro.cleave` |
| `electrostaff` | W4 | toTracking; 0 charge, 1 shock, 2 block saber | Arcs crawling along the staff ends; a snap on shock; saber clash sparks | `electrostaff.charge`, `electrostaff.shock`, `electrostaff.clash` |
| `jet_pack` | W4 | toTracking; 0 burst, 1 empty | A gout of flame and smoke from the pack | `jetpack.burst`, `jetpack.empty` |
| `mod_fit` | W5 | toPlayer; 0 fit, 1 remove | (screen) | `bench.mod_fit`, `bench.mod_remove` |
| `bench_craft` | W5 | around; 0 start, 1 done, 2 masterwork | The welding arm sparks; on done a puff of smoke; Masterwork: a short shimmer of sparks rising from the item | `bench.weld` (loop), `bench.done`, `bench.masterwork` |
| `salvage` | W5 | around | Parts clatter out in a small spill of grit | `bench.salvage` |
| `consumable` | W5 | toTracking; 0 medpac, 1 kolto, 2 stim, 3 crash | A small hiss at the arm; kolto: a blue mist puff | `medpac.use`, `kolto.inject`, `stim.inject`, `stim.crash` |
| `scanner_pulse` | W5 | toPlayer; dir | A ring-free pulse: a single quick brightening of motes along the look direction | `scanner.pulse`, `scanner.ping` |
| `grenade_player` | W5 | around; 0 throw, 1 stun burst | Stun: a white flash (steady with flashing off) and a ring-free dust kick | `grenade.throw`, `grenade.stun` |
| `saber_staff` | W5 | toTracking; 0 ignite second blade, 1 whirl | The second blade's ignition; Whirl: two blade arcs and a dust kick | `saber.staff_ignite`, `saber.staff_whirl` |
| `lens_fit` | W5 | toPlayer | (screen) a crystal glint | `workbench.lens_fit` |
| `ship_engine` | W3 | toTracking; entity; 0 idle, 1 spool, 2 climb, 3 descent, 4 settle | Exhaust from the nacelles; ground dust in uneven gusts at spool and settle; struts | `ship.idle` (loop), `ship.spool`, `ship.climb`, `ship.descent`, `ship.land`, `ship.ramp` |
| `ship_jump` | W3 | toTracking; 0 jump out, 1 arrive | A pressure thump; the ship stretches and vanishes in a streak (out) or appears from one (in) | `ship.jump_out`, `ship.jump_in` |
| `hyperspace` | W3 | toPlayer; intensity = progress | The overlay (11.2) | `ship.hyperspace` (loop) |
| `ship_repair` | W3 | around; variant = socket (0 hull, 1 core, 2 motivator, 3 nav, 4 plate weld, 5 stand up) | Sparks, a clank, a hum; stand-up: tarp pulled back, supports hiss away, running lights on | `ship.fit_part`, `ship.weld`, `ship.power_up` |
| `ship_damage` | W3 | toTracking; entity; intensity = 1 − hull | Sparks and smoke by hull band | `ship.damaged` (crackle loop under 20 hull) |
| `landing_beacon` | W3 | around; 0 set, 1 refuse, 2 recall | A blink pattern | `beacon.set`, `beacon.refuse`, `beacon.recall` |
| `force_speed` | W6 | toTracking; 0 start, 1 end, 2 burst | 9.1 | `force.speed.start`, `force.speed.loop`, `force.speed.end`, `force.speed.burst` |
| `force_barrier` / `force_barrier_impact` | W6 | toTracking; 0 start, 1 end, 2 pulse, 3 break / position | 9.2 | `force.barrier.start`, `.loop`, `.impact`, `.pulse`, `.break` |
| `force_choke` / `force_choke_target` | W6 | toTracking; 0 start, 1 end, 2 crush / entity | 9.3 | `force.choke.start`, `.loop`, `.release`, `.crush` |
| `hunter_tell` | W6 | toTracking; entity; 0 jet, 1 flamer, 2 rocket, 3 net | Pack whine and flicker; wrist pilot flame; the red targeting dot; a gauntlet click | `mandalorian.jet_tell`, `mandalorian.flamer_tell`, `mandalorian.rocket_lock`, `mandalorian.net_tell` |
| `hunter_attack` | W6 | around; 0 jet hop, 1 flame, 2 rocket burst, 3 net hit | Flame and smoke; a cone of fire particles; a smoky burst; the net's cords | `mandalorian.jet`, `mandalorian.flamer` (loop), `mandalorian.rocket`, `mandalorian.explode`, `mandalorian.net` |
| `droid_tell` | W6 | toTracking; entity; 0 spin, 1 shield, 2 death spark | Barrel spin blur; sparks where bolts strike the shield; a crackle | `droid.spin_up`, `droid.fire`, `droid.shield`, `droid.death` |
| `beast_tell` | W6 | toTracking; entity; 0 hollowhorn charge, 1 ash hound lunge, 2 howl | Dirt kicked by a hoof; a crouch; a breath of ash | `hollowhorn.snort`, `hollowhorn.charge`, `hollowhorn.impact`, `ash_hound.growl`, `ash_hound.howl`, `ash_hound.lunge` |
| `kaast` | W6 | around; variant = moment (0 phase, 1 mine arm, 2 mine live, 3 slam tell, 4 slam, 5 yield, 6 helmet); `DangerTellTable` marks variants 1 and 3 as tells | Banners; mine blink (a tell); the slam's rising flame and the dust burst on impact; the cracked helmet | `boss.kaast.*` (16.4) |
| `seeker_scan` | W2 | toPlayer; entity | The red scan fan of lit motes | `seeker.scan`, `seeker.chirp` |
| `waystone` | W1 | around; 0 corrupted ambience, 1 cleanse | Dark veins pulsing slowly in the stone; on cleanse, the veins drain as dust and the stone's own pale hum returns | `waystone.corrupt` (loop), `waystone.cleanse`, `waystone.hum` |
| `heartstone` | W1 | around; 0 wave, 1 complete | Corruption welling from the ground as dark mist at the stone's base; on complete a slow exhale of pale motes | `heartstone.wave`, `heartstone.cleansed` |
| `crystal_answer` | W1 | around; variant 0 Hollow chime (1–3), 1 rift bleed | Aurelite glints in time with the chime; the rift's crystal darkening to red | `hollow.chime`, `hollow.answer`, `rift.whisper`, `rift.bleed` |
| `calling` variant 9 `OFFWORLD` (done in Phase 0) | lead | toPlayer; event data = the target's `Planet` ordinal | Faint motes toward the ship or pad, with the action-bar line (3.6) | `crystal.calling` (reused), pitched up |
| `lesson_complete` variants 8–10 | W2 | toPlayer | `chapter4`, `planet`, `masterwork` banners | `story.chapter_four`, `planet.arrive` |

### 13.4 Sounds and music

Every new id is in `tools/assets/sounds/events_m4.json` (written in Phase 0: 139 ids, in the M3 format) with its file list, category and subtitle; `gen_sounds.py --m4` writes placeholders and the Java constants (the M3 process). Reused ids (`blaster.pistol_fire`, `blaster.fire`, `crystal.calling`) are not listed again. Recipes per owner: `sounds/world_m4.py` (W1), `story_m4.py` (W2), `ship.py` (W3), `gear.py` (W4), `crafting.py` (W5), `force_m4.py` and `enemy_m4.py` (W6).

**Music and ambience:**

| Id | Use |
|---|---|
| `music.selvaren`, `music.varkhast` | the planets' `background_music` attribute (vanilla music slider) |
| `ambient.selvaren.highwood`, `.meadow`, `.tarn`, `ambient.varkhast.redrock`, `.ashfall`, `.tomb_valley` | biome `ambient_sounds` loops and additions |
| `ambient.greenwatch`, `ambient.proving` | hub beds through `SiteAmbience` |
| `music.kaast.hunt`, `music.kaast.trap`, `music.kaast.last_stand` | boss phases, crossfaded like the M3 boss |

**Loops and their stop rules** (every loop also stops on death, logout, dimension change and `/kill`):

| Loop | Stops when |
|---|---|
| `ship.idle`, `ship.hyperspace`, `ship.damaged` | the ship leaves the state; the player dismounts (hyperspace) |
| `bench.weld` | the craft finishes or is cancelled, or the screen closes |
| `force.speed.loop`, `force.barrier.loop`, `force.choke.loop` | the power ends on any cancel path |
| `mandalorian.flamer` | the flame ends, or its owner is staggered, dies or is removed |
| `waystone.corrupt` | the stone is cleansed for this player, or they leave 16 blocks |
| `music.kaast.*` | the next phase crossfades in, yield, victory, reset, or leaving the ring |
| hub and site beds | leaving the site (`site_presence` changes) |

---

## 14. Balance (defaults)

Ticks: 20 per second. Cumulative XP to reach each level (`Progression.xpToNext`, unchanged curve):

| Level | 11 | 12 | 13 | 14 | 15 | 16 |
|---|---|---|---|---|---|---|
| Cumulative XP | 20,529 | 25,795 | 31,803 | 38,587 | 46,180 | 54,612 |

### 14.1 Expected budget (solo, normal pace)

M3 players finish at about level 11 (M3 14.1). Independents follow the same column with Tessaly's Road and Both Doors in place of the path missions.

| Source | XP | Total | Lv |
|---|---|---|---|
| Chapter Four begins | – | ≈ 20,600 | 11 |
| Briefing, bench and first discipline crafts (first-craft XP 30 × level, about 8 crafts) | 300 + ≈ 500 | ≈ 21,400 | 11 |
| The four repairs (300 each) | 1,200 | ≈ 22,600 | 11 |
| "A Part Off the Books" (if taken; about half of players) | 1,200 | ≈ 23,800 | 11 |
| First flight: the planet 400, the hub 200 | 600 | ≈ 24,400 | 11 |
| The arc's two missions | 3,500 | ≈ 27,900 | 12 |
| The expedition | 2,000 | ≈ 29,900 | 12 |
| Sabotage and two ambushes | 1,900 | ≈ 31,800 | **13** |
| 4 planet contracts at levels 12–13 (≈ 760 each) | ≈ 3,000 | ≈ 34,800 | 13 |
| Planet discovery (waystones, greater tombs, the other world: 150–300 each) | ≈ 1,200 | ≈ 36,000 | 13 |
| About 60 kills on planets (hunters 60, droids 75, hounds 27, with fatigue) | ≈ 2,800 | ≈ 38,800 | **14**: the duel gate |
| Torvin Kaast | 4,000 | ≈ 42,800 | 14 |
| Report | 800 | ≈ 43,600 | 14 |
| The loop: contracts (≈ 800 at 14), the second expedition, hunter bounties | ≈ 2,600 | ≈ 46,200 | **15** |

If playtests show players reaching level 14 well after the world arc, lower `chapter_four.duelLevel` to 13 before raising any reward.

**Skill points in M4:**

| Source | SP |
|---|---|
| Levels 11 to 15 | 4 |
| Kaast | 2 |
| Each expedition (first one in the arc, second in the loop) | 1 each, 2 in all |
| **Total by level 15** | about 8 |

Purchasable M4 nodes (9.5): Jedi 10 and Sith 10 (8, plus 2 for Lift), Independents 3 (a `speed_shape` branch, `staff_whirl` and `lens_mastery`; Speed, `speed_wayfarer` and Lift are granted). Jedi and Sith builds still have to choose; Independents spend the rest in the M2 and M3 trees.

**Credits:**

| Source | Credits |
|---|---|
| M3 back pay (8.8) | 100–300 |
| Planet contracts (25 + 5 × level: 90 at level 13) | ≈ 360 for the 4 in the XP table |
| Story missions | 150 for each of the two arc missions, 100 for "A Part Off the Books" if taken: ≈ 400 at most |
| Enemy drops (50% at 1–6) | ≈ 3 per kill (≈ 200 over the chapter) |
| Kaast | 300 + stash |
| **Typical by the end of Chapter Four** | ≈ 1,500 earned |
| Typical spending | motivator 400, or 360 at Honoured (if bought), fuel 40 each, two or three mods or schematics 150–400, commissions 50–250 |

### 14.2 Weapon reference (level 12 player, no mods, Standard quality)

| Weapon | Burst DPS | Sustained DPS (good venting) | Notes |
|---|---|---|---|
| Lightsaber (Unformed) | 11.2 | 11.2 | blocks, parries, deflects, refunds FE |
| Blaster pistol | 10.0 | ≈ 9.7 | range 40+, one hand free |
| Blaster rifle | 10.0 | ≈ 9.8 | best accuracy; charged shot 12 |
| Heavy repeater | 16.7 | ≈ 10.3 | slow, inaccurate on the move, pierces |
| Vibroblade | 10.8 (+flurry) | ≈ 12 | guard, no deflection |
| Vibrosword | 10.2 (+cleave) | ≈ 10 | guard, reach |
| Electrostaff | 7.2 (+shock) | ≈ 8 | reach, control, blocks sabers |
| Saberstaff | 11.3 per target (7 × 0.9 × 1.8), Whirl ×N | ≈ 11.3 | crowds |

The blaster figures use the 5.1 table. Burst is damage × 20 ÷ rate, before any heat. Sustained assumes continuous fire with a perfect vent each time heat reaches the 70–90 band (half the vent lock, then 3 shots with no heat), no cooling while firing (`coolDelay` 16 is longer than every rate), the next shot at the later of the rate and the lock, and the repeater's 12-tick spin-up after each vent. With plain vents all three give about 7.5.

Enemy health for reference: troopers 20–30, hunters 40, droids 50, hollowhorns 30–45, ash hounds 18, Kaast 500.

### 14.3 Sections

Each section is a `BalanceConfig.section` with public fields. Every tuning number in sections 3–10 is a key here, except numbers that are data (schematic inputs, vendor stock and prices, loot tables, templates and their sizes) and values fixed at registration (armour base values, stack sizes). Owners in brackets.

### `planets` (`planet.PlanetBalance`, W1)

| Key | Default |
|---|---|
| `respawnAtHub` | true |
| `hubRings` / `hubFootprint` / `hubMaxSlope` / `hubTicketRadius` | [0, 16, 32] / 63 (Milestone 6: removed; the search uses the template's own size) / 6 / 5 |
| `heartstoneRing` / `siteEnsureRange` | [80, 100, 120] / 160 |
| `expeditionRings` / `cacheRings` / `campRings` | [300, 375, 450] / [150, 200, 250] / [400, 500, 600] |
| `waystoneCorruptedChance` | 0.25 |
| `ashfallDensity` / `duskglassDamage` / `sinkingPlatformTicks` | 1.0 / 2 / 60 |
| `planetDiscoveryXp` / `hubDiscoveryXp` / `siteDiscoveryXp` | 400 / 200 / 150 (greater tomb 300) |

### `travel` and `ship` (`travel.ShipBalance`, W3)

| Key | Default |
|---|---|
| `enabled` / `combatLock` / `combatLockRange` / `combatLockTicks` | true / true / 16 / 100 |
| `spoolTicks` / `climbTicks` / `climbHeight` / `jumpTicks` / `hyperspaceTicks` / `hyperspaceMaxWait` / `descentTicks` / `settleTicks` | 40 / 60 / 40 / 10 / 80 / 200 / 80 / 20 |
| `tankCells` / `tankCellsUpgraded` / `cellsPerJump` | 4 / 6 / 1 |
| `wearPerJump` / `wearReinforced` / `jumpMinHull` / `damageFxHull` / `sabotageHull` | 5 / 3 / 20 / 40 / 10 |
| `hullPlates` / `plateRepair` / `plateRepairSkilled` / `plateRepairCooldown` / `spacerRepairPerPoint` | 12 / 5 / 8 / 10 / 6 |
| `starterCells` / `cradleRange` / `crewAddRange` | 2 / [32, 64] / 16 |
| `seats` / `seatsUpgraded` / `hold` / `holdUpgraded` | 2 / 3 / 27 / 36 |
| `padBays` / `bayLapseTicks` / `overflowRings` / `campPadOffset` / `chunkTicketRadius` / `ticketLingerTicks` | 4 / 24000 / [40, 48, 56] (Milestone 6: [64, 72, 80]) / 48 / 3 / 200 |
| `cameraDistance` | 8 |
| `beaconArea` / `beaconMaxSlope` | 11 / 2 |
| `freeRecallPerDay` / `freeTowPerDay` | 1 / 1 |

### `blasters` (`equipment.blaster.BlasterBalance`, W4)

| Key | Pistol / Rifle / Repeater |
|---|---|
| `damage` | 4.0 / 5.0 / 2.5 |
| `rate` / `spinUp` | 8 / 10 / 3; repeater spin-up 12 (again after each vent) |
| `speed` / `life` | 2.2, 40 / 2.6, 60 / 2.4, 50 |
| `hipSpread` / `steadyMultiplier` / `moveMultiplier` / `airMultiplier` | 2.0, 2.5, 4.0 / 0.35 / 2 / 3 |
| `heatPerShot` / `cooling` / `coolDelay` | 15, 14, 7 / 2.5, 2.0, 1.5 / 16 |
| `overheatLock` / `ventLock` / `ventMin` / `perfectVentBand` / `perfectVentFreeShots` | 50, 60, 80 / 20, 25, 30 / 30 / [70, 90] / 3 |
| `chargedTicks` / `chargedDamage` / `chargedHeat` | 30 / 12 / 35 |
| `repeaterSlowPct` / `rifleSlowPct` / `repeaterPierce` / `suppressHits` / `suppressWindow` / `suppressTicks` | 30 / 10 / 1 / 4 / 40 / 30 |
| `bashDamage` / `bashStagger` / `quickDrawTicks` | 2 / 6 / 4 |
| `durability` | 600 / 800 / 900 |

### `melee` (`equipment.melee.MeleeBalance`, W4)

| Key | Default |
|---|---|
| `vibrobladeDamage` / `vibrobladeSpeed` / `flurryHits` / `flurryWindow` / `flurryBonus` | 6 / 1.8 / 3 / 30 / 3 |
| `vibroswordDamage` / `vibroswordSpeed` / `vibroswordReach` / `cleaveTargets` / `cleaveRange` / `cleaveMultiplier` | 8.5 / 1.2 / 0.5 / 2 / 2 / 0.6 |
| `vibrobladeDurability` / `vibroswordDurability` | 500 / 700 |
| `guardBladePct` / `guardSwordPct` / `guardStaffPct` / `saberNotch` | 50 / 65 / 60 / 5 |
| `staffDamage` / `staffSpeed` / `staffReach` / `staffChargeTicks` / `staffChargeWindow` / `staffShockTicks` / `staffArcRange` / `droidShockMultiplier` | 6.5 / 1.1 / 1.0 / 40 / 100 / 20 / 3 / 1.5 |

### `armour_m4` and `mods` (`equipment.EquipmentM4Balance`, W4)

Every trait and set-bonus number of 6.2 and every mod number of 6.3, one key each (`watchCostPct`, `watchMeleePct`, `watchSetParryResistPct`, `watchSetBarrierCostPct`, `watchSetTicks`, … `jetCharges`, `jetRefillTicks`, `jetUp`, `jetForward`, `jetFallPct`, `jetLandWindow`). Base defence, toughness, durability and enchantability are fixed at registration (the M3 rule); quality scales a crafted stack's own components (7.4).

### `crafting` (`crafting.CraftingBalance`, W5)

| Key | Default |
|---|---|
| `levelXp` | [0, 120, 360, 800, 1600] |
| `secondDisciplineLevel` / `secondDisciplinePlayerLevel` | 2 / 13 |
| `lowLevelXpMultiplier` / `firstCraftXpPerLevel` | 0.25 / 30 |
| `fineBase` / `masterworkBase` / `finePerLevel` / `masterworkPerLevel` / `pityCrafts` | 0.18 / 0.03 / 0.06 / 0.03 / 10 |
| `qualityWeaponDamage` / `qualityArmour` / `qualityDurability` | [1.0, 1.06, 1.12] / [1.0, 1.15, 1.30] / [1.0, 1.25, 1.5] (passed to `Quality.configure` on load, 7.4) |
| `craftCancelRange` | 6 |
| `salvageReturn` / `salvageXp` | 0.5 / 0.25 |
| `unlearnCost` / `commissionPerLevel` | 500 / 50 |
| `medpacUseTicks` / `medpacHeal` / `medpacTicks` / `medpacInterruptDamage` / `healCooldown` | 16 / 8 / 40 / 4 / 200 |
| `koltoUseTicks` / `koltoHeal` / `koltoCooldown` | 10 / 6 / 300 |
| `stimTicks` / `combatStimPct` / `focusStimPct` / `crashTicks` | 1200 / 8 / 20 / 200 |
| `scannerRange` / `scannerTicks` / `scannerCooldown` | 48 / 200 / 400 |
| `playerGrenadeDamage` / `playerGrenadeRadius` / `stunRadius` / `stunTicks` / `stunDroidTicks` / `grenadeCooldown` | 7 / 3 / 3 / 30 / 60 / 60 |
| `cutterSealTicks` / `cutterDurability` / `repairKitPct` / `repairKitUses` | 60 / 250 / 25 / 3 |
| `lensHoned` / `lensSteady` / `lensResonant` | [6, 9, 12] / [1, 1, 2] / [5, 6.5, 8] |

### `saber` additions (`saber.SaberBalance`, W5)

| Key | Default |
|---|---|
| `trainingSaberDamage` | 2.5 |
| `staffDamagePct` / `staffSpeedBonus` / `staffReach` / `staffGuardDrainPct` / `staffParryTicks` / `staffConeDegrees` / `staffThrowTargets` | −10 / 0.2 / 0.5 / 20 / −1 / 15 / 3 |
| `whirlRange` / `whirlPct` / `whirlWindup` / `whirlRecovery` | 2.5 / 75 / 14 / 20 |
| `staffWhirlStaggerTicks` / `lensMasteryPct` | 10 / 50 |

### `vendors` (`vendor.VendorBalance`, W2)

| Key | Default |
|---|---|
| `priceMultiplier` / `honouredDiscount` / `sellRate` | 1.0 / 0.9 / 0.2 |
| `holdPaymentRange` | 32 (`Credits.HOLD_RANGE`) |
| `creditDropChance` / `creditDrop` | 0.5 / [1, 6] |
| `contractCreditsBase` / `contractCreditsPerLevel` | 25 / 5 |
| `backPayPerMilestone` / `backPayMax` | 100 / 300 |

### `chapter_four` (`quest.story.ChapterFourBalance`, W2)

| Key | Default |
|---|---|
| `waitTicks` / `minSleepTicks` | 24000 / 100 |
| `seekerDescend` / `seekerScanTicks` | 20 / 40 |
| `briefXp` / `partXp` / `salvageRange` / `offTheBooksXp` / `offTheBooksCredits` | 300 / 300 / 8 / 1200 / 100 |
| `arcMissionXp` / `arcPowerMissionXp` / `arcMissionCredits` / `expeditionXp` / `expeditionSp` | 1500 / 2000 / 150 / 2000 / 1 |
| `arcRep` / `arcPowerRep` / `arcPowerTrust` | 40 / 60 / 10 |
| `heartstoneTicks` / `heartstoneGrantTicks` / `heartstoneWaves` / `sepulchreGripTicks` | 1800 / 900 / [0, 600, 1200] / 40 |
| `sabotageXp` / `ambushXp` / `ambushSize` / `ambushRange` / `ambushMinDistance` / `ambushGapDays` / `ambushLeaveRange` / `ambushRetryDays` | 300 / 800 / [3, 4] / [24, 32] / 96 / 2 / 160 / 1 |
| `duelLevel` / `reportXp` / `reportTrust` / `reportResonance` | 14 / 800 / 10 / 300 |
| `spareLight` / `endDark` / `endJediTrust` / `endSithTrust` | 10 / -10 / -15 / 10 |
| `hunterBounties` / `hunterChance` / `hunterCheckTicks` / `hunterSchematicPity` | true / 0.10 / 1200 / 10 |

The duel helper's 1,000 XP is `kaast.helperXp` only.

### `force_speed`, `force_barrier`, `force_choke` (W6)

Every number of 9.1–9.3, one key each, with the M3 naming (`cost`, `cooldownTicks`, `durationTicks`, `speedPct`, …; `channelCost`, `maxTicks`, `boltCost`, `heavyBoltCost`, `meleePct`, `pulseMinTicks`, …; `range`, `coneDegrees`, `damage`, `damageInterval`, `nonHostileShift`, …), plus `pvpEffects` false for each. The 9.5 node numbers sit with their power: `force_barrier.guardiansPatienceFe` / `guardiansPatienceTicks` 2 / 40 and `force_choke.cruelFocusPct` / `cruelFocusWindow` 20 / 40.

### `mandalorian`, `security_droid`, `creatures` (W6)

Every number of 10.1–10.4, one key each, plus `creatures.houndHubExclusion` 64 (3.4).

### `kaast` (`boss.kaast.KaastBalance`, W6 fight keys; W2 reads the reward keys)

| Key | Default |
|---|---|
| `maxHealth` / `perParticipant` / `scalingCap` / `minesPerParticipant` / `huntersPerParticipant` | 500 / 0.6 / 4 / 2 / 1 |
| `armor` / `knockbackResistance` / `controlShorten` / `liftFallBlocks` / `rattleHealth` | 12 / 0.6 / 0.4 / 1 / 20 |
| `phase2At` / `phase3At` / `yieldAt` / `transitionTicks` | 0.65 / 0.30 / 0.10 / 60 |
| P1 `hopTellTicks` / `hopInterval` / `rifleDamage` / `burst` / `burstCooldown` / `rocketInterval` / `rocketTellTicks` / `rocketDamage` / `markBonus` / `nestFallStagger` | 30 / 160 / 6 / 3 / 40 / 300 / 24 / 7 / 0.25 / 40 |
| P2 `mines` / `mineArmTicks` / `mineKneelTicks` / `mineDamage` / `mineRadius` / `netInterval` / `netTellTicks` / `netRootTicks` / `netSaberHits` / `hunters` / `huntersAt` | 6 / 40 / 30 / 8 / 1.5 / 400 / 20 / 60 / 3 / 2 / 0.5 |
| P3 `comboDamage` / `comboWindup` / `guard` / `parryGuardDamage` / `chargeTell` / `slamTellTicks` / `slamDamage` / `slamRadius` / `chokeTicks` | [8, 8, 12] / 10 / 120 / 40 / 14 / 40 / 12 / 3 / 20 (once, phase 3 only) |
| `resetTicks` / `participantLeaveTicks` | 200 / 100 |
| `ownerXp` / `ownerSp` / `ownerCredits` / `resonance` / `helperXp` | 4000 / 2 / 300 / 500 / 1000 |
| `rematchXp` / `rematchSchematicPity` / `rematchCredits` | 800 / 4 / 100 |
| `placeCamp` | true |

---

## 15. Architecture and work split

### 15.1 Phase 0 (lead, before workers start)

**Probes.** The four M4 probes ran (`tools/api-probe/m4_{dims,worldgen,ship,items}.txt`, run 39080604844) and are digested in the scratchpad (`api/m4_*_digest.md`). Findings this spec relies on: data-pack dimensions with the 26.2 `dimension_type` keys (3.1); environment attributes for sky, fog, music, ambience and bed rules (3.1); cross-dimension teleport of a root vehicle carries and remounts riders (4.6); a vehicle with no controlling passenger avoids both floating kicks (4.1); the third-person camera only backs off for `LivingEntity` vehicles (4.6); the 26.2 weapon components (`AttackRange`, `Weapon`, `SwingAnimation`, `UseCooldown`) and the arm pose rules (5). Phase 0 ran one more probe, `m4_followup.txt` (run 39082997019). It confirmed: the floating-kick rules for a vehicle with no controlling passenger (4.1); the logout order, with Fabric's `DISCONNECT` before `PlayerList#remove` (4.9); `FabricItem#allowComponentsUpdateAnimation` (5.1); `EnvironmentAttributes.AMBIENT_PARTICLES` (3.2); that vanilla `BaseFireBlock#inPortalDimension` lights portals only in the overworld and the Nether (3.1); `TicketType` as a record `(timeout, flags)` with `PORTAL`, and `ServerChunkCache#addTicketWithRadius` (3.5, 4.6); and that `moveAcross` remounts riders by UUID only in tracked chunks (4.6). It refuted an `Item.Properties#attackRange` builder (5.2 uses the component) and `Player#createAttributes` adding `CAMERA_DISTANCE`. Still open, each marked `// API-CHECK` with its fallback: `CAMERA_DISTANCE` on players (4.6), the `AmbientParticle` fields (3.2), the attack range component's field name (5.2) and registering a mod ticket type (4.6). Every other use that rests on an unprobed method carries `// API-CHECK` too.

**Seed survey.** The lead logs, for the playthrough seed, where the home world's mentor camps and Courier's Fall land, and, after a first test flight, where both hubs land and the nearest waystone, greater tomb and expedition ring positions (17.3).

The lead writes the following **contracts** with real signatures and minimal bodies so every worker compiles against them from day one. "Real code" means the lead writes the whole body. Signatures are frozen: methods can be added, not changed. Every enum is stored or synced by ordinal and is append only. "Existing" names only what is added to an M1–M3 type.

```java
// com.oldrepublic.planet  [lead: Planets and Clocks, real code, lead files; TerrainPalette written with its overworld fallback, then W1's like the rest]
public final class Planets {
	public static final ResourceKey<Level> SELVAREN, VARKHAST;               // oldrepublic:selvaren, oldrepublic:varkhast
	public enum Planet { HOME, SELVAREN, VARKHAST; ResourceKey<Level> level(); String id(); static @Nullable Planet of(ResourceKey<Level> key); static @Nullable Planet byId(String id); }
	public static boolean isStoryLevel(Level level);                          // overworld or a planet
	public static boolean isHomeWorld(Level level);                           // overworld only (new code uses it; the M1–M3 checks already test the overworld)
	public static boolean isPlanet(Level level);
	public static @Nullable ServerLevel level(MinecraftServer server, Planet planet); // null if the dimension is missing (12.2)
}
public final class Clocks { public static final long TICKS_PER_DAY = 24000; public static long time(MinecraftServer s); public static long day(MinecraftServer s); } // overworld game time
public final class TerrainPalette {
	public record Layers(BlockState top, BlockState fill, BlockState base) { public static final Layers DEFAULT; }
	public static Layers at(ServerLevel level, BlockPos pos);                 // by biome; grass/dirt/stone fallback
	public static void register(ResourceKey<Biome> biome, Layers layers);      // W1 registers the six planet biomes
}
public final class PlanetHubs {                                              // W1 body
	public static @Nullable BlockPos hub(MinecraftServer s, Planets.Planet p);   // the hub anchor if placed
	public static CompletableFuture<BlockPos> ensure(MinecraftServer s, Planets.Planet p); // places once per server (3.5); completes on the server thread
	public static List<BlockPos> padBays(MinecraftServer s, Planets.Planet p);
	public static @Nullable BlockPos medbay(MinecraftServer s, Planets.Planet p);
}
public final class PlanetSites {                                             // W1 body: runtime planet sites (3.4)
	public enum Site { SINGING_HOLLOW, DUSKGLASS_RIFT, WAYFARER_CACHE, HUNTING_CAMP, AMBUSH_ONE, AMBUSH_TWO, HEARTSTONE, SEPULCHRE } // HEARTSTONE and SEPULCHRE are shared: one server-wide position
	public static @Nullable GlobalPos ensure(ServerPlayer owner, Site site, Planets.Planet planet); // null = not yet placed (retries; the Calling points to the ring)
	public static @Nullable GlobalPos find(ServerPlayer owner, Site site);
}
// Placeholder planets: data/oldrepublic/dimension/{selvaren,varkhast}.json use the overworld noise settings and one
// vanilla biome each (forest, badlands); their dimension types copy the overworld's with their own sky and fog colours.
// W1 replaces both with the real noise settings and six biomes.

// existing com.oldrepublic.world.site
SiteKind appends: WAYSTONE, GREATER_TOMB, PLANET_HUB, HEARTSTONE, SEPULCHRE, EXPEDITION, WAYFARER_CACHE, HUNTING_CAMP, SHIP_CRADLE
WorldSites.Data gains count(SiteKind kind, ResourceKey<Level> dimension); MAX_RECORDS becomes 1,024 (count(kind) stays and counts all dimensions)

// existing com.oldrepublic.quest and mission  [lead: real code]
Guidance.Request(int priority, Vec3 target, int callingVariant, String source, @Nullable ResourceKey<Level> dimension) // the M3 four-argument constructor stays (null = the player's level)
QuestEffects.CALLING_OFFWORLD = 9                                             // the off-world cue and action-bar line (3.6), done; client CallingFx has the case
MissionData.Active gains nested Where(ResourceKey<Level> site, ResourceKey<Level> board) // default overworld; siteGlobal() and boardGlobal() combine it with the BlockPos fields
JournalEntries appends the 8.9 ids and chapter_header.4; CHAPTER_FIRSTS gains A_PRICE

// com.oldrepublic.travel  [lead: the contract files ShipState, Destination, ShipRecord, TravelData, TravelAttachments, TravelLocks
//                          and moveAcross; W3 owns every other file, including ShipRegistry, Ships and TravelService]
public enum ShipState { HULK, GROUNDED, READY, LAUNCHING, IN_TRANSIT, LANDING, DISABLED }
public enum Destination { HOME, GREENWATCH, PROVING, HUNTING_CAMP; String id(); static @Nullable Destination byId(String id); }
public record ShipRecord(UUID owner, UUID body, GlobalPos pos, float yaw, ShipState state, int hull, int fuel, int upgrades, List<ItemStack> hold, List<UUID> crew, String hullCode, Optional<Flight> flight) {
	public static final int MAX_HOLD = 36, MAX_CREW = 3;                    // plus the UPGRADE_* bits, hulk() and the with*() copies
	public record Flight(Destination from, Destination to, int phase) {}      // phase 0–5: Spool, Climb, Jump, Hyperspace, Descent, Settle
}
public record TravelData(Set<Destination> unlocked, Optional<GlobalPos> homePad, Optional<GlobalPos> lastPad, long recallDay, long towDay, Optional<UUID> crewOf) {} // attachment oldrepublic:travel (12.1)
public final class TravelLocks { public static Optional<ShipState> riddenShipState(ServerPlayer p); } // the "not while flying" AbilityLocks entry (9)
public final class ShipRegistry {                                            // W3's file; global attachment oldrepublic:ships
	public static Optional<ShipRecord> of(MinecraftServer s, UUID owner);
	public static Optional<ShipRecord> byBody(MinecraftServer s, UUID body);
	public static void put(MinecraftServer s, ShipRecord record);
	public static Data snapshot(MinecraftServer s); public static void commit(MinecraftServer s, Data data); // detached Data for tests (the WorldSites pattern)
	public static final class Data { /* get, put, remove, all, byBody, copy; CODEC */ }
}
public final class Ships {                                                   // W3 body
	public static @Nullable ShipRecord placeHulk(ServerPlayer owner, BlockPos near); // the cradle and a HULK body (4.2); null = retry later
	public static ShipRecord give(ServerPlayer owner);                        // command and test path: READY, full tank
	public static boolean isAboard(Player p);
	public static @Nullable Entity body(MinecraftServer s, UUID owner);       // loaded body or null
}
public final class TravelService {                                           // W3 body
	public enum Refusal { NONE, NOT_OWNER, NOT_READY, NO_FUEL, HULL, LOCKED, COMBAT, DISABLED_BY_CONFIG, BUSY, HERE, UNAVAILABLE } // HERE, UNAVAILABLE appended by W3
	public static Refusal canLaunch(ServerPlayer owner, Destination to);
	public static Refusal launch(ServerPlayer owner, Destination to);         // starts the sequence (4.6)
	public static void recall(ServerPlayer owner, GlobalPos pad);             // 4.8, including the tow
	public static boolean isUnlocked(ServerPlayer p, Destination d);
	public static void unlock(ServerPlayer p, Destination d);                 // W2 calls this from the story
	public static @Nullable GlobalPos pad(ServerPlayer p, Destination d);
	public static Entity moveAcross(Entity root, ServerLevel to, Vec3 pos, float yaw); // [lead: real code, frozen] the one cross-dimension move; returns the new body;
	                                                                          // the caller holds a chunk ticket on the destination first (4.6)
}

// com.oldrepublic.quest.story  [lead: ChapterFour, the enums and the StoryData.Chapter4 record; W2 owns ChapterFourService and the rest]
public enum ChapterFourStage { NOT_STARTED, MARKED, SEEKER, REPAIRS, FIRST_FLIGHT, WORLD_ARC, HUNTED, CHALLENGED, DUEL, AFTERMATH, COMPLETE }
public enum KaastOutcome { SPARED, STRIPPED, ENDED }
public final class ChapterFour {                                             // real code; reads work on both sides
	public static final int MAX_CLUE = 3;
	public static ChapterFourStage stage(Player p);
	public static boolean atLeast(Player p, ChapterFourStage stage);
	public static @Nullable Planets.Planet arcPlanet(Player p);
	public static int clues(Player p);                                        // the clue mask
	public static boolean hasClue(Player p, int clue);
	public static void addClue(ServerPlayer p, int clue);                    // records the bit, then calls ChapterFourService.onClue; W6 calls it (8.5)
}
// StoryData.Chapter4(stage, waitSince, Arc(planet, missionFlags, expedition), Hunt(clues, sabotageDone, ambushDay, ambushes)); W2 adds more nested records (12.1)
// ChapterFourService (W2): one public method per QuestHooks call below, and package-private onClue

// existing com.oldrepublic.quest.QuestHooks  [lead: real forwarding to ChapterFourService; the named owner fires each]
public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first);   // W3 fires on Settle
public static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to);   // W3
public static void onShipPartFitted(ServerPlayer owner, int socket);                       // W3 (0 hull, 1 core, 2 motivator, 3 nav)
public static void onCrafted(ServerPlayer p, Identifier schematic, int quality, boolean first); // W5
public static void onDisciplineLevel(ServerPlayer p, String discipline, int level);         // W5
public static void onPurchase(ServerPlayer p, String vendor, ItemStack bought, int price);  // W2 (its own; listed for others)
public static void onWaystoneCleansed(ServerPlayer p, BlockPos pos, boolean heartstone);   // W1
public static void onHunterKilled(ServerPlayer p, LivingEntity hunter, boolean owned);     // W6
public static void onDroidKilled(ServerPlayer p, LivingEntity droid);                      // W6
public static void onBeastKilled(ServerPlayer p, LivingEntity beast, String kind);         // W6
public static void onExpeditionDone(ServerPlayer p, PlanetSites.Site site);                // W1
// onBossPhase / onBossYielded / onBossDefeated are reused with bossId "kaast"

// com.oldrepublic.crafting  [lead: Discipline, Quality, Schematic, Schematics, CraftingData, CraftingAttachments; W5 the rest, Crafting's bodies included]
public enum Discipline { ARMSTECH, ARMOURCRAFT, ARTIFICE, MEDTECH; String id(); static @Nullable Discipline byId(String id); }
public enum Quality { STANDARD, FINE, MASTERWORK; float weapon(); float armour(); float durability(); static Quality of(int ordinal);
	static void configure(float[] weapon, float[] armour, float[] durability); } // defaults 1.0/1.06/1.12, 1.0/1.15/1.30, 1.0/1.25/1.5; W5's balance calls configure on load
public record Schematic(Identifier id, Optional<Discipline> discipline, int level, List<Input> inputs, ItemStack output, boolean quality, int craftTicks, int xp, Learned learned) {
	public record Input(Either<Item, TagKey<Item>> what, int count) {}
	public enum Learned { AUTO, ITEM, TRAINER }
}
public final class Schematics { public static @Nullable Schematic get(Identifier id); public static Collection<Schematic> all(); public static void replace(Collection<Schematic> all); } // W5's reload listener calls replace; synced
public final class Crafting {
	public static boolean has(ServerPlayer p, Discipline d);
	public static int level(ServerPlayer p, Discipline d);
	public static boolean knows(ServerPlayer p, Identifier schematic);
	public static void teach(ServerPlayer p, Identifier schematic);           // W2 rewards and vendors call it
	public static Quality roll(ServerPlayer p, Schematic s);                  // pity included
	public static Quality roll(ServerPlayer p, Schematic s, RandomSource random); // tests pass a seeded source
	public static Quality quality(ItemStack stack);
	public static ItemStack withQuality(ItemStack stack, Quality q);          // W4 reads quality through its own stat code
}
public record CraftingData(List<Progress> disciplines, List<Discipline> unlearned, Set<Identifier> known, Set<Identifier> firstCrafts, long chosenAt) {} // attachment oldrepublic:crafting (12.1)

// com.oldrepublic.economy  [lead: real code]
public final class Credits {
	public static final int BUNDLE = 100;
	public static final double HOLD_RANGE = 32;                               // the ship hold counts when its body is this close (7.7)
	public static int count(ServerPlayer p, boolean includeShipHold);
	public static boolean take(ServerPlayer p, int amount, boolean includeShipHold); // all or nothing, exact change returned
	public static void give(ServerPlayer p, int amount);                      // bundles first, then chits; drops for the owner if full
	public static long value(ItemStack stack);
}

// com.oldrepublic.equipment  [lead: ModSlot, FittedMods and the class swap in enemy/EnemyItems; W4 owns the rest, stubs included]
public class BlasterItem extends Item { public enum Kind { PISTOL, RIFLE, REPEATER } public Kind kind(); } // equipment.blaster; registered in place for blaster_pistol and blaster_rifle (Milestone 7 appended SCATTER and CARBINE)
public record BlasterHeat(float heat, long lastShot, long lockedUntil, int freeShots) {}  // equipment.blaster; component oldrepublic:blaster_heat
public class VibroWeaponItem extends Item { }                                  // equipment.melee; registered in place for vibroblade
public enum ModSlot { ARMOUR, PACK, BARREL, CELL, EDGE }                       // equipment.mod
public record FittedMods(Map<ModSlot, ItemStack> mods) { public static final int MAX = 3; } // equipment.mod; component oldrepublic:mods
public final class Mods {                                                     // equipment.mod; W4 (the stub gives every stack one ARMOUR slot)
	public static List<ModSlot> slotsOf(ItemStack gear);
	public static boolean fits(ItemStack gear, ModSlot slot, ItemStack mod);
	public static FittedMods fitted(ItemStack gear);
	public static ItemStack fit(ItemStack gear, ModSlot slot, ItemStack mod); // returns the gear; W5's Modify tab calls it
	public static ItemStack remove(ItemStack gear, ModSlot slot);             // returns the removed mod
}
// Not Phase 0 (W4 does them with its sets, 6.1): the ArmourSet appends, SetBonusRules.Worn as int[] counts, EquipmentTraits' Extra
// existing enemy/BlasterBolt.Style appends PLAYER (green-white), MANDALORIAN (gold), with renderer palettes

// com.oldrepublic.combat  [lead: real code]
public final class WeaponActions {
	public interface Handler { void onAction(ServerPlayer p, ItemStack held, boolean pressed); }
	public static void register(Predicate<ItemStack> held, Handler handler);  // W4 (blasters, electrostaff), W5 (saberstaff)
	public static boolean dispatch(ServerPlayer p, boolean pressed);         // the first handler whose predicate matches the main-hand stack
}
public record WeaponActionPayload(boolean pressed) {}                         // C2S; the client key is client/combat/WeaponActionKey (N, press and release)

// existing com.oldrepublic.saber
SaberConfig(crystal, color, emitter, grip, pommel, Optional<CrystalAttunement> second, Optional<Identifier> lens) // optionalFieldOf; old stacks decode unchanged
public class SaberStaffItem extends LightsaberItem { }                        // W5 body
public class TrainingSaberItem extends LightsaberItem { }                     // W5 body
// HiltParts.Grip appends STAFF; SaberItems.GRIP_STAFF registers grip_staff

// com.oldrepublic.vendor  [lead: VendorEntry; W2 the rest]
public record VendorEntry(ItemStack item, int price, Optional<Faction> faction, int tier, Optional<Discipline> discipline, int level, int dailyLimit) {}
public final class Vendors { public static void open(ServerPlayer p, Entity vendor, Identifier stock); }

// W6 stubs in W6's packages (W6 owns the files and registers the entity types)
public final class Hunters { public static List<LivingEntity> spawnAmbush(ServerLevel l, BlockPos at, int count, @Nullable UUID owner, boolean withDroid); } // enemy.mandalorian
public final class Droids { public static @Nullable LivingEntity spawnSecurity(ServerLevel l, BlockPos at, @Nullable UUID owner, boolean imperialGuard); } // enemy.droid; null = cannot spawn
public final class HuntingCamps { public static @Nullable GlobalPos place(ServerPlayer owner); }   // boss.kaast; places through W1's PlanetSites
public final class KaastFight { public static void resolve(ServerPlayer owner, KaastOutcome outcome); } // boss.kaast; W2's dialogue action calls it

// com.oldrepublic.registry  [lead: real code]
public final class M4Items { /* the plain items several workers name (Registries below) */ }
public final class M4Components { BLASTER_HEAT, MODS, QUALITY /* int 0–2 */, SCHEMATIC /* Identifier */, NAMED /* String */ } // nav_data stays W3's
public final class M4Contracts { public static void init(); }                 // called from OldRepublicMod after BossFeature

// existing DangerTellTable: a variant mask per effect: hunter_tell every variant, droid_tell 0, beast_tell 0 and 1, kaast 1 (mine arm) and 3 (slam tell)
// existing ClientConfig: shipCamera, hyperspaceOverlay, blasterReticle, planetAmbience and their /oldrepublicfx toggles (done)
// no shared particle class: each owner registers its 13.2 types in its own <Feature>Particles

// Entry points and mixin configs, one per worker (each worker owns its own):
//   W1 planet.PlanetFeature, client.planet.PlanetClient, oldrepublic-planet.mixins.json (mixin.planet)
//   W2 vendor.VendorFeature, client.vendor.VendorClient
//   W3 travel.TravelFeature, client.travel.TravelClient, oldrepublic-travel.mixins.json (mixin.travel)
//   W4 equipment.EquipmentFeature (kept)
//   W5 crafting.CraftingFeature, client.crafting.CraftingClient, oldrepublic-crafting.mixins.json (mixin.crafting)
//   W6 force.ForceM4Feature, enemy.FoesFeature, client.force.ForceM4Client, client.enemy.FoesClient, oldrepublic-foes.mixins.json (mixin.foes)
// M4ClientTest runs the m4/{Planet,Travel,Gear,Crafting,Foes,Story}ClientSteps stubs (17.2).

// Additions after Phase 0 (the lead makes them on W6's request; signatures as below)
ForceReactions.Handler gains default int chokeTicks(LivingEntity target, int requested)  // returns requested; Kaast overrides (9.3, 10.5); done
ForceReactions.chokeTicks(LivingEntity target, int requested)                         // the minimum over all handlers, never below 0; done
PowerAnimations.Kind appends BARRIER, CHOKE; HumanoidModelMixin poses them (9.2, 9.3)
```

**Registries.** `registry/M4Items` (the lead's) holds only the plain items more than one worker names: `credit_chit` (stacks to 99), `credit_bundle` (64), `aurelite_shard`, `duskglass_shard`, `alloy_plate`, `masterwork_fragment`, `power_core`, `hyperdrive_motivator`, `nav_computer`, `hyperfuel_cell`, `passenger_seat`, `cargo_bay_extension`, `reinforced_plating`, `fuel_tank_extension`, the 12 mod items including `jet_pack`, `hollowhorn_hide`, `hollowhorn_horn`, `ash_hound_fang` and `droid_actuator`. Phase 0 also registered `grip_staff` (`SaberItems.GRIP_STAFF`). Every item that needs its own class (the schematic item, medpacs, the bench block, `landing_beacon`, blocks, weapons, armour, lenses, `frag_grenade`, `kaast_pattern` and so on) is registered by its owner, and so is every entity type: W3 `courier_shuttle`; W6 the hunters, droids, beasts, seeker droid, Rattle, Kaast, projectiles and mines; W2 the hub NPCs. Other workers reach those entities through the stubs (`Ships`, `Hunters`, `Droids`). Data loaders (schematics, vendor stock, missions) skip an entry whose item id is unknown, with a warning, so each worker's part loads before the others are committed; W2's rewards look items up by id. The id's owner in 16.1 writes its lang, models and textures.

**Process files** (written in Phase 0). `docs/WORKER_M4.md` (the M3 worker rules, plus: every cross-dimension move goes through `TravelService.moveAcross`; no code keeps an entity reference across a jump; every new saved position is a `GlobalPos`; no new overworld-only check without `Planets.isHomeWorld`), `tools/ownership_m4.json` (15.2), `tools/check_m4_ids.py` (ids of section 16 against registrations, lang, sounds and `events_m4.json`; its id lists change only together with section 16), `tools/assets/sounds/events_m4.json` (13.4), and the `ContractsM4GameTests` class (17.1). CI's `m3-checks` job is renamed `checks` and runs both the M3 and M4 checks.

**Phase 0 code changes to existing systems** (3.6): `Planets` and `Clocks` for new code (the ~25 existing overworld checks are already correct home-world checks and were not renamed; `StoryService.now` is unchanged, because a data-pack dimension's game time is the overworld's), the `MissionData.Active.where` record, `Guidance.Request.dimension` and the off-world Calling, the `WorldSites` per-dimension count, the `TerrainPalette` hook in `RuntimeSites` and `adaptTerrain`, the item class swaps for the three reused ids, the `BlasterBolt` styles, `SaberConfig`'s two optional fields, `Grip.STAFF`, and the `DangerTellTable` masks. The poacher loot change (nuggets to credit chits, and the captain's 10% `vibroblade`) is done (12.5). M1–M3 game tests must stay green after Phase 0.

### 15.2 Workers and ownership

Six workers. **A file has exactly one owner.** Shared files (lead) change only through requests in a worker's report.

| Worker | Owns (code) | Owns (data, assets, docs) | Delivers |
|---|---|---|---|
| **W1 worlds** | `com.oldrepublic.planet.**` except the lead's `Planets` and `Clocks`; `com.oldrepublic.world.**` (M4 additions; M3 W1 files stay W1's); `client/{planet,world}/**`; `mixin/{planet,world}/**` and `oldrepublic-{planet,world}.mixins.json` | dimensions, dimension types, noise settings, biomes, features, structure sets and pools for waystones and greater tombs; the hub, expedition, camp and ambush templates; `tools/assets/gen_structures_m4.py` and sidecars; `gen_textures_planet.py`; `sounds/world_m4.py`; lang `planet.json`; loot `chests/**` (M4: `greater_tomb`, `wayfarer_cache_m4`) except `kaast_stash`, and `blocks/**` except the beacon, bench, workbench and camp gate tables; damage type `duskglass_cut`; particles `ash_flake`, `aurelite_glint`, `duskglass_shard`; `docs/design/planets.md` | Both planets (3.1–3.3), sites and templates (3.4), hubs' placement (3.5), `TerrainPalette` planet rows, ambience, ashfall, the second moon, waystone and Heartstone blocks, expeditions and their puzzles. Nothing for portals: vanilla refuses them (3.1) |
| **W2 story, vendors, economy** | `com.oldrepublic.quest.**` except the lead's `QuestHooks`, `Guidance` and `quest/story/ChapterFour`; `com.oldrepublic.mission.**`, `identity.**`, `dialogue.**`, `mentor.**`; `vendor.**` except `VendorEntry`; `client/{quest,mission,identity,dialogue,mentor,vendor}/**` except `client/quest/JournalTabs`; `mixin/{quest,identity}/**` | lang `quest.json`, `mission.json`, `identity.json`, `vendor.json`; every dialogue file except `pad_terminal.json` (W3) and the M3 holocron and hoard dialogues, including the `trainer_*` files (W5 supplies their lines by request); `data/oldrepublic/mission/*.json` M4 additions; `data/oldrepublic/vendor/*.json`; hub NPC portraits and skins (`gen_textures_quest.py`, `gen_textures_hub.py`); `sounds/story_m4.py`; loot `gameplay/**` except `echo_cache`, and `blocks/marker_beacon`; the assets of `credit_chit`, `credit_bundle`, `kaast_pattern`, `tomb_relic`, `sealed_contract` and `marker_beacon` | Chapter Four (8), `ChapterFourService`, hub hosts, apprentices, acolytes and guards (their entity types too), vendors and the vendor screen, the contract terminal, credits flow, enemy credit drops and back pay, planet contracts and the new `escort` and `spar` objective types, the seeker droid scene, every Chapter Four reward and the Kaast ending scenes |
| **W3 ships and travel** | `com.oldrepublic.travel.**` except the lead's contract files `ShipState`, `Destination`, `ShipRecord`, `TravelData`, `TravelAttachments` and `TravelLocks`; `TravelService` is W3's file, but its `moveAcross` is the lead's frozen code; `client/travel/**`; `mixin/travel/**` and `oldrepublic-travel.mixins.json` | the ship model and texture (`gen_textures_ship.py`), `gen_structures_ship_m4.py` and sidecars, the destination map art; `sounds/ship.py`; lang `travel.json`; dialogue `pad_terminal.json`; loot `blocks/landing_beacon`; particles `engine_exhaust`, `spark_shower` | The courier shuttle (its entity type too), hulk and repairs, fuel, wear, the flight sequence and its camera, hyperspace overlay and flight HUD, pads and bays, the pad terminal, the landing beacon, recall and tow, crew, `ShipRegistry` rules, ship screen and destination map, ship commands |
| **W4 gear** | `com.oldrepublic.equipment.**` (M4 additions and the M3 files) except the lead's `mod/ModSlot` and `mod/FittedMods`; `client/equipment/**`; `mixin/equipment/**` | lang `equipment.json` additions; `gen_textures_equipment.py` additions (4 sets, mods, blasters, vibroweapons, staff, jet pack), held item models; `sounds/equipment.py`, `sounds/gear.py`; particles `steam_vent`, `flame_jet`; recipes for vanilla-crafted gear (none expected) | Blasters, vibroweapons and the electrostaff (5.1–5.4), the four armour sets with the `ArmourSet` appends, `SetBonusRules.Worn` counts and `EquipmentTraits.Extra` (6.1–6.2), mod effects and `Mods` (6.3), the jet pack (6.4), heat gauge, reticle, arm poses, Weapon Action handlers |
| **W5 crafting and saber** | `com.oldrepublic.crafting.**` except the lead's `Discipline`, `Quality`, `Schematic`, `Schematics`, `CraftingData` and `CraftingAttachments`; `com.oldrepublic.saber.**` (M4 additions and the M3 files); `client/{crafting,saber}/**`; `mixin/{crafting,saber}/**` | `data/oldrepublic/schematic/*.json`; lang `crafting.json`, `saber.json` additions; `gen_textures_crafting.py` (bench, consumables, tools, lenses, staff), `gen_textures_saber.py` additions (staff hilts, training saber); `sounds/crafting.py`, `sounds/saber.py` additions; loot `blocks/engineering_bench`, `blocks/lightsaber_workbench` | Engineering Bench and screen (7.1), disciplines and the Crafting tab (7.2), schematics (7.3), quality (7.4), salvage (7.5), consumables and tools including the new `frag_grenade` item (7.6), the training saber, saberstaff and lenses (5.5–5.7), the trainers' lines and discipline actions |
| **W6 Force and foes** | `com.oldrepublic.force.{speed,barrier,choke}.**` and `force/ForceM4Feature`; `com.oldrepublic.enemy.{mandalorian,droid,creature}.**` and `enemy/FoesFeature`; `com.oldrepublic.boss.kaast.**`; `client/force/{speed,barrier,choke}/**`, `client/force/ForceM4Client`, `client/enemy/{mandalorian,droid,creature}/**`, `client/enemy/FoesClient`, `client/boss/kaast/**`; `mixin/foes/**` and `oldrepublic-foes.mixins.json` | `skill_tree/**` (all of it in M4); lang `force.json`, `skill.json`, `enemy.json`, `boss.json` additions; `gen_textures_foes.py` (hunters, droids, beasts, Kaast and Rattle); `sounds/force_m4.py`, `sounds/enemy_m4.py`; loot `entities/{mandalorian_hunter,security_droid,hollowhorn,ash_hound,seeker_droid,kaast_droid,torvin_kaast,hunter_rocket,hunter_net,camp_mine}`, `chests/kaast_stash`, `blocks/camp_gate`; damage types `force_choke`, `wrist_flamer`, `mandalorian_rocket`, `camp_mine`, `jet_slam`; particle `held_dust` | Speed, Barrier, Choke and Lift-for-paths (9), the four enemies and their natural spawns (10.1–10.4), Kaast, the camp fight and the `/oldrepublic kaast` commands (10.5), the seeker droid and Rattle; the entity types of all of them |

**Shared and inherited files:**

| Files | Owner | Notes |
|---|---|---|
| `registry/{M4Items,M4Components,M4Contracts}`, `combat/**`, `planet/{Planets,Clocks}`, `economy/**`, the travel contract files and `TravelService.moveAcross`, `quest/{QuestHooks,Guidance}`, `quest/story/ChapterFour`, the crafting contract files, `equipment/mod/{ModSlot,FittedMods}`, `vendor/VendorEntry`, `client/quest/JournalTabs` | lead | the contract files inside worker packages are named in each worker's `except` list |
| M1–M3 files of packages no M4 worker continues: `enemy/BlasterBolt`, `enemy/trooper/FragGrenade`, `enemy/construct/**`, `force/ForceReactions`, `client/force/PowerAnimations`, `mixin/force/HumanoidModelMixin`, `boss/BossCommands`, the poacher loot tables | lead | changed on a worker's request (5.1, 7.6, 9.2, 9.3) or by the lead (12.5) |
| `data/minecraft/tags/**`, `data/oldrepublic/tags/**` new tag files | lead creates; owners fill their own tags by request | |
| `src/gametest/resources/**` | lead | workers request fixtures |
| `src/gametest/java/.../m4/<Feature>ClientSteps.java` | the feature's worker | stubs by the lead |
| Existing test classes | as M3, plus `TravelGameTests` W3, `CraftingGameTests` W5, `GearGameTests` W4, `PlanetGameTests` W1, `StoryM4GameTests` W2, `ForceM4GameTests` and `FoesGameTests` W6 | |
| `tools/assets/gen_structures.py`, `gen_sounds.py`, `tools/check_*.py`, `tools/assets/sounds/events_m4.json` | lead | |
| Balance sections | the section's owner (14) | `travel` and `ship` are `travel.ShipBalance` (W3) |

Notes on boundaries:

- **Cross-dimension moves:** only `TravelService.moveAcross` (the lead's code in W3's file) moves anything between levels. W2's story, W6's camp and every test call it or `TravelService.launch`.
- **Hubs:** W1 places the hub and its anchors; W2 spawns and owns everything that talks or sells there; W3 owns the pad bays as landing spots and the pad terminal.
- **Ship parts:** W5's schematics make them; W3's sockets take them.
- **Mods:** W4 owns what a mod does and which gear has which slots (`Mods`); W5 owns the bench tab that fits them and the schematics that make them.
- **Saberstaff and lens:** W5. Their nodes in `skill_tree/saber.json` are W6's file, written to W5's request.
- **Story-granted powers:** W2 calls `Skills.grant` (existing; it delegates to `SkillService.grant`) for Barrier, Choke and Speed at the story moments, under the slot rule in 9; W6 implements them.
- **Kaast:** W6 runs the fight, the yield and `KaastFight.resolve`; W2 owns the dialogue, the scenes and every reward.
- **Stubs while parallel:** W2 needs `TravelService`, `PlanetHubs`, `PlanetSites`, `Ships`, `Hunters`, `Droids`, `HuntingCamps` and `Crafting.teach`; the Phase 0 stubs return safe values (`null` for a site or body that is not there yet, an empty list, or a refusal). W3 needs `PlanetHubs.ensure` (stub: the surface at (0, 0), with four bays 12 blocks off). W5 needs `Mods` (stub: every stack has one `ARMOUR` slot). W6 needs `PlanetSites` for the camp (stub: `null`, not yet placed), so W6 tests the fight on a fixture placed with `RuntimeSites.placeAt`. Every worker tests with commands until integration.

### 15.3 Integration order (lead)

1. Phase 0 committed and compiling, `ContractsM4GameTests` and all M1–M3 tests green, the follow-up probe read, the seed survey logged, `WORKER_M4.md` written.
2. Workers deliver in parallel in **one working tree**, with no per-worker branches. Workers never commit; the lead reviews each worker's files and commits them, and CI builds the lead's branch. `check_ownership.py` with `ownership_m4.json` reports any file a worker changed outside its globs.
3. Review and commit order **W1 → W3 → W4 → W5 → W6 → W2**: the worlds first, then the ship that reaches them, the gear and crafting that equip the player, the powers and foes that test them, and the story last. Each part loads without the later ones, because data loaders skip unknown item ids and the creature spawns are W6's (15.1).
4. Fix compile errors on CI; send logic issues back.
5. Run every server and client test (`M4ClientTest` runs the six `m4/*ClientSteps`); re-run the M1–M3 playthrough and confirm it stays green.
6. PlaythroughTest extension (17.3).
7. Update the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `VFX_SFX_SPEC`, `AUDIO_BRIEF`, `DECISIONS`), including any cuts from section 1.

---

## 16. Shared identifiers

All ids are in the `oldrepublic` namespace. "Existing, reused" ids come from M1–M3: they are not registered again, get no new lang key and are not in `tools/assets/sounds/events_m4.json` (`tools/check_m4_ids.py` enforces this, and its id lists change only together with this section). In 16.1 the owner is the worker who writes the id's lang, models and textures; the plain items several workers name are registered in the lead's `registry/M4Items` (15.1), and every other id by its owner.

### 16.1 Items, blocks, entities, data

| Kind | Ids | Owner |
|---|---|---|
| Planet blocks | `silverleaf`, `aurelite_ore`, `deepslate_aurelite_ore`, `ashroot`, `duskglass_cluster`, `budding_duskglass`, `waystone`, `waystone_corrupted`, `heartstone`, `sealed_plating`, `hub_spawner` (a `site_spawner` variant), `camp_gate` | W1 (`camp_gate` W6) |
| Planet items | `silverleaf` (block item), `aurelite_shard`, `ashroot` (block item), `duskglass_shard` | W1 |
| Creature drops | `hollowhorn_hide`, `hollowhorn_horn`, `ash_hound_fang`, `droid_actuator`, `raw_hollowhorn`, `cooked_hollowhorn` | W6 |
| Ship | `courier_shuttle` (entity), `landing_beacon` (block), `pad_terminal` (block, the hub pads' recall terminal, 3.5), `power_core`, `hyperdrive_motivator`, `nav_computer`, `hyperfuel_cell`, `passenger_seat`, `cargo_bay_extension`, `reinforced_plating`, `fuel_tank_extension` | W3 (items' recipes W5) |
| Weapons | `blaster_pistol`, `blaster_rifle`, `vibroblade` (**existing, reused**; class swapped), `heavy_repeater`, `vibrosword`, `electrostaff`, `kaast_longshot` (named rifle) | W4 |
| Armour | `watch_battle_{helmet,chest,legs,boots}`, `sith_war_{helmet,chest,legs,boots}`, `mandalorian_{helmet,chest,legs,boots}`, `spacer_{cap,jacket,trousers,boots}`, `kaast_helmet`, `kaast_chest` | W4 |
| Mods | `mod_plating`, `mod_weave`, `mod_attunement`, `mod_conduit`, `mod_stabiliser`, `jet_pack`, `mod_focused_barrel`, `mod_heavy_barrel`, `mod_cooling_cell`, `mod_overcharged_cell`, `mod_keen_edge`, `mod_shock_edge` | W4 effects, W5 schematics |
| Crafting | `engineering_bench` (block), `alloy_plate`, `masterwork_fragment`, `schematic`, `medpac`, `kolto_injector`, `combat_stim`, `focus_stim`, `fusion_cutter`, `scanner`, `stun_grenade` (the item and its thrown entity type), `repair_kit`, `frag_grenade` (a **new** item that throws the existing M3 `frag_grenade` entity, which is reused) | W5 |
| Saber | `lightsaber_staff`, `training_saber`, `training_saber_red`, `grip_staff` (registered in Phase 0 as `SaberItems.GRIP_STAFF`; the `grip_<id>` convention), `lens_honed`, `lens_steady`, `lens_resonant` | W5 |
| Economy | `credit_chit`, `credit_bundle` (both in `M4Items`), `kaast_pattern` | W2 |
| Story | `marker_beacon` (Kaast's challenge, a block), `contract_terminal` (block, the hubs' planet contract board, 3.5), `tomb_relic` (the Proving and Both Doors), `sealed_contract` (a keepsake, 8.5) | W2 |
| Entities | `mandalorian_hunter`, `security_droid`, `hollowhorn`, `ash_hound`, `seeker_droid`, `kaast_droid`, `torvin_kaast`, `hunter_rocket`, `hunter_net`, `camp_mine` | W6 |
| Hub NPCs | `hub_host` (Ansa, Kaddor by variant), `apprentice`, `acolyte_hub`, `proving_guard`, `vendor` (variant by stock id) | W2 |
| Spawn eggs | `mandalorian_hunter_spawn_egg`, `security_droid_spawn_egg`, `hollowhorn_spawn_egg`, `ash_hound_spawn_egg` | W6 |
| Data | dimensions `selvaren`, `varkhast`; dimension types and noise settings of the same names; biomes `selvaren_highwood`, `selvaren_meadow`, `selvaren_tarn`, `varkhast_redrock`, `varkhast_ashfall`, `varkhast_tomb_valley`; structures `waystone`, `greater_tomb`; structure sets `waystones`, `greater_tombs` | W1 |
| Templates | `planet/waystone`, `planet/waystone_corrupted`, `greater_tomb/{gate,descent,crypt_a,crypt_b,path,dig_camp}`, `hub/greenwatch`, `hub/proving`, `hub/sepulchre`, `planet/heartstone`, `expedition/singing_hollow_{surface,grotto}`, `expedition/duskglass_rift_{surface,rift}`, `planet/wayfarer_cache`, `boss/hunting_camp`, `mission/ambush_rocks`, `ship/cradle`, `ship/beacon_pad` | W1 (`ship/*` W3) |
| Schematics (`data/oldrepublic/schematic/`) | common: `power_core`, `nav_computer`, `hyperfuel_cell`, `landing_beacon`, `repair_kit`; Armstech: `blaster_pistol` (1), `vibroblade` (1), `mod_focused_barrel` (1), `mod_cooling_cell` (1), `mod_keen_edge` (1), `blaster_rifle` (2), `mod_heavy_barrel` (2), `frag_grenade` (2), `passenger_seat` (2, Armstech only), `vibrosword` (3), `electrostaff` (3), `mod_overcharged_cell` (3), `mod_shock_edge` (3), `stun_grenade` (3), `heavy_repeater` (4); Armourcraft: `spacer_*` (1), `mod_plating` (1), `mod_weave` (1), `alloy_plate` (2), `cargo_bay_extension` (2), `watch_battle_*` (3, item), `sith_war_*` (3, item), `mod_stabiliser` (3), `jet_pack` (3), `reinforced_plating` (3), `mandalorian_*` (4, item); Artifice: `grip_staff` (2), `lens_*` (2), `mod_attunement` (2), `mod_conduit` (2), `fuel_tank_extension` (2); Medtech: `medpac` (1), `fusion_cutter` (1), `combat_stim` (2), `focus_stim` (2), `scanner` (2), `kolto_injector` (3); level 5 Masterwork-only recipes of each discipline's best item using `masterwork_fragment` (`trainer`, 7.8). Unmarked schematics are `auto` at their level (7.3). Inputs are W5's data, not listed here (7.3) | W5 |
| Vendor stock (`data/oldrepublic/vendor/`) | `watch_quartermaster`, `imperial_quartermaster`, `trainer_armstech`, `trainer_armourcraft`, `trainer_artifice`, `trainer_medtech`, `spacer`, `requisition_republic`, `requisition_imperial` | W2 |
| Missions | `c4_off_the_books`, `c4_greenwatch`, `c4_heartstone`, `c4_proving`, `c4_sepulchre`, `c4_tessalys_road`, `c4_both_doors`; contracts `selvaren_{cleanse,cull,recover,survey,escort,spar}`, `varkhast_{cleanse,cull,recover,survey,escort,spar}` | W2 |

### 16.2 Payloads

| Id | Direction | Owner |
|---|---|---|
| `weapon_action` (pressed) | C2S | lead (dispatch), W4/W5 handlers |
| `ship_action` (button id, destination) | C2S | W3 |
| `ship_status` (state, hull, fuel, destinations with lock reasons, crew) | S2C | W3 |
| `flight_phase` (phase, ticks, destination) | S2C | W3 |
| `bench_action` (tab, schematic id, slot) | C2S | W5 |
| `schematic_sync` (all schematics) | S2C | W5 |
| `vendor_stock` (entries, prices, lock reasons, player credits) / `vendor_buy` (index, count) | S2C / C2S | W2 |
| `jet_burst` | C2S | W4 (the server validates airborne, charges, worn pack) |

### 16.3 Effect events

The full look-and-sound table is 13.3. By owner: W1 `waystone`, `heartstone`, `crystal_answer`; W2 `seeker_scan`, `lesson_complete` 8–10; W3 `ship_engine`, `ship_jump`, `hyperspace`, `ship_repair`, `ship_damage`, `landing_beacon`; W4 `player_blaster_fire`, `blaster_heat`, `repeater_spin`, `vibro_hit`, `electrostaff`, `jet_pack`; W5 `mod_fit`, `bench_craft`, `salvage`, `consumable`, `scanner_pulse`, `grenade_player`, `saber_staff`, `lens_fit`; W6 `force_speed`, `force_barrier`, `force_barrier_impact`, `force_choke`, `force_choke_target`, `hunter_tell`, `hunter_attack`, `droid_tell`, `beast_tell`, `kaast`; lead `calling` 9.

### 16.4 Sounds

Every sound named in 9, 13.3 and 13.4, plus: `boss.kaast.{hurt,hop,rifle,phase,mine_arm,mine_beep,mine_burst,net_throw,axe_draw,combo,charge,slam_tell,slam,helmet_crack,yield,walk_away}`, `mandalorian.{hurt,death,bark}`, `droid.{hurt,step,alert}`, `hollowhorn.{idle,hurt,death}`, `ash_hound.{idle,hurt,death}`, `vendor.{buy,refuse}`, `credits.pickup`, `seeker.fly`, `hub.apprentice_spar`, `planet.arrive`. Placeholders are generated for all of them; `AUDIO_BRIEF.md` gets an M4 section with a brief per sound. Three sounds are reused, not new: the player pistol and rifle fire with the M2 `blaster.pistol_fire` and `blaster.fire`, and the off-world Calling uses `crystal.calling`.

### 16.5 Lang parts

| Part | Prefixes |
|---|---|
| `planet.json` (W1, new) | `block.oldrepublic.<W1 blocks>`, `item.oldrepublic.{aurelite_shard,duskglass_shard}`, `biome.oldrepublic.*`, `dimension.oldrepublic.*`, `site.oldrepublic.<M4 sites>`, `death.attack.oldrepublic.duskglass_cut*` |
| `quest.json`, `mission.json`, `vendor.json` (new) (W2) | `journal.oldrepublic.entry.<8.9>`, `journal.oldrepublic.codex.<8.9>`, `quest.oldrepublic.c4.*`, `dialogue.oldrepublic.c4_*`, `dialogue.oldrepublic.vendor_*`, `npc.oldrepublic.<hub NPCs>`, `mission.oldrepublic.c4_*`, `mission.oldrepublic.{selvaren,varkhast}_*`, `vendor.oldrepublic.*`, `screen.oldrepublic.vendor.*`, `item.oldrepublic.{credit_chit,credit_bundle,kaast_pattern,tomb_relic,sealed_contract}`, `block.oldrepublic.{marker_beacon,contract_terminal}` |
| `travel.json` (W3, new) | `entity.oldrepublic.courier_shuttle`, `item.oldrepublic.<ship items>`, `block.oldrepublic.{landing_beacon,pad_terminal}`, `ship.oldrepublic.*`, `screen.oldrepublic.ship.*`, `destination.oldrepublic.*`, `dialogue.oldrepublic.pad_terminal.*` |
| `equipment.json` (W4) | `item.oldrepublic.<M4 weapons, armour, mods>` (not the three reused ids), `equipment.oldrepublic.<M4 sets, traits, mods, heat>`, `key.oldrepublic.weapon_action` |
| `crafting.json` (W5, new), `saber.json` (W5) | `block.oldrepublic.engineering_bench`, `item.oldrepublic.<W5 items>`, `crafting.oldrepublic.*`, `discipline.oldrepublic.*`, `schematic.oldrepublic.*`, `quality.oldrepublic.*`, `screen.oldrepublic.bench.*`, `screen.oldrepublic.crafting_tab.*`, `saber.oldrepublic.{staff,lens}.*` |
| `force.json`, `skill.json`, `enemy.json`, `boss.json` (W6) | `ability.oldrepublic.{force_speed,force_barrier,force_choke}.*`, `skill.oldrepublic.node.<9.5>.*`, `entity.oldrepublic.<W6 entities>`, `item.oldrepublic.<W6 drops and eggs>`, `boss.oldrepublic.kaast.*`, `death.attack.oldrepublic.{force_choke,wrist_flamer,mandalorian_rocket,camp_mine,jet_slam}*` |
| `sounds.json` (lead) | `subtitles.oldrepublic.*` (generated) |

---

## 17. Test plan

Distinguish compile, automated checks and manual play, as in M1–M3. Nothing counts as "played" until a person plays it. Every game test follows the M3 test rules (`WORKER_M4.md`): an explicit `maxTicks` (≤ 400 for server tests), small fixtures (≤ 7 high), listed mock players, explicit days, no global state, and whole templates, worldgen and real dimension travel only in client tests.

### 17.1 Server game tests (`./gradlew build`; each worker writes their own class, the lead registers it)

| Class | Owner | Tests |
|---|---|---|
| `ContractsM4GameTests` (green before workers start; as built) | lead | `planetLevelsExistOnTestServer` (only logs: the data-pack planets do not exist on the game-test server, so no server test touches a real planet; they use detached data, fixtures or the Nether, and real planets are tested in the client suite); `isStoryLevelSplit`; `clocksUseOverworldTime`; `terrainPaletteFallsBackToGrassDirtStone`; `worldSitesCountPerDimensionAndCap1024`; `guidanceRequestOldConstructorMeansSameLevel`; `missionActiveWithoutWhereDecodesAsOverworld`; `chapter4AbsentDecodes`; `shipRegistryDataCodecRoundTrip`; `moveAcrossKeepsUuidAndRemountsRiders` (a test vehicle and an `M3TestPlayers.listed` rider to the Nether, with a chunk ticket held first; Fabric's `FakePlayer` refuses to ride); `creditsTakeAllOrNothingWithChange`; `reusedItemIdsAreNewClassesAndStillRender` (registry lookups by id); `saberConfigOldStackDecodesWithoutSecondOrLens`; `abilityLocksRefuseWhileFlying`; `dangerTellTableMatches13_3` |
| `PlanetGameTests` | W1 | `biomesInNoVanillaStructureTag` (registry tag lookups); `planetBiomesHavePaletteRows`; `hubEnsurePlacesOncePerServer` (detached data, fake placement); `hubFallbackPlatformWhenNoRingFits`; `planetSiteEnsureRetriesUntilChunksExist`; `waystoneCleansePerPlayer` (fixture); `corruptedChanceSeededByPosition`; `sealedPlatingOnlyByCutterOrPush`; `vanillaRefusesPortalsOnPlanets` (the vanilla rule, checked in the End, since planets do not exist on the game-test server); `everyM4TemplateHasSidecarAnchorsInsideSize`. Every test uses detached data or fixtures in the overworld; none needs a real planet |
| `TravelGameTests` | W3 | `launchRefusalsMatrix` (owner, state, fuel, hull, lock, combat, config); `jumpTakesOneCellAndWear`; `disabledBelowJumpMinHull`; `plateRepairAndSkilledRepair`; `repairSocketsAcceptOnlyTheirPart`; `allFourPartsMakesGrounded`; `bodyWithStaleUuidDiscardsItself`; `restoreFromRegistryKeepsHold`; `bayAssignmentThenRing`; `beaconOnePerPlayerAndAreaCheck`; `recallCostsCell`; `freeRecallOncePerDay`; `towBringsDisabledShipAt20OncePerDay`; `settleSetsDisabledBelow20AndGroundedWhenEmpty`; `ridersDismountAtSettle`; `restartInTransitLandsAtDestinationLaunchingReturnsToOrigin` (registry rules on detached data); `disconnectAboardDismountsBeforeSave` (rules); `ridersInvulnerableDuringSequence`; `crewOnlyMayBoardOwnerOnlyLaunches`; `crewRemovalDismounts`; `noControllingPassenger` |
| `GearGameTests` | W4 | `heatRisesLocksAndCools` (rules, explicit ticks); `ventBelow30DoesNothing`; `perfectVentHalvesLockAndFreeShots`; `chargedShotIsHeavy`; `boltStylePlayerAndAllyPass`; `steadySpreadAndMoveMultipliers`; `repeaterSpinUpAndSuppression`; `vibroGuardReducesFrontMeleeOnly`; `flurryEveryThirdHit`; `cleaveHitsTwoMore`; `electrostaffBlocksSaberWithoutDurability`; `qualityMultipliesDamage`; `setBonusWornCountsEveryOrdinal` (moved here from the contracts class, 6.1); `newSetsTraitsAndBonuses` (each, with the M3 alignment gate); `extraTraitsDecodeAbsent`; `modSlotsPerGear`; `modsRespectClamps`; `jetChargesRefillOnGround`; `jetDefersToForceJumpGlide` |
| `CraftingGameTests` | W5 | `schematicJsonLoadsAndSyncs`; `craftNeedsDisciplineLevelAndInputs`; `craftTakesFromTrayAndInventory`; `qualityOddsByLevel` (seeded); `pityGuaranteesMasterworkAt10`; `lowLevelXpQuarter`; `firstCraftPlayerXpOnce`; `secondDisciplineGate`; `unlearnCostsAndResets`; `salvageReturnsHalfAndMods`; `namedAndStoryItemsCannotSalvage`; `medpacSharedCooldownAndInterrupt`; `stimCrash`; `scannerCountsSurvey`; `saberstaffBuildNeedsTwoCrystalsAndGrip`; `staffWhirlHitsAllInRadius`; `singleBladeToggle`; `lensSlotAndEffects`; `unknownItemIdsSkippedWithWarning`; `trainingSaberNeverKillsPlayer`; `craftingDataCodecRoundTripAndCopyOnDeath` |
| `StoryM4GameTests` | W2 | `stagesOnlyMoveForward`; `oldSaveAtC3CompleteEntersMarked`; `seekerSceneOnSafeMomentOnly`; `briefingPerPathGivesHulkBenchAndDiscipline`; `motivatorThreeRoutes`; `partsXpOnce`; `firstFlightEndsOnAnyLandingOnPathWorld` (Independent: either); `arcOrderFreeAndPowerOwnedCompletesStep`; `grantedPowerWaitsForFreeSlot`; `heartstoneCompletesOnlyWithBarrierSinceWave3`; `sepulchreGripReleasesAtGrant`; `speedRefundForBoughtNode`; `arcMissionsPay150`; `expeditionSpOncePerExpedition`; `skipExpeditionKeepsJournalEntry`; `huntEventsRecordedAsFlagsBeforeHunted`; `huntedOnlyAfterWorldArc`; `sabotageOnceWhenShipLandsHome`; `ambushRetryAfterFleeing`; `clue3CountsOnDroidDeath`; `cluesCountOnce`; `challengedNeedsArcCluesAndLevel14`; `oneTimeItemsReissuedOncePerDay`; `kaastChoiceRewardsOncePerFlag`; `reportUnlocksAllDestinations`; `backPayOnceCapped300`; `peacefulCluesByJournal`; `vendorTierGatesAndRefusals`; `vendorDailyLimitPerPlayer`; `commissionChargesFeeAndStandardQuality`; `contractCreditsOnlyAtFullPay`; `requisitionRefusedAtWary`; `planetContractsSeededPerDimension` |
| `ForceM4GameTests` | W6 | `speedDurationCostAndCancels`; `speedBurstDash`; `wayfarerMissChanceNeverThroughWalls`; `barrierStopsBoltsAndCostsFe`; `barrierHeavyCostsMore`; `barrierPulseAfter40Ticks`; `barrierShelterProtectsAllies`; `barrierReturnSendsBoltsBack`; `barrierBreaksOnBigHit`; `chokeTargetsBreathingOnly`; `chokeHoldUsesDiminishing`; `chokeOnPlayersOnlyWithPvp` (through `Holds`); `chokeShiftOnNonHostileOncePerDay`; `crushAndDreadBranches`; `cruelFocusWithin40TicksOfRelease`; `chokeTicksHookRespected`; `liftNodesInJediAndSithTreesOnly`; `newNodesVisibleAndPurchasable`; `masteryThresholds` |
| `FoesGameTests` | W6 | `hunterTellsPrecedeAttacks`; `hunterJetHopToCover` (fixture); `flamerConeAndBarrierHalves`; `rocketNotSaberDeflectableButPushable`; `hunterYieldDialogue`; `droidResistsBlasterAndImmuneToChoke`; `droidShockWeakness`; `droidShieldOnceBelowHalf`; `hollowhornNeutralUntilHitOrCalf`; `hollowhornChargeStunsOnWall`; `ashHoundPackFlanksAndLungeParried`; `kaastPhaseThresholds`; `kaastChokeOnlyOnceInPhase3`; `kaastInterruptsOnlyFromListed`; `kaastMinesArmThenLive`; `netEndsSpeedBarrierJump`; `kaastHealthScaling`; `kaastResetAfter200`; `kaastYieldAt10`; `resolveCallsHook`; `rematchOncePerDay`; `noFarmDropsFromBounties` |

### 17.2 Client game tests (screenshots reviewed)

- **`TravelClientTest`** (lead driver, W3 assertions): a seeded, non-superflat world; `/oldrepublic ship give @p`; board; launch to `greenwatch`; run the full sequence with real ticks; assert the player is in `oldrepublic:selvaren`, standing beside the ship (riders dismount at Settle, 4.6), which is `READY` at a pad bay, and the hub is placed; screenshots at climb, hyperspace and landing; walk to the keeper; fly to `proving`, then `home`. Then on a **dedicated server** (`context.worldBuilder().createServer()`, default properties, `allow-flight=false`): the same round trip, plus a 3-charge jet pack chain and 80 ticks of Barrier, asserting the player stays connected.
- **`PlanetsClientTest`** (lead driver, W1 assertions): in each planet, sample biomes at fixed coordinates (the seed survey), place a waystone and a greater tomb with `/oldrepublic site place`, assert the templates and sidecar anchors, and take sky screenshots at noon and dusk in each biome.
- **`PersistenceM4Test`** (lead): create a world; write a `ShipRegistry` record with a hold, a `TravelData`, a `CraftingData` with a Masterwork pity, a Chapter Four DUEL stage with a yielded Kaast, and a stack with mods and quality; close; reopen; assert all.
- **`M4ClientTest`** (lead, written in Phase 0) runs the six step classes `m4/{Planet,Travel,Gear,Crafting,Foes,Story}ClientSteps`; each worker fills its own.
- **Gear and crafting steps** (W4, W5, in `M4ClientTest` through `m4/GearClientSteps` and `m4/CraftingClientSteps`): fire each blaster at a target dummy and screenshot the reticle, heat gauge and arm pose in first and third person; the Engineering Bench's four tabs; the vendor screen; the saberstaff in hand.
- A check fails the test if two mod keys share a default binding, now including N.
- Screenshots are named `oldrepublic_m4_<nn>_<what>`, by band: W1 10–19, W2 20–29, W3 30–39, W4 40–49, W5 50–59, W6 60–69.

| Worker | Screenshots |
|---|---|
| W1 | each biome at noon; Selvaren dusk; Varkhast ashfall; a waystone (clean and corrupted); a greater tomb gate; the Greenwatch and the Proving from their pads; the Singing Hollow and the rift |
| W2 | Chapter Four journal page; Ansa and Kaddor dialogues; the vendor screen with a locked row; the seeker scan; the report banner |
| W3 | the hulk under its tarp; the four sockets; take-off dust; climb; hyperspace; landing; the ship screen tabs; the destination map; ship damage effects |
| W4 | each blaster aimed (1st and 3rd person); heat gauge at a perfect vent; the four armour sets; a jet pack burst; the electrostaff charged |
| W5 | the bench (Craft, Modify, Salvage, Disciplines); a Masterwork craft; the Crafting tab; the saberstaff lit (one and two blades); a lens in the workbench |
| W6 | Speed's dust trail; Barrier stopping bolts; Choke on a hunter; a hunter's flamer tell; the droid shield; a hollowhorn charge tell; an ash hound lunge; Kaast in each phase; the yield |

### 17.3 PlaythroughTest extension (lead)

After Chapter Three, the same world continues on the Jedi path with real inputs where possible:

1. Wait out MARKED; see the seeker; talk to Saelen; take the bench; choose Artifice.
2. Fit the hull (12 durasteel from a logged `/give` if the route did not yield enough), craft the power core and nav computer at the bench, cast Sense at a Wreck Field's `HEART` anchor for the motivator (or a logged shortcut), load the nav computer at Saelen, fuel.
3. Fly to Selvaren with real menu clicks on the ship screen. Meet Ansa.
4. "The Greenwatch": cleanse the two nearest waystones (logged teleports beyond 128 blocks). "The Heartstone": hold the ring, raise Barrier when granted (the test keeps a slot free so the grant is slotted, 9) and again during the third wave. Do the Singing Hollow with real Sense and Push presses.
5. Fly home (the ship lands at home and the sabotage blows); repair it; fly back to Selvaren for ambush one, then home for ambush two two days later (logged time skip and XP top-up to level 14 if needed).
6. Fly to the hunting camp. Fight Kaast: deflect from the nests and Push him off one in phase 1, set mines off with Push in phase 2, parry the third combo hit in phase 3; each phase has a 2,400-tick timeout with the logged `/oldrepublic kaast phase` shortcut (10.5; PASS WITH SHORTCUT, the M3 rule).
7. **Spare**; report to Saelen. Assert COMPLETE, all destinations unlocked, `force_barrier` owned, a second crystal.

A shorter **Sith run** starts from a logged shortcut to Chapter Four `REPAIRS` on the Sith path, flies to Varkhast and does Zarvos's Sepulchre with Choke. The **Independent run** (Tessaly's Road and Speed) is item 9 on the cut list. Every shortcut is logged.

### 17.4 Manual checks (owner or a tester)

- The flight: does it feel like a ship leaving a world? Is 15 seconds right? Any client stutter on arrival behind the overlay?
- Whether each planet feels distinct from the home world at a glance, in daylight and at night, at all four presets.
- Blasters against sabers: is a blaster build fun without being better than a saber in close combat? Is the perfect vent readable?
- Crafting: are two disciplines enough choice, and does the pity counter feel fair?
- Kaast solo at level 14 and 15, and with 2 players. Target: 6 to 10 minutes; phase 2 must be readable with sound off (mine blinks).
- Barrier, Choke and Speed: readable without glows? Does Barrier read as "the Force", not a force field?
- A dedicated server with two players flying as owner and crew; one logging out mid-flight.
- Peaceful pass: clues by journal, the duel as a talk.

---

## 18. Lore notes for `DECISIONS.md` (lead adds these)

- **Planets are original.** The brief names Tython-, Korriban-, Dromund Kaas- and Tatooine-inspired places. M4 uses two original worlds instead of canon ones, so the mod never contradicts canon geography: **Selvaren** (a highland forest world where the Watch began; Tython-like in feel) and **Varkhast** (a red-rock tomb world where the Empire trains acolytes; Korriban-like in feel). The storm jungle and the wasteland port wait for M5 and will be original too.
- **Original characters:** Keeper Ansa Veylin, Overseer Vael Kaddor, Torvin Kaast, the hunter's droid Rattle, the unnamed Buyer. Lord Hadrek Zarvos and Tessaly Arn are the M3 gatekeepers. The lead checks every name against the canon wikis before release.
- **Mandalorians.** Mandalorian culture, armour and hunters are canon concepts; Kaast is an original character who has left his clan, and his armour is an original design. The spec avoids canon proper nouns for materials and clans; "Mandalorian iron" is used only as a plain description of his axe. Mandalorian clans as a faction story are M5.
- **Hyperspace.** Ships jump between systems through hyperspace with a motivator and a nav computer (canon concepts). Flight inside a system is not simulated in M4.
- **Credits.** Republic and Imperial credits both circulate on the frontier; the mod's chits are generic "credits" without a government's stamp.
- **Force Barrier, Speed and Choke** are canon powers, adapted. Barrier is shown only through what it stops (bent air and held dust), never as a coloured dome.
- **Sith Lord boss.** M3 kept "a Sith Lord boss" for the M4 enclave and academy. M4's boss is instead the brief's "elite Mandalorian hunter", because the chapter is about leaving home with a price on your head; Zarvos appears as an echo in his own tomb. A living Sith Lord boss moves to M5 with the redemption and corruption questlines (open question 2).
- **Saberstaffs and training sabers** are canon weapon types; every hilt part is an original design.

---

## 19. Open questions

Only the decisions that block development. Each has a recommended default, so work can go ahead without waiting. The lead records the owner's answer in `DECISIONS.md`.

1. **Original planets or canon ones?** The brief says "Tython-inspired" and "Korriban-inspired"; using the canon names would read as more Star Wars but risks contradicting canon.
   **Default:** original planets, Selvaren and Varkhast, with the canon worlds as inspiration only (18).
2. **Redemption and corruption.** M3 deferred them to M4 "when there are enclave and academy locations". The hubs now exist, but M4 is already full with ships, planets, crafting and gear.
   **Default:** move them to M5 together with a living Sith Lord boss, so they get a full chapter of their own. M4 keeps the path-change rule unchanged.
3. **Credits as items or a wallet?** Items can be dropped, stored, traded and lost; a wallet number cannot be stolen or duplicated by item bugs.
   **Default:** items (7.7), because players can trade them and the ship hold can carry them, and vendors read the hold too.
4. **Two disciplines out of four.** Enough to make builds differ, but some players will want everything.
   **Default:** two, with unlearning for 500 credits and trainer commissions so nothing is locked away.
5. **No free flight.** The brief puts local flight "later, if technically practical". The probes show it is practical (the vanilla happy ghast proves a flying mount works), but it is a large feature.
   **Default:** M4 ships the scripted flight only; free flight is the first M5 ship feature.
6. **Lift for Jedi and Sith.** M3 left it open.
   **Default:** a 2 SP node at level 13 in each of the Jedi and Sith trees (`lift_learned_jedi`, `lift_learned_sith`, 9.4).

---

## 20. Revision notes

This revision fixed the consistency review and folded in Phase 0 as built and the follow-up probe. Each line is a decision that changes behaviour, with the sections it touches.

1. Sepulchre: Zarvos's echo lets go as Choke is granted and then waits; any Choke that lands ends the trial (8.4).
2. A granted power takes the first free slot; with none free, a hint shows and the story step waits (9, 8.4).
3. Heartstone: it completes after 90 s if Barrier was raised once since wave 3, else on the next raise; the waves are corrupted hollowhorns and M3 Watcher sentinels (8.4, 10.3).
4. HUNTED starts only on leaving WORLD_ARC; earlier hunt events are flags; CHALLENGED needs the arc, 3 clues and level 14; FIRST_FLIGHT ends on any landing on the path world (8.1).
5. Sabotage fires only when the ship itself lands at home; ambush two is on another world; clue 3 counts when the droid dies (8.5).
6. Hunter bounties start at HUNTED and go on after the chapter (2, 8.7).
7. Restart mid-flight: `IN_TRANSIT` and `LANDING` land at the destination, `LAUNCHING` goes back to the origin; the state is then set as at Settle (4.9, 12.2).
8. Settle sets `DISABLED` under 20 hull (it wins over `GROUNDED`), and riders dismount at Settle (4.6).
9. No crew check at launch; a crew member removed while aboard is put off the ship (4.6, 4.9).
10. Recalling a `DISABLED` ship tows it in at 20 hull, one free tow a day; the spacer also tows (4.8).
11. `hunting_camp` stays unlocked from CHALLENGED on; the rematch is called at the camp gate and pays 100 credits (4.5, 10.6).
12. Kaast's 4,000 XP, 2 SP and 300 credits are paid once, in 10.6, whatever the choice (8.6).
13. Choke works on Kaast once, in phase 3 only, through the new `ForceReactions.Handler.chokeTicks` (9.3, 10.5).
14. Hubs search rings 0, 16 and 32 under an 11×11-chunk ticket (Milestone 6: 15×15 chunks for the rebuilt hubs); the Heartstone is placed when its step first begins (3.4, 3.5).
15. Overflow landing rings are 40, 48 and 56 blocks from the hub centre (4.5; Milestone 6: 64, 72 and 80).
16. Nether portals need no mod code, because vanilla refuses them on planets; `planets.allowPortals` is gone (3.1, 12.5).
17. Off-world Calling as built: the ship body, else the home pad, in the player's level, else the faint cue, with "The Calling comes from <world>." (3.6, 11.2).
18. On a planet, players respawn at a bed only; respawn anchors do not work there (12.2).
19. The Wreck Field motivator is found with Sense near the `HEART` anchor; bought, it costs 400, or 360 at Honoured (4.2, 8.2).
20. The Independent's hulk is Vasch's spare courier, named in the wreck's nav log, not the wreck itself (2, 4.2).
21. Independents are welcomed at both hubs; Both Doors is a favour for standing, not a service gate (2, 8.4).
22. Trainers serve every path and sell their level 5 recipes; the second discipline is chosen at a bench (7.1, 7.2, 7.8).
23. The Watch and Sith set schematics are `item` schematics sold by the quartermasters; the set bonuses use the M3 alignment gates; the Watch bonus is now Warden's Resolve (6.2).
24. There is no Revered tier: ×0.9 at Honoured is the best price (7.8).
25. Arc missions pay 150 credits each and "A Part Off the Books" 100; enemy credit drops are W2's, with `vendors` keys (7.7, 8.4).
26. Each expedition gives 1 SP, once (8.4).
27. Crystal tuning is cut and left for later; crystals stay cosmetic (1, 5.7).
28. The saberstaff has one emitter part at both ends and one colour; there are no split colours (5.6).
29. Blasters retuned: damage 4.0 / 5.0 / 2.5, rate 8 / 10 / 3, heat 15 / 14 / 7, spin-up after each vent, steadied spread ×0.35 for all; `mod_heavy_barrel` slows the rate by 1 tick (5.1, 6.3, 14.2).
30. Force Speed has no other requirement and is `taught_by` Independents; Tessaly's Road refunds a bought node (9.1, 8.4).
31. Lift for Jedi and Sith is two nodes, `lift_learned_jedi` and `lift_learned_sith`; `force_lift` loses `path: independent` (9.4).
32. `staff_whirl` and `lens_mastery` have no requirement; `cruel_focus` uses a 40-tick window after the caster's Choke ends (9.5).
33. `passenger_seat` is an Armstech 2 schematic only (4.7, 16.1).
34. The waystone step is a `survey` objective; `escort` and `spar` are new objective types (8.4, 8.7).
35. Lost one-time chapter items are re-issued once a day while the step needs them (12.2).
36. `kaast_pattern` replaces the "commission pass" and is kept after use (8.6, 7.8).
37. New block ids `pad_terminal` (W3) and `contract_terminal` (W2); `wayfarer_relic` is dropped (16.1).
38. Poacher drops become credit chits and Poacher Captains drop the `vibroblade`, both done by the lead (5.2, 7.7, 12.5).
39. Creature spawns are W6's `BiomeModifications`; entity types and particles are registered by their owners (3.1, 13.2, 15.1).
40. One working tree: workers never commit; the lead reviews and commits in the order W1, W3, W4, W5, W6, W2 (15.3).

Follow-ups outside this file, done by the lead: `tools/check_m4_ids.py` and `tools/ownership_m4.json` have `pad_terminal` (W3) and `contract_terminal` (W2) and no `wayfarer_relic`; the poacher loot change (line 38) is made; `ForceReactions.Handler.chokeTicks` is added. `workbench.tune` stays in `events_m4.json`, unused and reserved for crystal tuning when it returns.
