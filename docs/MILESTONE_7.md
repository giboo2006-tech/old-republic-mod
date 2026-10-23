# Milestone 7: the lightsaber and weapons overhaul

Gabriel's request (23 October): "Go add more weapons and improvements to the weapons, make it so you gotta
go mine for ores to craft better hilts, grips (to get stronger lightsabers) give boss drops. Make the
design of the hilts more accurate and detailed to star wars. Just add stuff yourself and put in a lot of
effort into the lightsabers. do what you want"

So this milestone does five things:

1. **Every hilt part is redesigned** in the film style (machined metal, black grips, activation boxes,
   shrouded emitters, D-rings) as detailed, round 3D models, and the inventory shows the real hilt.
2. **Hilt materials.** Every emitter, grip and pommel is made of a material, from plain steel to
   hymnsteel. Better materials make a stronger saber and each gives a small trait. The materials come
   from ores the player mines: a new meteoric iron in the overworld, the planets' existing ores and a new
   clan iron ore on Tyrvann.
3. **The Forge**, a new tab on the Lightsaber Workbench, where the player studies a part design and
   forges it, or re-forges a part they own, in any material they carry.
4. **Boss drops.** Each of the four bosses drops a set of hilt parts with a set bonus, and elites drop
   materials. Seven new craftable designs come from the world.
5. **New weapons and better old ones:** a vibro-axe, a force pike, a scatter blaster and a blaster
   carbine; four mods made from the new materials; 3D models in the hand for every weapon still drawn
   flat.

Contents

1. Scope and cut order
2. How it should feel
3. Hilt materials
4. Ores and worldgen
5. The Forge
6. Hilt part designs
7. Boss sets and drops
8. Saber variants: crossguard and curved
9. Weapons
10. Hilt rendering
11. Sounds
12. Frozen Phase 0 contracts
13. Ownership and workers
14. Tests
15. Docs
16. Revision notes

---

## 1. Scope and cut order

In scope: the saber feature's parts, stats, workbench and rendering; the saber parts of the boss and elite
rewards; new ore blocks, materials and their worldgen; the blasters and vibro weapons; their mods;
sounds for all of these; the checks and tests that go with them.

Out of scope: new bosses (the tomb guardian, war droid and cave beast of the design brief stay
unbuilt), a lightsaber pike, new crystal colours, armour materials, new quests, new NPC roles, changes
to existing reward amounts.

If time runs short, cut from the bottom of this list first:

1. Hilt materials and their stats, the ores (starfall iron, clan iron ore, hymnsteel) and the Forge
   (Gabriel's first point).
2. The detailed models of the 12 existing hilt parts, with material tints (his second point).
3. The boss sets and drops, and the elites' material drops (his third point).
4. The seven new craftable designs and where they are found.
5. The crossguard (Warden) and the curved hilt (Penitent).
6. 3D inventory icons for sabers and parts; the thrown saber drawn with its real hilt.
7. The new weapons and the 3D in-hand models of the pistol, vibroblade, vibrosword and electrostaff.
8. The four material mods; the new sounds.
9. Stretch: a large 3D hilt preview in the workbench projector.

## 2. How it should feel

- The first time the player draws their saber in this build it looks like a film prop: a machined hilt
  with black grip ridges, an activation box with a red button on the thumb side, a shrouded emitter with
  a dark mouth. Every part they chose shows, in the hand, on the belt of an NPC, in the inventory and
  spinning through the air on a throw.
- Ores matter. The player finds a scorched meteor crater on the plains, digs out the starfall iron, smelts
  it and forges a darker, heavier emitter that hits harder. On each planet the local ore makes a better,
  differently coloured hilt; at the end of the story a hymnsteel hilt is pale, almost lavender silver.
- A full hymnsteel hilt is about a fifth stronger than a steel one: noticeable, never a different game.
- Bosses drop trophies with a story: the hilt of a Watch knight Kaast hunted down, Darth Ulvaine's
  curved hilt, the resonant parts of the Long Hymn's choir. Wearing a full set changes how the saber
  fights.
- Weapons: an axe that breaks a guard, a force pike that stuns at reach, a scatter blaster that empties a
  doorway, a burst carbine. Every weapon is a 3D object in the hand.

---

## 3. Hilt materials

### 3.1 The ladder

Each hilt part (emitter, grip, pommel) carries one material. A part without one is steel, so every part
and every saber that exists today is steel and keeps its stats exactly.

| Ordinal | Id | Name | Tier | Tint | Costs (per unit) | Where the player gets it | Trait, per part |
|---|---|---|---|---|---|---|---|
| 0 | `steel` | Steel | 0 | `E6E8EC` | iron ingot | anywhere | none |
| 1 | `durasteel` | Durasteel | 1 | `B4C0CE` | `durasteel_plate` | wreck salvage, droids, quartermasters | none |
| 2 | `starfall` | Starfall iron | 2 | `8C8496` | `starfall_ingot` | overworld meteor craters and deep veins (4.1) | +4% saber damage to droids and constructs |
| 3 | `aurelite` | Aurelite | 3 | `E0C878` | `aurelite_shard` | Selvaren ore | Force powers cost 3% less |
| 4 | `duskglass` | Duskglass | 3 | `7C6C9C` | `duskglass_shard` | Varkhast crystal walls | each saber hit restores 0.5 Force energy |
| 5 | `sunstone` | Sunstone | 4 | `E09A4C` | `sunstone` | Ghessar ore | 4% chance a saber hit sets the foe alight for 2 s |
| 6 | `clan_iron` | Clan iron | 4 | `5C6676` | `clan_iron` | Tyrvann ore (4.2), Hask warriors, the Hask armourer | guard drain 5% lower |
| 7 | `hymnsteel` | Hymnsteel | 5 | `CCC2E8` | `hymnsteel_ingot` | an alloy (4.3) | Force power damage 3% higher |

Traits stack per part: a full starfall hilt deals 12% more to droids. A saberstaff counts its emitter
(once) and its grip. Ordinals are synced: append only.

### 3.2 Numbers

Each part adds a bonus by its material's tier (balance section `saber`, arrays indexed by tier 0 to 5):

| Part | Field | Default by tier |
|---|---|---|
| Emitter | `materialEmitterDamage` | 0, 0.2, 0.4, 0.6, 0.8, 1.0 (damage, flat) |
| Grip | `materialGripSpeedPct` | 0, 1.5, 3, 4.5, 6, 7.5 (attack speed, percent) |
| Grip | `materialGripGuard` | 0, 4, 8, 12, 16, 20 (guard stamina) |
| Pommel | `materialPommelParry` | 0, 0.2, 0.4, 0.6, 0.8, 1.0 (parry ticks, flat, before the form multiplier) |
| Pommel | `materialPommelGuard` | 0, 4, 8, 12, 16, 20 (guard stamina) |

Worked example: a Guardian/Wrapped/Weighted saber (8 damage, 1.35 speed) all in hymnsteel deals 9 damage
at 1.45 attacks per second: 13.1 DPS against 10.8, about 21% more. All in starfall it deals 8.4 at
1.39: 11.7 DPS, 8% more. The parry window keeps its cap (`parryWindowCap`, 11).

Order in `SaberStats`: the parts' numbers (and the staff's), then the materials, then the blade length,
then the crystal tunings (12.1). `SaberStats.of(emitter, grip, pommel, b)` does not change, so every
pinned number of today's tests holds.

### 3.3 Trait numbers

Balance section `saber`: `starfallDroidDamagePct` 4, `aureliteForceCostPct` 3, `duskglassEnergyOnHit`
0.5, `sunstoneIgniteChancePct` 4, `sunstoneIgniteTicks` 40, `clanIronGuardDrainPct` 5,
`hymnsteelForceDamagePct` 3. Traits act while the saber is lit in the main hand (a dual wielder's
off-hand saber counts for its own hits). Force cost and Force damage go through `ForceModifiers` and
`DamageModifiers`, so their clamps hold.

### 3.4 Showing it

- A loose part's name carries its material ("Starfall Vanguard Emitter"; a steel part keeps its plain
  name). Its tooltip adds the material's line (name, tier, trait) under today's stat line.
- A saber's tooltip lists its three parts with their materials, the active set bonus (7.2) and the
  traits it adds up to.
- The workbench's stat bars count materials; their scale grows to the best material hilt.

---

## 4. Ores and worldgen

### 4.1 Starfall iron (overworld)

- Blocks: `starfall_ore` (in stone, strength 3.5), `deepslate_starfall_ore` (in deepslate, 5.0), both
  need an iron pickaxe and drop 1 `raw_starfall` (Fortune applies, Silk Touch gives the block);
  `starfall_rock`, the meteorite's charred shell (strength 2.5, any pickaxe, drops itself).
- Items: `raw_starfall`, `starfall_ingot`. Smelting 200 ticks or blasting 100 ticks, 0.7 XP.
- **Meteor craters** (`oldrepublic:wilds/starfall_crater`, a worldgen jigsaw structure of the overworld
  wilds, `docs/STRUCTURES.md` rules): a bowl 13 to 21 blocks across and 3 to 6 deep with the meteorite
  in the middle, a shell of `starfall_rock`, basalt and blackstone around 6 to 14 starfall ore blocks,
  scattered fragments and scorched ground thrown out along an impact streak. Three starts: a fresh
  crater (magma under the shell, smoke rising from a buried campfire, burnt trees), an old one (grassed
  over, a pond in the bowl, moss on the shell) and a deep one in hills (a shaft through the shell to a
  core of deepslate ore). Placement: `clear_of_origin`, spacing 30, separation 10, the overworld land
  biomes (not oceans, rivers or the deep dark). No loot chest; a wrecked probe or a sign of an earlier
  prospector is welcome.
- **Deep veins:** `oldrepublic:ore_starfall_deep`, veins of 4 in deepslate, 2 tries a chunk between
  y -64 and -8, added to every overworld biome with Fabric's `BiomeModifications.addFeature` in the
  `UNDERGROUND_ORES` step (API-CHECK). If that call cannot be made to work, the craters alone are enough.

### 4.2 Clan iron ore (Tyrvann)

- Block `clan_iron_ore`: basalt with blued metal seams (strength 4.0), needs a diamond pickaxe
  (`minecraft:needs_diamond_tool`), drops 1 `clan_iron` (Fortune applies).
- Generated in Tyrvann's basalt ridges: target `minecraft:basalt`, veins of 5, 8 tries a chunk from the
  ridges' basalt band to the surface; written through `tools/assets/gen_structures_m5.py` (its
  `CONFIGURED`, `PLACED`, `MASTER["tyrvann"][6]` and the ridge biomes), then rerun. Hask warriors and the
  Hask armourer keep their clan iron.

### 4.3 Hymnsteel

- Item `hymnsteel_ingot`. Shapeless crafting: `hymnstone_shard` + `clan_iron` + `sunstone` +
  `starfall_ingot` give 2 hymnsteel ingots. A full hymnsteel hilt (7 units) takes four hymnstone shards.

### 4.4 The other materials

Durasteel, aurelite, duskglass and sunstone are mined or found as today. Elites drop more of them (7.4).

---

## 5. The Forge

A third tab on the Lightsaber Workbench, beside Assemble and Tune.

### 5.1 Designs

- A **design** is a hilt part variant (`emitter_vanguard`). Designs are craftable, set or legendary:

  | Kind | Designs | Known how |
  |---|---|---|
  | Craftable, known from the start | Wrapped and Ridged grips; Weighted and Balanced pommels | they have crafting-table recipes |
  | Craftable, studied | Guardian, Duelist, Vanguard and Warden emitters; Staff, Banded, Knurled and Fluted grips; Ringed and Domed pommels | **Study** a part of that design on the bench |
  | Set (7) and legendary | the boss sets; the Cantor's Emitter, Sunforged Grip, Hask Pommel | never forged; only re-forged |

- What a player knows is saved per player (`ForgeData`, an attachment that survives death and is synced
  to its owner).

### 5.2 Actions

- **Study:** a part of a craftable design the player does not know, in its socket on the bench. Free;
  the part stays.
- **Forge:** a known craftable design and a material. Takes the material's units and credits from the
  inventory and puts the new part in its empty socket, or in the inventory when the socket is full.
- **Re-forge:** any part in its socket (craftable, set or legendary) and a material other than its own.
  Same cost as forging that part in the new material. The part keeps its design and its quality (a
  legendary part stays Legendary); its old material is gone.
- Costs (balance section `saber`): units per part `forgeUnitsEmitter` 2, `forgeUnitsGrip` 3,
  `forgeUnitsStaffGrip` 5, `forgeUnitsPommel` 2; credits per part by tier `forgeCredits` 0, 20, 40, 80,
  120, 200. Credits are the chits and bundles the player carries, as the Tune tab takes them.
- Effects: `workbench.forge` with sparks at the bench on Forge and Re-forge, `workbench.study` on Study.

### 5.3 The tab

- A list of the known designs grouped by slot, a row of eight material chips (a chip the player cannot
  afford shows why in its tooltip), the chosen part drawn as its 3D icon in the chosen material, the stat
  change against the part in the socket, the cost line, and the Forge, Study and Re-forge buttons. A
  status line says what is missing.
- Assembly stays free and takes parts of any material. The crafting-table recipes and the Artifice
  schematics stay as they are and make steel parts.

---

## 6. Hilt part designs

### 6.1 Art direction

- The hilts take the film trilogies' engineering language: machined metal tubes, black rubber T-track
  grip ridges, activation boxes with a button on the thumb side, emitter shrouds with vents and a dark
  recessed mouth, bevelled pommel caps, D-rings, small brass accents. Every design is **original**: no
  copy of a named film prop, no logos, no text, no glyphs or runes (`docs/assets/saber.md` is updated
  to say so; the old "generic cylinders" rule goes).
- **Round, not square.** Bodies are regular octagonal prisms (`lib.octo`, 10.2): two axis-aligned bars
  and two bars turned 45 degrees about the hilt's axis, which together make an exact octagon. Steps,
  rings, collars and grooves are octagons of other radii. Details (buttons, boxes, ridges, vents,
  quillon ports) are small boxes.
- **Colours:** the part's metal is a greyscale texture tinted by its material; black (rubber, paint),
  dark steel and brass accents are untinted. One coloured button (red, green or blue) per hilt at most.
  The emitter's mouth is near black. A small glass window or inlay may glow faintly (`light_emission`
  up to 6); nothing else glows.
- **Read at a glance.** Details must read in first person (the hilt about 120 pixels tall on an 854 by
  480 screen) and on a stand-in at three blocks. Prefer a few strong forms over noise.
- **Conventions:** the hilt's axis is model x 8, z 8, along +y. The thumb side, which first person shows,
  is +x: activation boxes and buttons face +x.

### 6.2 Bands and joins

| Slot | y range | May extend to | Radius |
|---|---|---|---|
| Pommel | -2.0 to -0.1 | -3.0 (rings, loops) | up to 1.4 |
| Grip | -0.1 to 3.7 | none | 0.85 to 1.15 |
| Emitter | 3.7 to 6.0 | 7.6 (tall shrouds); its blade mouth moves with it (10.4) | up to 1.6; quillon ports to z 8 ± 3.0 |
| Staff grip | 4.6 to 11.4 (staff model, 10.3) | none | 0.85 to 1.15 |

Every part meets its neighbour at a collar of radius 1.05 to 1.15 (the emitter's bottom, both ends of the
grip, the pommel's top), so any three parts fit together. Everything stays inside the model bounds
(-16 to 32). At most 60 elements per design.

### 6.3 The catalogue

Craftable designs are uncommon items, set parts rare, legendary parts epic. Stats are added in
`SaberStats.of(emitter, grip, pommel, b)`; the numbers below are balance defaults (section `saber`).

**Emitters**

| Design | Kind | Look | Stats |
|---|---|---|---|
| Guardian | craftable | A straight, heavy shroud (radius 1.35) with a flat crown and three vertical vent slots each side; a narrow neck with an activation plate and red button. | reach +0.25, speed -0.1 (today) |
| Duelist | craftable | Slim and tapered like a pen, a bevelled lip, a thin brass ring at the neck, a small blue sensor stud. | speed +0.15, damage -0.5 (today) |
| Reliquary | Collector set | A squared archival shroud with brass collars and a glass window over an amber emitter crystal; riveted plates. | aimed deflections and parry (today) |
| Cantor | legendary | A flared crown with four resonator fins and a slim throat; a violet inlay ring. | damage +8% (today) |
| Vanguard | craftable | A long cylindrical shroud with rows of horizontal vent slots on two sides and a window cut into its side showing the inner emitter tube; a thick lower collar. Heavy, industrial. | damage +0.5, speed -0.1 |
| Warden | craftable | A broad head with two short quillon ports pointing sideways (±z) under the crown, each with a dark mouth; a squared guard block below. Short side blades come out of the ports (8.1). | parry +1.0 tick, guard drain -10%, speed -0.15 |
| Watchman | Kaast set | A Watch knight's emitter: a crown of four low teeth at 45 degree steps, a grooved brass band, worn and pitted metal. | reach +0.15, damage +0.3 |
| Penitent | Ulvaine set | A neck bent 22.5 degrees toward +x under a narrow hooked emitter; dark lacquer with an ember-red ring. The blade leaves at the same angle (8.2). | damage +0.6, speed -0.05 |
| Choir | Cantor set | A tall bell of stacked rings ribbed with eight thin pipes like an organ's, a pale violet inlay. | damage +5%, Force cost -3% |

**Grips**

| Design | Kind | Look | Stats |
|---|---|---|---|
| Wrapped | craftable | A leather-wrapped tube (dark brown, diagonal wrap lines) between steel collars, a slim activation slider. | guard ×1.2 (today) |
| Ridged | craftable | The classic: a steel tube with rows of short black T-track ridges on four sides over its lower two thirds, a clamp band at the top with an activation box. | speed +0.1 (today) |
| Staff | craftable | The saberstaff's long grip: a central black ridged section, steel tubes to each end, an activation box for each blade (mirrored), rings. | the staff's numbers (today) |
| Sunforged | legendary | A dark fluted core banded with three sunstone rings. | speed +8%, guard +5 (today) |
| Banded | craftable | Alternating thick metal rings and black rubber rings, like a ribbed column; a small activation stud. | guard +10, speed +0.05 |
| Knurled | craftable | A slim (radius 0.95) tube with a diamond knurl over its length, thin collars, a flush slide switch. | speed +0.15, guard ×0.9 |
| Fluted | craftable | Eight deep vertical black channels cut into the metal, a brass ring at the top. | damage +5% |
| Reliquary | Collector set | A brass-banded archival tube with a riveted leather panel and a blank tag plate. | speed +0.05, guard +10, parry +0.5 |
| Watchman | Kaast set | A worn grey cord wrap with a leather thong, a battered activation box with pitted metal. | guard +20 |
| Penitent | Ulvaine set | A smooth dark lacquered grip that flares toward the pommel, with thin ember-red inlay lines. | speed +0.1, damage +2% |
| Choir | Cantor set | Stacked pale rings like reeds, narrowing to the middle, with small violet glass beads. | speed +6% |

**Pommels**

| Design | Kind | Look | Stats |
|---|---|---|---|
| Weighted | craftable | A heavy bevelled cap (radius 1.35), a black band, a brass base ring. | damage +1, speed -0.15 (today) |
| Balanced | craftable | A slim cap with a bevelled end and a small brass nub. | parry ×1.1 (today) |
| Archivist | Collector set | A squared archival cap with brass corners and a small glass vial window. | parry and speed (today) |
| Hask | legendary | A heavy clan iron block with ember notches and a flat striking face. | guard +15, parry +1 (today) |
| Ringed | craftable | A grooved cap with a D-ring hanging below it on two short posts. | parry +0.5, speed +0.05 |
| Domed | craftable | A domed cap of stacked rings with a small crystal window in the dome. | Force cost -4% |
| Watchman | Kaast set | A grooved cap with a lanyard eyelet and pitted metal. | parry +1.0 |
| Penitent | Ulvaine set | A tapered teardrop cap with an ember ring (no spike). | damage +0.5 |
| Choir | Cantor set | A small bell with three stacked rings and a violet inlay. | parry +0.5, guard +10 |

### 6.4 Where craftable designs come from

| Design | Found as a steel part |
|---|---|
| Guardian, Duelist emitters | poachers and the poacher captain, as today |
| Vanguard emitter | the Republic and Imperial requisition vendors (260 credits); trooper captains 8% |
| Warden emitter | the Watch hermitage and Watchers' Rest chests, 35% |
| Banded grip | the requisition vendors (180 credits) |
| Knurled grip | the smuggler cove chest 35%; cartel captains 15% |
| Fluted grip | Sith tomb chests 30% |
| Ringed pommel | the poacher dig chest 30%; poacher captains 10% |
| Domed pommel | the crystal cave's hidden chest 35%; Hymnsworn callers 10% |
| Wrapped, Ridged, Weighted, Balanced | the crafting table, as today; the Staff grip by Artifice, as today |

---

## 7. Boss sets and drops

### 7.1 The sets

| Set | Boss | Parts | Material they drop in |
|---|---|---|---|
| `collector` | Orren Vasch, the Collector (Chapter 3) | Reliquary emitter, Reliquary grip, Archivist pommel | the new grip in starfall iron; the emitter and pommel stay steel as today |
| `watchman` | Torvin Kaast (Chapter 4) | Watchman emitter, grip, pommel | aurelite |
| `penitent` | Darth Ulvaine (Chapter 5) | Penitent emitter, grip, pommel | clan iron |
| `choir` | the First Cantor (the Long Hymn) | Choir emitter, grip, pommel | hymnsteel |

### 7.2 Set bonuses

A saber with two parts of one set has its two-piece bonus; with three, both. A saberstaff counts its
emitter and grip.

| Set | Two parts | Three parts |
|---|---|---|
| Collector | +1 parry tick | **Curator's Eye:** a parry marks the attacker for 3 s; your next saber hit on it deals 20% more |
| Watchman | +15 guard stamina | **Vigil:** guard stamina comes back 25% faster, and a blocked melee hit slows the attacker for 1 s |
| Penitent | +5% saber damage | **Contrition:** saber hits restore 1 Force energy and deal 10% more to foes under half health |
| Choir | Force powers cost 5% less | **Long Hymn:** every fifth saber hit within 10 s releases a resonant pulse: 3 damage and a small push to other foes within 3 blocks (sound and particles, nothing drawn on the ground) |

Numbers are balance fields (`collectorSet2Parry`, `collectorMarkTicks`, `collectorMarkDamagePct`,
`watchmanSet2Guard`, `watchmanRegenPct`, `watchmanSlowTicks`, `penitentSet2DamagePct`,
`penitentEnergyOnHit`, `penitentLowHealthPct`, `choirSet2CostPct`, `choirPulseHits`,
`choirPulseWindowTicks`, `choirPulseDamage`, `choirPulseRadius`, `choirPulseKnockback` 0.4).

### 7.3 Drops

Every new drop is its own call under its own guard; no amount paid today changes.

| Boss | When | Drop |
|---|---|---|
| Collector | each echo clear (daily, as today) | the Reliquary grip, 25%, guaranteed by the fourth clear without one (its own pity counter) |
| Kaast | first clear (any choice) | the Watchman emitter |
| Kaast | each echo rematch (daily) | the Watchman grip or pommel (the one the player lacks), 25%, pity 3 |
| Ulvaine | the fight's pay (once) | the Penitent emitter |
| Ulvaine | each echo (daily) and each helper roll | the Penitent grip or pommel (the one the player lacks), 25%, pity 3 |
| First Cantor | first clear | the Choir emitter |
| First Cantor | each weekly clear | a Choir grip or pommel the player lacks, 30%, pity 2; with all three, 2 hymnsteel ingots instead |

`gameplay/echo_cache` (rolled on every Collector echo and Unchained clear) gains 1 to 2 starfall ingots
(40%) and 1 durasteel plate (40%).

### 7.4 Elites

| Elite | Adds |
|---|---|
| Hask champion | 1 to 2 clan iron |
| Cartel captain | 1 to 3 sunstone; the Knurled grip 15% |
| Hymnsworn caller | a hymnstone shard 15%; the Domed pommel 10% |
| Scour matriarch | 2 to 4 duskglass shards |
| Hollowhorn bull | 2 to 4 aurelite shards |
| Trooper captains | the Vanguard emitter 8% |
| Poacher captain | the Ringed pommel 10% |

### 7.5 NPC sabers

Vasch holds the Collector set (amber), Ulvaine the Penitent set (red, clan iron). Sith acolytes hold
Vanguard emitters with Fluted grips. Mentors keep their roles' parts.

---

## 8. Saber variants: crossguard and curved

### 8.1 Crossguard (the Warden emitter)

Two short side blades (quillons) leave the Warden's ports along ±z (across the view in first person, which
looks at the thumb side), each a third as wide and 3.5 model
units long, lit and retracted with the main blade. They are drawn, not hit-tested, and leave no motion
trail.

### 8.2 Curved hilt (the Penitent emitter)

The Penitent's neck is bent 22.5 degrees toward +x and its blade leaves at the same angle from the
emitter's mouth. On a saberstaff both ends bend, point-symmetric.

---

## 9. Weapons

### 9.1 New weapons

| Id | Kind (appended) | Numbers (balance defaults) | Special | Made / sold |
|---|---|---|---|---|
| `vibro_axe` | `VibroWeaponItem.Kind.AXE` | damage 10, speed 0.85, reach +0.25, durability 800, guard -40% | **Sunder:** a full swing (strength 0.9 or more) against a guarding foe (a saber guard, an NPC guard, a vibro guard) drains its guard three times as fast, and if it breaks staggers it for 15 ticks | Armstech 3; the Hask armourer, 700 credits |
| `force_pike` | `VibroWeaponItem.Kind.PIKE` | damage 7, speed 1.0, reach +1.5, durability 650, guard -50% | **Stun thrust:** the Weapon Action charges for 30 ticks; the next hit within 100 ticks stuns for 30 ticks (Slowness IV, Weakness) and pushes back 0.6; droids are not stunned and take ×1.3 instead | Armstech 4; the Saltgate cartel fence, 900 credits |
| `scatter_blaster` | `BlasterItem.Kind.SCATTER` | 6 pellets of 1.8, spread 8°, bolt speed 1.8, life 8 ticks, rate 18 ticks, heat 28, cooling 2.5, overheat 70, vent 25, durability 700, movement -15% | point blank all six pellets land (10.8); a light knockback | Armstech 3; the cartel fence, 650 credits |
| `blaster_carbine` | `BlasterItem.Kind.CARBINE` | 3-bolt burst of 3.0, 2 ticks apart, a burst every 14 ticks, spread 1.2°, bolt 2.5 × 50, heat 20 a burst, cooling 2.2, overheat 70, vent 25, durability 750 | no movement penalty; tapping the Weapon Action vents | Armstech 2; the requisition vendors at Trusted, 420 credits |

The weapon reference (`docs/BALANCE.md`) gains a row each; a lightsaber stays the Force user's best
weapon.

### 9.2 Material mods

| Id | Slot | Effect | Made |
|---|---|---|---|
| `mod_starfall_edge` | EDGE | +12% melee damage to droids and constructs, +25% durability | Armstech 2: 2 starfall ingots, 1 alloy plate |
| `mod_clan_iron_edge` | EDGE | the weapon blocks a lightsaber without losing durability (as the electrostaff does) and takes 15% less guard drain | Armstech 4: 3 clan iron, 1 alloy plate |
| `mod_aurelite_barrel` | BARREL | spread -30%, bolt damage +5% | Armstech 3: 3 aurelite shards, 1 alloy plate |
| `mod_duskglass_cell` | CELL | heat per shot -20% | Armstech 3: 3 duskglass shards, 1 power core |

### 9.3 3D in the hand

The pistol, vibroblade, vibrosword and electrostaff, and the four new weapons, get 3D models in the hand
(the flat icons stay in the inventory), in the style of the rifle's and repeater's models: original,
used-universe machinery, no copied props. Troopers and NPCs holding them show the new models.

---

## 10. Hilt rendering

### 10.1 Item definitions

- `items/lightsaber.json` (generated by `tools/assets/hilts/build.py`, 13.1):
  - in `gui`, `fixed` and `on_shelf`: the 3D hilt (a `gui` transform on `lightsaber/base.json`, the
    hilt diagonal) with, while lit, a short glowing blade stub tinted by `oldrepublic:blade_color`; no
    special model, so no blade trail records in a GUI;
  - elsewhere: empty while thrown; otherwise a composite of the pommel and grip selects and an emitter
    select whose cases each hold the emitter's model and its blade (`oldrepublic:saber_blade` with that
    emitter's mouth, tilt and quillons). The idle and guard folders collapse into one (their geometry
    and transforms are the same).
- The staff and the training sabers follow the same pattern; part items get 3D icons through wrapper
  models whose `gui`, `fixed` and `ground` transforms frame their band.
- Every hilt model's metal faces have `tintindex` 0, fed by the tint source `oldrepublic:hilt_material`
  (field `part`: `emitter`, `grip` or `pommel`): the saber's material for that part, a loose part's own
  material, white otherwise.
- If the 3D icons read worse in a 16-pixel slot than today's flat icons, the flat icons stay
  (`build.py --flat-icons`); the lead decides from the screenshots.

### 10.2 The model library (`tools/assets/hilts/`)

- `lib.py` (the lead's): `octo` (a regular octagonal prism of inradius r between two heights: two
  axis-aligned bars and two turned 45 degrees; caps on request), `lathe` (stacked octagons, caps where
  the radius steps), `ring` (a hollow octagon), `box`, `side_box` (a detail on octagon side k), `turn`,
  `around` (copies about the axis), `bend` (the Penitent's neck), `staff_upper` and `staff_lower` (10.3),
  `Paint` (which texels a face shows: a window at 4 texels per unit, the whole region, a column of a
  strip wrapped around an octagon, or one texel), `Sheet` (a 64×64 texture painter: brushed metal,
  ridges, knurl, wraps, vents, glass, wear, rivets), `Blade`, `Design` (with `problems()`, every rule of
  6.1 and 6.2) and `registry()`. Quarter turns are baked into the coordinates; any other rotation is
  written as one axis when it is one, else as the game's Euler angles (applied Z, then Y, then X:
  `CuboidRotation$EulerXYZRotation` calls `Matrix4f.rotationZYX`). `--selftest` checks the helpers,
  `--designs` every design; CI's checks job runs both.
- `common.py` (the lead's): `textures/item/hilt/common.png` ("#common"), the shared swatches (tinted
  steel in four finishes, rubber, ridged rubber, gunmetal, brass, a leather wrap, the mouth, vents,
  knurl, glass, ember, flat colours and bevelled buttons); `paint(name)` hands them out.
- `preview.py` (the lead's): a software renderer of any element list that follows the game's face
  corners, UV rotations and element rotations, textured, tinted and shaded, from any angle, with a blade
  stub at the emitter's mouth; parts, assembled hilts, the staff, contact sheets, every material, and
  the hilt at first person and inventory sizes. Previews go to a scratch folder (`--out`), never the
  repository.
- `emitters.py` (HE), `grips.py` (HG), `pommels.py` (HP): `DESIGNS`, one `Design` per variant id: its
  elements in hilt model coordinates (6.2), its own optional 64×64 sheet ("#part"), and for emitters its
  blade data (10.4). Each starts with the lead's reference design (Guardian, Ridged, Weighted).
- `build.py` (HC): writes every hilt model, texture, item definition and icon model from the three
  modules; `--only emitters|grips|pommels|items` writes one slot's files (or the item definitions and
  base models); `--out DIR` writes under DIR instead of the assets folder, for a dry run.
  `gen_textures_saber.py` stops writing hilt part models and part item definitions.

### 10.3 The staff

The staff grip spans y 4.6 to 11.4 in the staff model. Its upper emitter is the emitter design moved up
7.7 so its base sits at 11.4 (`lib.staff_upper`); its lower one is the upper one turned 180 degrees about
the z axis through the staff's centre (8, 8, 8) (`lib.staff_lower`), so the staff is point-symmetric: a
mirror would turn faces inside out, and a bent or one-sided emitter keeps its hand at both ends. Blade
mouths follow (10.4).

### 10.4 Blades per emitter

`SaberBladeRenderer.Unbaked` gains optional fields, so today's JSON stays valid:
`mouth` ([x, y] in model units, default [8, 5.6]; for a staff the upper mouth; the lower blade leaves
from (16 − x, 16 − y) in the opposite direction, 10.3), `tilt` (degrees about the model's z axis,
default 0, positive toward +x) and `quillons` (optional `{"y": …, "z": …, "length": …}`: two side blades
from z 8 ± `z` at height `y`, pointing ±z; a staff's lower pair sits at height 16 − `y`). The training
saber keeps its kind.

### 10.5 The thrown saber

The flying saber draws its real hilt (an `ItemStackRenderState` of a copy of the stack without
`saber_thrown`, spun with the blade) instead of the grey capsule.

### 10.6 The workbench projector

The projector draws the assembled hilt (the bench's parts as a client-side stack, through its 3D icon)
instead of three flat part icons. A larger 3D preview is the stretch goal.

---

## 11. Sounds

`tools/assets/sounds/events_m7.json` and `gen_sounds.py --m7` (original, synthesised as before). The ids
follow the existing families (`workbench.tune`, `vibroblade.swing`, `electrostaff.shock`,
`blaster.repeater.fire`); the recipe modules are SD's `forge_m7`, `saber_m7`, `melee_m7` and `blaster_m7`.

| Id | Module | What |
|---|---|---|
| `workbench.forge` | `forge_m7` | a hammer on hot metal with a short hiss (Forge and Re-forge) |
| `workbench.study` | `forge_m7` | a soft scanner sweep and click (Study) |
| `saber.vanguard.ignite`, `.hum`, `.retract` | `saber_m7` | heavier and lower than the Guardian |
| `saber.warden.ignite`, `.hum`, `.retract` | `saber_m7` | an unstable crackle over the hum (the quillons venting) |
| `saber.watchman.ignite`, `.hum`, `.retract` | `saber_m7` | a clean, old-fashioned tone |
| `saber.penitent.ignite`, `.hum`, `.retract` | `saber_m7` | low and menacing, a slow growl |
| `saber.choir.ignite`, `.hum`, `.retract` | `saber_m7` | a harmonic, choral hum |
| `saber.set_pulse` | `saber_m7` | the Long Hymn's resonant pulse |
| `saber.set_mark` | `saber_m7` | Curator's Eye marking a foe |
| `vibro_axe.swing`, `vibro_axe.sunder` | `melee_m7` | a heavy whoosh; a crack of a broken guard |
| `force_pike.charge`, `force_pike.stun` | `melee_m7` | a rising buzz; an electric snap |
| `blaster.scatter.fire` | `blaster_m7` | a short, wide blast with a scatter of small zips |
| `blaster.carbine.fire` | `blaster_m7` | one bolt of the burst, sharp and light |

`SaberHums` gains profiles for the five new emitters; the client's pitch table gets their entries.

---

## 12. Frozen Phase 0 contracts

Phase 0 (the lead) builds these before the workers start. Workers may add to them only through a
request; signatures never change.

### 12.1 Java

- `HiltParts`: appended, in this order:
  - `Emitter`: `VANGUARD`, `WARDEN`, `WATCHMAN`, `PENITENT`, `CHOIR`;
  - `Grip`: `BANDED`, `KNURLED`, `FLUTED`, `RELIQUARY`, `WATCHMAN`, `PENITENT`, `CHOIR`;
  - `Pommel`: `RINGED`, `DOMED`, `WATCHMAN`, `PENITENT`, `CHOIR`;
  - and `enum Slot { EMITTER, GRIP, POMMEL }` with `static Slot slot(Part part)`.
- `com.oldrepublic.saber.HiltMaterial`:

  ```java
  public enum HiltMaterial {
      STEEL, DURASTEEL, STARFALL, AURELITE, DUSKGLASS, SUNSTONE, CLAN_IRON, HYMNSTEEL; // 3.1 order; append only
      public static final Codec<HiltMaterial> CODEC;                    // by id; an unknown id decodes as STEEL
      public static final StreamCodec<ByteBuf, HiltMaterial> STREAM_CODEC; // by ordinal
      public String id();          // "steel", "clan_iron", ...
      public int tier();           // 0 to 5
      public int tint();           // RGB, 3.1
      public Identifier itemId();  // the material item one unit costs
      public static @Nullable HiltMaterial byId(String id);
  }
  ```
- `com.oldrepublic.saber.HiltMaterials`:

  ```java
  public record HiltMaterials(HiltMaterial emitter, HiltMaterial grip, HiltMaterial pommel) {
      public static final HiltMaterials DEFAULT;                        // all steel
      public static final Codec<HiltMaterials> CODEC;                   // optional fields "emitter", "grip", "pommel" (steel)
      public static final StreamCodec<ByteBuf, HiltMaterials> STREAM_CODEC;
      public HiltMaterial of(HiltParts.Slot slot);
      public HiltMaterials with(HiltParts.Slot slot, HiltMaterial material);
  }
  ```
- `SaberComponents`:
  - `SaberConfig` gains a ninth component, `HiltMaterials materials`, last. The codec reads it with
    `optionalFieldOf("materials", HiltMaterials.DEFAULT)` (not written while default, so old stacks
    round-trip unchanged); the stream codec appends it. The 5-, 7- and 8-argument constructors stay and
    give `DEFAULT`; every `with…` keeps the materials; new `withMaterials(HiltMaterials)`.
  - `HILT_MATERIAL` (`oldrepublic:hilt_material`, a `HiltMaterial`, persistent and synced) on loose hilt
    parts; absent means steel. `static HiltMaterial material(ItemStack part)`.
- `com.oldrepublic.saber.HiltSet`:

  ```java
  public enum HiltSet {
      COLLECTOR, WATCHMAN, PENITENT, CHOIR;
      public String id();
      public static @Nullable HiltSet of(HiltParts.Part part);
      public static int count(SaberConfig config, HiltSet set, boolean staff); // a staff counts its emitter and grip
      public static @Nullable HiltSet active(SaberConfig config, boolean staff); // the set with two or more parts, or null
  }
  ```
- `SaberItems`: the 17 new part items (`EMITTER_VANGUARD` … `POMMEL_CHOIR`), and

  ```java
  public static ItemStack partStack(HiltParts.Part part, HiltMaterial material);
  public static ItemStack buildSaber(CrystalAttunement crystal, BladeColor color, Emitter emitter, Grip grip, Pommel pommel,
          HiltMaterials materials, SaberBalance balance);
  public static ItemStack buildStaff(CrystalAttunement crystal, CrystalAttunement second, BladeColor color, Emitter emitter,
          HiltMaterials materials, SaberBalance balance);
  public static boolean isCraftableDesign(HiltParts.Part part);
  public static boolean isKnownByDefault(HiltParts.Part part);
  public static boolean isSetPart(HiltParts.Part part);
  ```
- `SaberStats`: `public SaberStats withMaterials(HiltMaterials materials, boolean pommel, SaberBalance b)`
  (Phase 0 returns `this`; HS implements it), called by `of(SaberConfig, …)` and by the staff path of
  `of(ItemStack, …)` after the parts and before the length and tunings.
- `SaberAssembly`: construct reads each part's material into the config (a staff: emitter and grip);
  disassembly gives each part back with its material; the preview counts them.
- `SaberBalance`: every field named in 3.2, 3.3, 5.2, 6.3 and 7.2, with the defaults given there.
- `com.oldrepublic.registry.M7Items`: blocks `STARFALL_ORE`, `DEEPSLATE_STARFALL_ORE`, `STARFALL_ROCK`,
  `CLAN_IRON_ORE` (with block items); items `RAW_STARFALL`, `STARFALL_INGOT`, `HYMNSTEEL_INGOT`;
  weapons `VIBRO_AXE` (`VibroWeaponItem`, `Kind.AXE`), `FORCE_PIKE` (`Kind.PIKE`), `SCATTER_BLASTER`
  (`BlasterItem`, `Kind.SCATTER`), `BLASTER_CARBINE` (`Kind.CARBINE`); mods `MOD_STARFALL_EDGE`,
  `MOD_CLAN_IRON_EDGE`, `MOD_AURELITE_BARREL`, `MOD_DUSKGLASS_CELL` (`Mods.slotOf` knows them). The
  kinds are appended in Phase 0; their numbers are the weapon workers'.
- `ModSounds`: a Milestone 7 section with every event of section 11.
- Client: the tint source `oldrepublic:hilt_material` (registered in `SaberModelProperties`).
- `VisualsClientTest`: the named looks `hilts` (`HiltLook`, HC), `weapons` (`WeaponLook`, WB) and
  `ores` (`OreLook`, OR), each a stub to fill.

### 12.2 Tools and data

- `tools/assets/hilts/lib.py`, `common.py`, `preview.py`, and a reference design in each slot module.
- Placeholder art for every new id (copies of the nearest existing icon or block), so no client test
  sees a missing model.
- `tools/check_m7_ids.py` (each M7 id has its name, item definition, model, texture, block state, loot
  table and sound event), run by CI's checks job with `--report` until integration, then strictly.
- `tools/assets/sounds/events_m7.json`, the `--m7` mode of `gen_sounds.py` and placeholder audio.
- `tools/ownership_m7.json` and the CI overlap check for it.
- `ContractsM7GameTests` (12.1's codecs, enums, ids, assembly round trip), registered in the gametest
  `fabric.mod.json`.

---

## 13. Ownership and workers

### 13.1 Workers

| Code | Area | Owns |
|---|---|---|
| HS | hilt stats, materials, traits and sets (Java) | `SaberStats.java`, `SaberBalance.java` (the M7 block), `HiltPartItem.java`, `LightsaberItem.java` and `SaberStaffItem.java` (tooltips), `SaberCommands.java`, `SaberM7Feature.java` (the Phase 0 stub's body), new `saber/HiltTraits.java` and `saber/HiltSetEffects.java`, the trait and set hooks in `SaberCombat.java` and `SaberGuard.java`, `client/saber/HiltSetFx.java` (the sets' client effects); lang part `hilts.json`; test class `HiltStatsGameTests` |
| HF | the Forge (Java) | new `saber/forge/**`, `saber/workbench/**`, `client/saber/workbench/**`, `SaberAssembly.java` (after Phase 0), `tools/assets/gen_textures_forge.py` and the Forge tab's sprites; lang part `forge.json`; test class `ForgeGameTests` |
| HC | hilt client and the assembler | `tools/assets/hilts/build.py`, `tools/assets/gen_textures_saber.py`, `client/saber/**` except `workbench/**`, `SaberAudio.java` and `HiltSetFx.java`, the thrown saber's client code, every hilt item definition and the `lightsaber/` base and icon models; `gametest/visuals/HiltLook.java` |
| HE | emitter designs (art) | `tools/assets/hilts/emitters.py`; `models/item/lightsaber/emitter_*`, `textures/item/hilt/emitter_*` |
| HG | grip designs (art) | `tools/assets/hilts/grips.py`; `models/item/lightsaber/grip_*`, `textures/item/hilt/grip_*` |
| HP | pommel designs (art) | `tools/assets/hilts/pommels.py`; `models/item/lightsaber/pommel_*`, `textures/item/hilt/pommel_*` |
| OR | ores, materials, worldgen | new `world/ore/**`; the M7 blocks' and materials' assets, block loot tables, tags and recipes; `wilds/starfall_crater` (structure, set, pools, templates, tag) and `tools/assets/structures/or/**`; `tools/assets/gen_textures_ores_m7.py`; the Tyrvann clan iron feature in `gen_structures_m5.py` (run only with a new `--planets` flag that writes the planets' worldgen files and nothing else, with `git status` compared before and after) and its output; lang part `ores.json`; `gametest/visuals/OreLook.java`; test class `OresGameTests` |
| BD | boss drops, sets' sources, elites | the reward code named in 7.3 (`quest/story/CollectorRewards.java`, `quest/story/KaastDuel.java`, `quest/story/UlvaineChoice.java`, `boss/ulvaine/UlvaineFight.java` and `DarthUlvaine.java`, `boss/raid/RaidRewards.java`), new `boss/drops/**` (the Phase 0 `BossDropsFeature`'s body, the elites' and captains' drops of 7.4), the NPC saber lines of 7.5 (`boss/OrrenVasch.java`, `enemy/SithAcolyte.java`), the chest loot tables of 6.4 (`jedi_ruin`, `sith_tomb`, `greater_tomb`, `crystal_cave_hidden`, the wilds' hermitage, cove and dig chests), `entities/poacher_captain.json`, the vendor stocks of 6.4 and 9.1, `gameplay/echo_cache.json`; lang part `boss_drops.json`; test class `BossDropsGameTests` |
| WM | melee weapons | `equipment/melee/**`, `client/equipment/MeleeM7Client.java` and `VibroGuardHud.java`, the vibro weapons' and the axe's and pike's assets, their schematics, the edge mods' effects; `tools/assets/gen_weapons_melee_m7.py`; lang part `melee_m7.json`; test class `MeleeM7GameTests` |
| WB | blasters | `equipment/blaster/**`, `equipment/mod/ModEffects.java`, `client/equipment/BlasterPoses.java`, `BlasterPose.java`, `BlasterHud.java`, `BlasterM7Client.java`, `client/enemy/MuzzleGlowLayer.java`, the blasters' assets, their schematics, the barrel and cell mods' effects; `tools/assets/gen_weapons_blasters_m7.py`; lang part `blasters_m7.json`; `gametest/visuals/WeaponLook.java`; test class `BlasterM7GameTests` |
| SD | sounds | `tools/assets/sounds/*_m7.py`, the M7 `.ogg` files, `sounds.json` and the `sounds.json` lang part (the subtitles; there is no separate M7 sound part), `saber/SaberHums.java`, `client/saber/SaberAudio.java`; never runs `gen_sounds.py --java` (`ModSounds` is the lead's) |

`tools/ownership_m7.json` is the machine-readable copy; everything else is the lead's: the Phase 0
contracts (`HiltParts`, `HiltMaterial(s)`, `HiltSet`, `SaberComponents`, `SaberItems`, `M7Items`,
`ModSounds`, `Mods.slotOf`), the Phase 0 stubs' call sites, `lib.py`, `common.py`, `preview.py`,
`common.png`, the check scripts, `events_m7.json`, the contract tests, the gametest `fabric.mod.json`
(the lead registers every worker's test class at integration), the older generators, every other lang
part and the top-level docs.

### 13.2 How the work fits together

- HE, HG and HP write designs against `lib.py` and preview them with `preview.py`, each slot with the
  other slots' reference designs, and keep to 6.1 and 6.2. HC's `build.py` turns them into game files;
  until it exists they preview only. They share one palette (`common.png`) and match the joins.
- HS implements the numbers and traits; HF calls `SaberStats` for its stat change line and `SaberItems`
  for its parts; BD hands out parts through `SaberItems.partStack(part, material)`.
- WM and WB code against the Phase 0 items and kinds. WB writes `WeaponLook` for all eight weapons.
- In-game checks: the lead pushes the workers' files and runs the `visuals` workflow (`hilts`,
  `weapons`, `ores`); the screenshots go back to the workers for a second pass.

### 13.3 Process

- One working tree; workers never commit, push, stash or switch branches. Never touch a file you do
  not own: put the exact change in your report under "Requests".
- Check every path you touched with `python3 tools/check_ownership.py --table tools/ownership_m7.json
  <code> --files <paths>` before you report.
- There is no local Java build: the lead compiles on CI. Mark every unverified API with
  `// API-CHECK: <what>`.
- Art workers render previews of everything they made into their scratch folder and look at them.

### 13.4 The report

Every worker reports: every file created, changed or deleted with the ownership check's output; the
numbers they chose where this spec left a choice; requests for files they do not own (exact changes);
API-CHECKs, tests written, anything cut (with section 1's order); preview paths; what a person should
look at in game.

---

## 14. Tests

- **The lead:** `ContractsM7GameTests` (12.1), and every existing test the contracts touch.
- **HS:** `HiltStatsGameTests`: each tier's bonus per slot, the worked example of 3.2, the order of
  steps, the parry cap, every new design's numbers, set counting (a staff's two), the traits' math, and
  today's pinned numbers unchanged for steel.
- **HF:** `ForgeGameTests`: study, forge and re-forge rules and costs, inventory changes, a full socket,
  a legendary part keeping its quality, `ForgeData` surviving death and a save, assembly and disassembly
  carrying materials.
- **OR:** `OresGameTests`: the ore drops and tools, the smelting and hymnsteel recipes, the configured
  and placed features parse, the crater structure places on a superflat test world.
- **BD:** `BossDropsGameTests`: each new drop once per guard, the pity counters, today's reward tests
  untouched.
- **WM, WB:** `MeleeM7GameTests` and `BlasterM7GameTests`: the new kinds' numbers, Sunder, the stun
  thrust, the pellets, the burst, the four mods.
- **Visuals:** `HiltLook` (the hilts, materials, sets, crossguard, curved, the staff, inventory, the
  Forge tab, first person, a hand close up, a throw), `WeaponLook` and `OreLook`. Their screenshots are
  the acceptance review and go in the report to Gabriel, beside the "before" shots of 23 October.

## 15. Docs

The lead updates `docs/BALANCE.md` (a Milestone 7 section: materials, designs, sets, forge costs,
weapons, mods), `docs/ASSET_MANIFEST.md` (the hilt library and generators), `docs/FEATURE_CHECKLIST.md`,
`docs/KNOWN_ISSUES.md`, `docs/DECISIONS.md`, `docs/assets/saber.md` and `sabers_m5.md` (the new art
rules), and the how-to-play guide. Workers put their text for those in their reports.

## 16. Revision notes

Phase 0, 23 October:

- **Rotations, read from the 26.2 jars** (`tools/api-probe/m7.txt`, `m7b.txt`): an element's
  `{"x", "y", "z"}` rotation is applied as Z × Y × X (`CuboidRotation$EulerXYZRotation` calls
  `Matrix4f.rotationZYX`), and `{"axis", "angle"}` turns right-handed about the positive axis, so a
  negative angle about z tilts +y toward +x, as the vanilla wall torch's −22.5 leans out from its wall.
  Any angle is accepted. `lib.py` writes one axis where it can and Euler angles otherwise, and checks the
  matrix rebuilds; `preview.py` renders with the same rules, and its self-test checks them against
  `lib.py`'s baked quarter turns.
- **The staff is point-symmetric** (10.3, 10.4): the lower emitter is the upper one turned 180 degrees
  about z through (8, 8, 8), not mirrored about y 8, which would turn its faces inside out and flip a
  bent emitter's hand. The lower mouth is (16 − x, 16 − y), its blade pointing the other way.
- **The model library** (10.2) names what was built: `lathe`, `ring`, `side_box`, `turn`, `around`,
  `bend`, `staff_upper` and `staff_lower` replace `mirror_y` and `rot_z`; `Design.problems()` holds the
  rules of 6.1 and 6.2 and CI runs it on every design. `build.py` gains `--only items` and `--out DIR`.
- **Sound ids** (11) follow the existing families: `workbench.forge` and `workbench.study`,
  `saber.<emitter>.ignite|hum|retract`, `saber.set_pulse`, `saber.set_mark`, `vibro_axe.*`,
  `force_pike.*` and `blaster.<kind>.fire`. SD owns `sounds.json` and the `sounds.json` lang part; there
  is no `sounds_m7` part, and `ModSounds` stays the lead's (no `gen_sounds.py --java`).
- **Balance:** `choirPulseKnockback` (0.4) joins the Choir set's fields (7.2).
- **The bench's mod slot** takes any item `Mods.slotOf` knows, so the four material mods (9.2) fit it
  like the M4 and legendary mods; it used to take only the M4 mods.
- **Phase 0 stubs and their call sites:** `SaberM7Feature` (HS), `forge/ForgeFeature` (HF),
  `world/ore/OreFeature` (OR) and `boss/drops/BossDropsFeature` (BD) are initialised by
  `M7Contracts.init`; `ForgeClient` (HF) and `HiltSetFx` (HS) by `OldRepublicModClient`, and
  `MeleeM7Client` (WM) and `BlasterM7Client` (WB) by `EquipmentClient`. Each worker fills its own stub and
  touches no call site. `GearTooltips` asks `MeleeM7Client.modEffect` (the edges, WM) and then
  `BlasterM7Client.modEffect` (the barrel and cell, WB) for a material mod's effect line, and
  `SaberModelProperties` registers the `oldrepublic:hilt_material` tint source.
- **Ownership** (13.1) is corrected to the files that hold the work: BD's reward code
  (`KaastDuel`, `UlvaineChoice`, `DarthUlvaine`, `boss/drops/**`, the NPC saber holders and the chests
  by name), HS's `SaberCommands`, `SaberStaffItem` and `HiltSetFx`, WM's and WB's client classes and
  generators, OR's generator and the `--planets` rule for `gen_structures_m5.py`, and the lead
  registering every worker's test class at integration.

Integration, 23 October:

- **Quillons run along ±z** (6.2, 8.1, 10.4): first person looks at the thumb side (+x), so side blades along
  ±x pointed at the camera (one a blob, the other hidden). The Warden is built along x and turned a quarter
  about the axis; the `quillons` field is `{"y", "z", "length"}`, and its port check in `lib.py` allows z 8 ± 3.0.
- **Octagon seams:** a nudged diagonal bar is about 1% wider (`tan(22.5 + 2 × 0.1)` degrees), so its side
  reaches past both vertices and no hairline crack shows along an octagon's edge (HE's request).
