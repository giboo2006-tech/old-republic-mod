# Milestone 5: Advanced Features (design spec)

This is the working spec for Milestone 5, the last entry of the roadmap in `DESIGN_BRIEF.md` section 17. It turns that entry into concrete, testable features that build on Milestones 1 to 4:

- Expanded flight and ship systems.
- Endgame encounters.
- Further faction stories.
- Optional large-scale systems supported by the established foundation.

Milestone 4 deferred a long list to Milestone 5 (`MILESTONE_4.md` 1, "Left for later milestones"). This spec takes the items that fit the roadmap entry and the brief's endgame list (`DESIGN_BRIEF.md` 14), and names the few it leaves for the owner's improvement list (section 1, "Left for later"):

- free flight, ship combat and space exploration (the brief's "later" and "finally" ship stages, 11);
- a second ship class;
- the storm jungle and the wasteland port (`DESIGN_BRIEF.md` 10);
- redemption and corruption questlines that change path, with a living Sith Lord boss (`DECISIONS.md`, Milestone 4);
- ranks above 3 (Master, Darth);
- the Buyer's story, a Mandalorian clan and a Hutt cartel as faction stories;
- the remaining powers of the brief (Stasis, Valor, mind influence, Drain, Fear, Rage; Protect and Crush already exist as branches, 9);
- more saber forms, dual-wielding, blade-length variants and crystal tuning;
- endgame encounters, a raid-size boss and legendary component crafting.

All numbers here are defaults. As before, live values go into `config/oldrepublic/balance.json` through `BalanceConfig` sections; section 16 lists every new key. Where this spec and the brief disagree, the brief wins and the difference goes into `DECISIONS.md`.

Contents:

1. Scope, cut order, and what is left for later
2. Player journey
3. Worlds: Ghessar and Tyrvann
4. Flight
5. Space and ship combat
6. Chapter Five: "The Long Hymn"
7. Factions: the Vessba kajidic and Clan Hask
8. Paths and ranks: the Fall, the Return, Master, Darth and Wayfarer
9. Force powers and the second power bar
10. Lightsabers: forms, dual-wielding, blade length, crystal tuning and hum profiles
11. Legendary components
12. Enemies, bosses and the raid
13. Controls and HUD
14. Persistence, multiplayer, dedicated server and config
15. Effects and sounds
16. Balance
17. Architecture and work split
18. Shared identifiers
19. Test plan
20. Lore notes for `DECISIONS.md`
21. Open questions
22. Revision notes

---

## 1. Scope

### In Milestone 5

| Area | What ships |
|---|---|
| Story | **Chapter Five, "The Long Hymn".** The Buyer is named: **Darth Ulvaine**, a Sith Lord cast out of the Empire's councils, who wants the player alive because their Resonance makes stones sing. The trail runs through **Saltgate**, a Hutt-run port on the wasteland world **Ghessar**, where the Buyer's factor keeps his books, then through Ulvaine's blockade around **Tyrvann**, a jungle world under a storm that never ends. Three verse stones ward the **Hymnal**, an ancient Sith temple built around a song-engine called the Long Hymn. Ulvaine waits at its heart. The chapter ends with her fate, a report to the mentor and the way open to the fourth rank. |
| Factions | Two new reputation tracks with vendors, contracts and attitudes: the **Vessba kajidic** (the Hutt cartel that owns Saltgate) and **Clan Hask** (the Mandalorian clan Torvin Kaast left, hired to guard Tyrvann). How the player met Kaast in Chapter Four sets where they start with the clan. |
| Paths and ranks | **The Fall** (Jedi to Sith) and **the Return** (Sith to Jedi): four-step questlines that change path in play, each at most once per character and at a real cost (8.4). **Master** and **Darth** (rank 4), and the **Wayfarer** title for Independents, each with a ceremony, a robe or mantle, a granted power and two more power slots. |
| Flight | **Free flight.** The pilot takes the helm and flies the ship by hand, on a planet and in space: throttle, mouse steering, climb and dive, boost, and landing on any clear, level ground as well as on pads. Flying up out of a planet's sky enters its orbit in space; diving into a planet from orbit enters its sky over the matching ground. Hyperspace jumps start from orbit and end in the destination's orbit. The M4 autopilot (pad to pad) stays for players who would rather not fly. A **feasibility gate** comes first (4.1). |
| Ship classes | A second hull class, the **interceptor** (fast, agile, one seat and a gunner, a small hold), fitted in place of the courier at the Saltgate shipwright. One ship per player, as in M4. |
| Space | Five orbit dimensions, one per known system (the home system, Selvaren, Varkhast, Ghessar and Tyrvann), all sharing one dimension type. Black sky, stars, a sun and the planet below drawn as large bodies, asteroid fields, and a derelict station to explore on foot. |
| Ship combat | Laser cannons with heat, concussion missiles with lock-on, shields and hull damage, a disabled-ship rescue instead of permanent loss, three enemy starfighters and a blockade gunship, random patrol encounters and daily space bounties. |
| Worlds | Two planet dimensions in the M4 style. **Ghessar**: dune sea, salt flats and canyons, a second sun, sandstorms, sunstone, the **sand scour** (a burrowing ambusher), the port hub **Saltgate**. **Tyrvann**: storm jungle, black mire and basalt ridges, a permanent storm with real lightning on high ground, stormglass, the **stormback** (a beast that charges itself from lightning), Clan Hask's **holdfast** as a small hub, the three verse stones and the Hymnal. |
| Powers | **Force Stasis** (Jedi), **Force Valor** (granted to a Master), **Force Drain** and **Force Fear** (Sith), **Force Rage** (granted to a Darth) and **Mind Trick** (Exploration tree, every path; limited mind influence on suitable NPCs, in combat and in dialogue). Each has branches, mastery, counterplay, animation, VFX and SFX. A **second power bar** of four slots, used by holding the Focus key. |
| Lightsabers | Three forms (**Makashi**, **Djem So**, **Jar'Kai**), **dual-wielding** two sabers, **short and long blades**, **crystal tuning** at the workbench (five tunings, stat-only, never colour), and an ignition and hum profile per emitter. |
| Crafting | **Legendary components**: a fourth quality tier and ten named components (saber parts, armour and weapon mods, ship parts), each from a legendary schematic and materials from the new worlds and the raid, with guaranteed progress. |
| Enemies | **Cartel enforcers** (Saltgate's guns and blades), **Clan Hask warriors** (the M4 hunter's kin, with a blade variant and a champion), **Hymnsworn** (Ulvaine's Force cultists who call lightning), the sand scour and the stormback, and three starfighters. |
| Bosses and endgame | **Darth Ulvaine**, a living Sith Lord duellist fought in three phases in the Hymnal, with an ending choice and a daily echo. **The Long Hymn**, a raid-size encounter against **the First Cantor**, for one to eight players, once a week per player for rewards. Daily space bounties and the faction contracts round out the loop. |
| Systems work | Planets are no longer hard-coded to two (the lead's Phase 0 sweep, 17.1). A generic boss music service replaces the two per-boss mixins. A shared raid session with participant scaling past four. Per-dimension weather. Control kinds for Fear, Stasis and pacify, replacing two ad hoc fear maps. |
| Controls and HUD | The flight HUD and controls, the Focus key and the second bar, the refit and tuning screens, the star map, and the Standing panel's rank 4 checklist. |

### Left for later (on purpose)

The roadmap ends with this milestone. These items from the brief's endgame list and the M4 deferrals stay out of M5 and go to the owner's improvement list:

- **A personal enclave** (`DESIGN_BRIEF.md` 14, "Building a personal enclave"). It needs land claims, build protection and a home menu, which none of the M5 systems provide.
- **Training NPC apprentices** (the brief calls it "a later expansion").
- **Ship interiors players walk around in.** The ship stays an entity players ride; there is no walkable cabin.
- **Large-scale battles** (armies on the ground) beyond the blockade and the raid.

Not in M5, by design (unchanged from M4):

- voice acting (barks stay subtitled);
- companions or hired crew NPCs;
- PvP faction warfare (ships cannot damage other players' ships, 5.4);
- player-built landing pads other than the M4 landing beacon.

### Cut order (if the milestone runs long)

Nothing from the roadmap entry itself is on this list: free flight, space, ship combat with cannons, Chapter Five, both factions, the Fall and the Return, rank 4, the six powers, dual-wielding, crystal tuning, legendary components, Ulvaine and the raid all stay. The lead cuts from the top and records each cut in `DECISIONS.md` and `KNOWN_ISSUES.md`.

1. Ghessar's second sun (3.2). Ghessar keeps one sun.
2. The gunner seat (5.2). Crew ride as passengers.
3. The derelict station (5.7). The *Spindle Capacitor* schematic then drops from the Long Hymn's roll (11.2).
4. Hum profiles per emitter (10.5). Every saber keeps the M1 hum.
5. Blade-length variants (10.3).
6. The Jar'Kai form (10.1). Dual-wielding keeps its off-hand penalty.
7. Space bounties (5.8), with the fence's cipher (7.1) and the bounty deed (8.5). Patrol encounters stay.
8. Concussion missiles (5.3). Cannons only.
9. The interceptor and the refit (4.9). The courier is the only class.
10. The Fall, Return and solo raid bot runs (19.3). The questlines and the raid are checked by game tests and by hand.

---

## 2. Player journey

1. **The call.** Chapter Four is complete. One in-game day later, or after the next sleep, at a safe moment, the mentor sends word (an Independent hears Tessaly Arn through the Compass): the Buyer's factor has surfaced at **Saltgate**, a Hutt port on **Ghessar**, asking after the player by name. The coordinates come with it. Saves that finished Chapter Four in M4 enter Chapter Five on their next join.
2. **The helm.** The courier's nav computer, rebuilt in Chapter Four, now lets its pilot fly by hand. The objective offers a short lesson: lift off, fly up out of the sky, look down at the home world from orbit, open the nav and jump to Ghessar, then dive into Ghessar's sky and land at Saltgate. A player who would rather not fly can still pick Saltgate on the nav console and ride the M4 autopilot.
3. **Saltgate.** A walled port of sand-scoured prefab and old freighter hulls, owned by **Vessba the Hutt**. The factor, **Imrel Dask**, keeps his counting-house behind the kajidic's enforcers. Three ways lead to him; any one is enough:
   - **The kajidic's way:** two jobs for Vessba's majordomo (collect a debt in the canyons; run a cargo past Clan Hask's patrol in orbit), and Vessba grants an audience.
   - **The clan's way:** **Varra Hask**, the clan's envoy, wants the factor too: the Buyer's coin has bought her clan's guns for a work they despise. Earn her trust (a hunt for a sand scour matriarch, after a duel of honour if Kaast died by the player's hand), and the clan breaks into the counting-house with the player.
   - **The Force's way:** a Mind Trick on the counting-house doorman, or Fear for a Sith. Quick, and the kajidic notices.
4. **The factor.** Dask talks. The Buyer is **Darth Ulvaine**. She wants the player alive to give voice to the **Long Hymn**, a song-engine sealed under Tyrvann's storms, and she has closed Tyrvann's sky with her interceptors. Dask plays her last message: the first time the player sees the Buyer's face. Spare him, hand him to the clan, or end him.
5. **The blockade.** At the Saltgate shipwright the player's ship gets **laser cannons** (paid by the factor's confiscated funds). The jump to Tyrvann ends in a battle in orbit: Ulvaine's interceptors and a gunship. If Clan Hask stands with the player, the clan's fighters turn their guns on the gunship. The player breaks through and dives into the storm.
6. **Tyrvann.** Under the storm: Clan Hask's **holdfast** (welcome, wary or hostile, depending on what came before), the black mire, the stormwood, and the basalt ridges where lightning strikes. Three **verse stones** ward the Hymnal. At each, Hymnsworn cultists guard a stone that sings; the player silences it with the Force and sees a piece of the Hymn's past: **the First Cantor**, the Sith who wrote the Hymn to bind a world's voices to one will.
7. **The Hymnal.** With the stones silent, the temple opens. At its heart Ulvaine fights in three phases: **The Duel** (saber and lightning), **The Storm** (she calls the storm into the hall: lightning rods, pylons, Hymnsworn), and **The Hymn** (she sings: pulses of sound across the hall, which the player must break). At 10% she yields.
8. **The choice.** **Spare** her (she leaves Tyrvann broken, and the Hymn falls silent with her), **bind** her for the player's own order (the Watch's cells, the Empire's councils, or Clan Hask's bounty for an Independent), or **end** her. Each has a short scene and an alignment shift. The player reports to the mentor (an Independent to the Compass): Chapter Five is complete.
9. **The fourth rank.** A Jedi who meets the requirements is raised to **Master**, a Sith to **Darth**, an Independent named **Wayfarer** by the last of Tessaly's road (8.5). Each gains a robe or mantle, a power, and two more slots on the second bar.
10. **The endgame loop:**
    - **The Long Hymn** raid against the First Cantor, once a week for rewards, for one to eight players;
    - Ulvaine's echo in the Hymnal, once a day;
    - **space bounties** on the Saltgate and holdfast boards, once a day;
    - kajidic and clan contracts, with the M3 daily soft cap;
    - **legendary components** built from raid hymnstone and the new worlds' materials;
    - crystal tuning, the new forms, dual-wielding, and the power bar's eight slots.
11. **The Fall and the Return** run alongside, for players whose alignment has crossed far to the other side: a Jedi deep in the dark (alignment -50 or lower) after Chapter Four is offered the Fall; a Sith deep in the light (+50 or higher) is offered the Return (8.2).

A solo player at a normal pace finishes Chapter Five in about 8 to 10 hours, from level 15 to about 18; rank 4 asks for level 20 (section 16 has the XP budget).

---

## 3. Worlds: Ghessar and Tyrvann

The worlds worker (W3) owns both planets, their hubs' templates and placement, their sites, blocks, ambience and weather, the detention post on Varkhast (8.3), and the per-dimension weather rule (3.5). The planets follow the M4 pattern exactly (`MILESTONE_4.md` 3): data-pack dimensions with their own dimension types, noise settings and biomes, generated by a new `tools/assets/gen_structures_m5.py` modelled on `gen_structures_m4.py` (`PLANETS`, `CLIMATE`, biome and dimension JSON writers, templates, pools and anchor sidecars checked against `SiteAnchors.Anchor`).

### 3.1 More than two planets (the lead's Phase 0 sweep)

M4 wrote "two planets" into about 25 places, several of which silently fall back to Selvaren for an unknown planet (the lead's survey lists them; Phase 0 fixes every one). Phase 0 makes every one of them handle any `Planets.Planet`, so W3 adds two planets as data and table rows:

- `Planets.Planet` appends `GHESSAR` and `TYRVANN` (keys `oldrepublic:ghessar`, `oldrepublic:tyrvann`); `Planets.isPlanet` becomes "any `Planet` except `HOME`".
- `Planets` gains the orbit keys (`oldrepublic:orbit_<planet id>`, one per `Planet`, 5.1): `orbit(Planet)`, `orbitOf(ResourceKey<Level>)` and `isOrbit(Level)`. An orbit is not a story level: `isStoryLevel` stays "home world or planet".
- Every `switch` over `Planet` outside the lead's contract files gets a `default` branch that does the safe thing for an unknown world (no hub template: refuse; no ambience bed: silence; no music: none). The places that picked Selvaren for "not Varkhast" (`PlanetHubs.template`, `PlanetContracts.planetOf`, `HubNpcs`, `MentorClient` accents) read a per-planet table instead.
- `Destination` appends `SALTGATE` (Ghessar) and `HOLDFAST` (Tyrvann). `NavData` and the ship screen's star chart read the destination list instead of four hard-coded entries.
- The M4 arrival rewards (`ChapterFourRepairs.onPlanetArrived` and `checkHub`: the banner, `chapter_four.planetXp` 400, `hubXp` 200, the entry, the codex and the host's chat greeting) read a per-planet table, and so does `HubNpcs.greet`. Ghessar: entry `saltgate`, codex `ghessar`, and at the hub codex `the_vessba_kajidic`. Tyrvann: entry `tyrvann`, codex `tyrvann`, and at the hub codex `clan_hask`. Neither greets in chat; the harbourmaster and Dorrun speak for themselves (6.3, 6.6).
- The `#oldrepublic:is_planet` biome tag gains the six new biomes; `is_ghessar` and `is_tyrvann` are new.
- The game-test server still has no planet dimensions (`MILESTONE_4.md` 17.1, `planetLevelsExistOnTestServer`): planet and orbit tests are client tests (19.2).

### 3.2 Ghessar, the wasteland port world

| | |
|---|---|
| Dimension | `oldrepublic:ghessar`; type `oldrepublic:ghessar` (an overworld copy): pale amber sky `#E9C89A`, dusty fog `#D8B27A`, no clouds (`cloud_color` alpha 0), music `oldrepublic:music.ghessar`, ambience per biome (3.6); beds work as on Selvaren and Varkhast (the M4 `bed_rule`: they set spawn and sleep when dark; respawn anchors do not work) |
| Terrain | `sea_level` -64 (no oceans); a low, rolling dune sea, flat salt pans and deep canyon country with mesas. Noise settings copy the overworld router with flattened continentalness and a shifted erosion range (W3 tunes it; the M4 router-copy method) |
| Biomes | `ghessar_dunes` (sand and sandstone, dead bushes, bleached ribs of giant beasts as a decor feature, temperature 2.0, no precipitation), `ghessar_saltflat` (a new `salt_crust` block over sandstone, white haze, rare salt pillars, no precipitation), `ghessar_canyons` (layered terracotta walls, red sand floors, `sunstone_ore` in the walls between y 40 and 120, cliff overhangs, no precipitation) |
| Second sun | The **twin sun**: a smaller, whiter sun drawn 40° behind the vanilla sun on the same arc, by a client mixin at the tail of `SkyRenderer#renderSunMoonAndStars` (W3, `mixin/planet`), only on Ghessar; W2 draws the same disc in `orbit_ghessar` (5.1, 13.2) (cut order 1) |
| Weather | **Sandstorms** instead of rain (3.5): Ghessar's own cycle, a storm every 3 to 5 in-game days for 3,000 to 6,000 ticks. Visibility drops (fog end 24 blocks), blowing-sand particles and a wind loop; sand scours surface twice as often. No rain, no lightning |
| Resources | `sunstone_ore` (canyons; drops 1–2 `sunstone`, Fortune applies; needs an iron pickaxe), `salt_crust` (salt pans; drops itself) |
| Creatures and foes | the **sand scour** (12.1) in the dunes and canyons; cartel enforcers at the kajidic's posts; desperate salvagers (the existing `scavengers` spawner group: scavenging crystal poachers, `count` 2–3) at debtor camps |
| Hub | **Saltgate** (3.2.1) |
| Worldgen structures | `crashed_freighter` (dune sea; spacing 28, separation 10; an old freighter hull half-buried in sand with a hold to search; contract `ghessar_recover`), `kajidic_post` (canyons; spacing 32, separation 12; a fortified kajidic waystation; contract `ghessar_escort` ends here) |
| Runtime sites | `debtor_camp` (per owner, for `c5_debt` and `ghessar_collect`: a salvagers' camp in a canyon 200–400 blocks from Saltgate; anchors `NPC_DEBTOR`, `STRONGBOX`, `SPAWNER`) |

#### 3.2.1 Saltgate

A walled port of sand-scoured prefab and old freighter hulls, placed at Ghessar's (0, 0) by `PlanetHubs.ensure` like the M4 hubs (raised on a platform if no fitting ground is found). Template `hub/saltgate`, about 63 by 63 blocks (the M4 `planets.hubFootprint`, which the surface search, the raised fallback, the hub ticket and `flight.hubBayRange` assume; Milestone 6: now 89 by 89, `hubFootprint` is gone, the searches, the fallback and the ticket read the template's own size, and `flight.hubBayRange` is 88), with these anchors (Phase 0 appends the new names; those that exist since M4, such as the pad bays, `HEART` (the hub's centre), `PAD_TERMINAL` (the M4 pad terminal: paid recall and restore), `CONTRACT_TERMINAL`, `MEDBAY` and `HOST`, are reused):

| Area | Anchors | What is there |
|---|---|---|
| Pad bays | `PAD_BAY_1`–`PAD_BAY_4` (M4) | four pads behind a gate; the harbourmaster (`HOST`) |
| The cantina | `CANTINA`, `VENDOR_FENCE`, `NPC_VARRA` | the fence (`cartel_fence`), Varra Hask, the bartender, the contract terminal (`CONTRACT_TERMINAL`, M4) |
| The counting-house | `COUNTING_HOUSE_DOOR`, `COUNTING_HOUSE_1`–`8` (guard posts), `NPC_DASK` | a two-floor strongroom behind the cantina; the doorman stands at the door; the 6.3 mission spawns its guards on the posts |
| The hangar | `VENDOR_SHIPWRIGHT`, `VENDOR_SPACER` | the shipwright and the spacer (M4 stock) |
| The audience hall | `NPC_VESSBA`, `NPC_TEEL` | Vessba on her dais, Ommo Teel beside her |
| Medbay and walls | `MEDBAY` (M4), `ENFORCER_1`–`6` | the medbay (the M4 respawn rule); enforcer posts on the walls (12.1, Attitude by kajidic standing) |

Every person in the table stands on a `hub_spawner` block at their anchor with a role (the M4 rule, `HubSpawners`): `host` gives the harbourmaster (the per-planet host table, 3.1), `vendor:<stock id>` the vendors, and the new roles `varra`, `vessba`, `teel`, `doorman` and `bartender` (W4 registers them; Dask is spawned by the counting-house mission, 6.4) and `enforcer` (W8 registers it, 12.1). The counting-house posts are plain anchors, where the 6.3 mission spawns its guards. Natural spawns are blocked in the hub's box (the M4 `PlanetHubs.allowLoad` rule). Hub NPCs are restored every 24,000 ticks (the M4 rule).

### 3.3 Tyrvann, the storm jungle

| | |
|---|---|
| Dimension | `oldrepublic:tyrvann`; type `oldrepublic:tyrvann`: bruised green-grey sky `#4A5A52`, dark fog `#2F3A35`, low heavy clouds (`cloud_height` 150, `cloud_color` `#3C4640`), music `oldrepublic:music.tyrvann`, ambience per biome (3.6); beds as on Ghessar (the M4 rule) |
| Terrain | `sea_level` 62; dense lowland jungle cut by black water, with sharp basalt ridges rising to y 160–200. The overworld router with raised jaggedness on the ridges (W3 tunes it) |
| Biomes | `tyrvann_stormwood` (tall dark trees built from dark oak and jungle parts, hanging moss, thick undergrowth, temperature 0.9, precipitation on), `tyrvann_mire` (mud, mangrove roots, black pools, fog, temperature 0.8, precipitation on), `tyrvann_basalt` (basalt, blackstone and smooth basalt ridges, sparse growth, `stormglass_cluster` on exposed tops, temperature 0.7, precipitation on) |
| Weather | **The storm never ends** (3.5): rain and thunder at full strength at all times. Vanilla's own thunder strikes happen as usual. **Ridge lightning** adds real strikes on high ground: every 30–60 seconds, each player standing in `tyrvann_basalt` above y 110 with open sky draws a strike 4–10 blocks away (never closer than 4); stormbacks within 24 blocks draw the strike to themselves instead (12.1). Cosmetic bolts (`setVisualOnly`) strike the Hymnal's arch every 20–40 seconds (6.7) |
| Resources | `stormglass_cluster` (basalt ridge tops; forms where real lightning strikes exposed basalt (half of such strikes, `planets.stormglassChance`); drops 1–2 `stormglass_shard`), mire peat (`peat` block, a fuel: burns 1.5× coal's time) |
| Creatures and foes | the **stormback** (12.1) on the ridges and in the stormwood; Hymnsworn at shrines and verse stones; Clan Hask warriors at the holdfast and on hunts |
| Hub | **The holdfast** (3.3.1) |
| Worldgen structures | `hymn_shrine` (stormwood and mire; spacing 30, separation 10; a small ring of carved stones with a Hymnsworn camp; contract `tyrvann_cleanse`), `mire_grave` (mire; spacing 34, separation 12; a fallen warrior's cairn sunk in the mud with a Mandalorian helmet in a chest; contract `tyrvann_recover`) |
| Runtime sites | three **verse stones** per player (3.3.2); **the Hymnal**, one per server (3.3.3) |

#### 3.3.1 The holdfast

Clan Hask's camp: a ring of prefab shelters and clan-iron walls on stilts above the mire edge, placed at Tyrvann's (0, 0) by `PlanetHubs.ensure`. Template `hub/holdfast`, about 56 by 56 (Milestone 6: now 83 by 83): pad bays 1–4, `HEART` (the hub's centre), `PAD_TERMINAL` (the M4 pad terminal: paid recall and restore), `NPC_DORRUN`, `NPC_VARRA` (Varra stands in both hubs), `VENDOR_ARMOURER`, `VENDOR_SPACER`, `CONTRACT_TERMINAL` (the M4 terminal; the hide covering is part of W3's template), `MEDBAY`, `WARRIOR_1`–`6` (warrior posts), and `HOLDFAST_GATE` (the gate block: shut by default; it opens for a player at Neutral clan standing or higher who uses it, and anyone with them, and shuts behind them, like the Heart's door, 3.3.3; 6.6). The holdfast has no `host`: Tyrvann's row in the host table (3.1) is empty, and Dorrun greets by 6.6's rule. Lightning rods on the roofs keep the storm off the camp: ridge lightning never targets within 48 blocks of the holdfast (Milestone 6: 72, `planets.ridgeHubExclusion`). Its people stand on `hub_spawner` blocks like Saltgate's: roles `dorrun` and `varra` (W4; Varra stands in both hubs, and her lines follow the player's stage), `vendor:hask_armourer`, `vendor:spacer` and `warrior` (W8).

#### 3.3.2 Verse stones

Per player (the M4 `PlanetSites` owned-site pattern): `PlanetSites.Site` appends `VERSE_STONE_1`–`VERSE_STONE_3`. From `VERSES` on, each is placed when the owner comes within 160 blocks of its ring point (the M4 `PlanetSites.ensure` rule; until then the Calling points at the ring point; Milestone 6: far placement places it as soon as it is ensured, and this rule starts only the near search), 250–450 blocks from the holdfast (Milestone 6: 350–650): the first in the stormwood, the second in the mire, the third on the basalt (the ring point is chosen before placement: from the stone's seeded direction, the nearest point at 350 blocks (Milestone 6: 500) whose biome is the stone's, found with `ServerLevel#findClosestBiome3d`, which reads the biome source and loads no chunk; else the seeded point. The Calling points at it from the start). Template `site/verse_stone`: a ring of black stones 11 blocks across around a 5-block monolith (Milestone 6: rebuilt as a court 23 by 23 blocks, the monolith on a stepped plinth inside a broken ring of standing stones), the `verse_stone` block at its heart, one `SPAWNER` anchor, where W3 spawns W8's `hymnsworn` group (12.1) with 3, 4 or 5 members by the stone's index (`planets.verseGuards` [3, 4, 5], 6.6); a group of 5 includes a storm-caller.

The **`verse_stone` block** (W3): a tall, faintly glowing monolith block with a block entity holding `owner`, `index` (1–3), `awake` (true until silenced) and the channel state.

- While awake it hums (a positional loop) and, while any guard spawned for it lives, it pulses the slowing hum (6.6).
- **Silencing:** the owner (or a helper in the owner's party, the M3 party rule) holds the use key on it with no guard alive: a 100-tick channel (`planets.silenceTicks`) with a progress ring on the HUD. Taking damage cancels the channel; a Force power cast at the block during the channel shortens it once by 40 ticks.
- Silenced, it goes dark and calls `QuestHooks.onVerseSilenced(owner, index)`. Another player's verse stone does nothing for them.

#### 3.3.3 The Hymnal

One shared site per server (`PlanetSites.Site.HYMNAL`, the M4 shared-site rule of `HEARTSTONE` and `SEPULCHRE`), eligible from `VERSES`, and placed by the M4 `ensure` rule when a player at `VERSES` or later comes within 160 blocks of its ring point (the Calling points at the ring point meanwhile): on a basalt ridge 600–900 blocks from the holdfast (three ring sweeps, then the clearing fallback, 6.9). Two templates, the M4 two-piece pattern (`PlanetSites.pieces`, `PlanetSites.sink`): `site/hymnal_arch`, the surface piece with the arch over the cleft, which the ring search places; and `site/hymnal`, the halls, sunk under it with its top 18 blocks below the arch's base (`planets.hymnalDepth`), reached by the processional's stair. Both are recorded as `HYMNAL`:

- **The arch:** a stone arch over a cleft in the ridge, struck by cosmetic lightning every 20–40 seconds.
- **The processional:** a descending hall in three landings with Hymnsworn spawners, two storm-caller posts and a pit with a stormback.
- **The Heart:** a round hall 31 blocks across around the dormant **song-engine** (a ring of organ-pipe pillars of basalt and stormglass), sealed by a door (`HEART_DOOR`, a `sealed_plating` door like the M4 Sepulchre's) that opens for a player at `THE_HYMNAL` or later and anyone with them.
- **The engine's undercroft:** a second, lower ring under the Heart's floor, reached by a stair that opens only for the raid (12.3).

Heart anchors (Phase 0 appends the names that are new, 17.1; W8 reads them): `HEART`, `THRESHOLD`, `DAIS`, `LECTERN` (the echo, 12.2), `ROD_1`–`ROD_4`, `PYLON_1`–`PYLON_2`, `RESONATOR_1`–`RESONATOR_4`, `HEART_DOOR`, `UNDERCROFT_DOOR`, `CHOIR_1`–`CHOIR_4`, `SILENCE_1`–`SILENCE_3`, `RING_1`–`RING_3`, `KEYSTONE`, `CANTOR`, `UNDERCROFT_MIN`, `UNDERCROFT_MAX`.

### 3.4 Varkhast addition: the detention post

`PlanetSites.Site` appends `DETENTION_POST` (per owner, for the Return's task, 8.3): an Imperial detention post 120–200 blocks from the Proving: a fenced yard, a guard tower and a cell block with three prisoner cells (`PRISONER_1`–`3` anchors, where W5's escort NPCs spawn) and four guard posts (`GUARD_1`–`4`: two security droids from the M4 `security_droids` spawner group with `count` 2, and two Imperial troopers from the M3 `Troopers.spawnSquad`, 8.3). The warden stands at `WARDEN` (a `hub_host` variant `warden`, W5's dialogue `turn_warden` takes a Mind Trick).

### 3.5 Per-dimension weather

M4's `PlanetWeatherCycleMixin` makes every planet ease toward the overworld's rain. M5 replaces that with a per-planet rule (W3 owns `planet/PlanetWeather` and the mixin):

| World | Rain and thunder | Own weather |
|---|---|---|
| Home world | vanilla | none |
| Selvaren, Varkhast | ease toward the overworld (the M4 behaviour, unchanged; Varkhast's rain still doubles its ash) | none |
| Ghessar | never: both levels held at 0, and every 20 ticks `RAIN_LEVEL_CHANGE` and `THUNDER_LEVEL_CHANGE` at 0 are resent to players in Ghessar (the Tyrvann recipe, with 0) | sandstorms on Ghessar's own cycle |
| Tyrvann | always 1 (the space digest recipe: `advanceWeatherCycle` cancelled for Tyrvann with both levels set to 1; every 20 ticks `RAIN_LEVEL_CHANGE` and `THUNDER_LEVEL_CHANGE` at 1 are resent to players in Tyrvann only) | ridge lightning |
| Orbits | never; the client forces its rain level to 0 there (the overworld's rain packets reach every dimension and would dim the stars; W2's client rule, 5.1) | none |

Sandstorm state (`PlanetWeather.State(kind, untilTick)`, server-global attachment `oldrepublic:planet_weather`) is saved, so a storm survives a restart, and is sent to players in that world on join and change (payload `planet_weather`, 18.2). Side effects of real lightning on Tyrvann stay vanilla (fire on Normal and Hard, which the rain soon puts out), except that skeleton-horse traps never spawn there.

### 3.6 Ambience, music and discovery

- `PlanetAmbience` (client, W3) beds per biome: dune wind and distant sand hiss; salt-flat stillness with ticking heat; canyon echoes and falling grit; stormwood rain on leaves and creaking trunks; mire bubbling and frogs; basalt wind and close thunder. The storm's thunder is vanilla's.
- Music: `music.ghessar`, `music.tyrvann`, `music.space` (orbits), as the M4 planet tracks (environment attribute on the dimension type, the M4 rule). The tracks are placeholders until composed (the asset list in 15.4).
- First landing on each new planet pays the M4 arrival rewards (400 for the planet, 200 for its hub; `chapter_four.planetXp` and `hubXp`, 3.1). The four new worldgen sites pay `planets.siteDiscoveryXp` (150) through `Discovery.xp`, where W4 adds the four `SiteKind` cases. A worldgen site is found only through its structure tag (`StructureLocator.structureTag`, 17.1).

### 3.7 Edge cases

- A data pack that removes Ghessar or Tyrvann: the destination is refused (`UNAVAILABLE`, the M4 rule), and Chapter Five waits at `CALLED` or `BLOCKADE` with a journal line ("The way to Ghessar is closed on this server").
- The M4 rule for hubs that cannot find ground (a raised platform) applies to Saltgate and the holdfast.
- Peaceful: no sand scours or stormbacks; ridge lightning still strikes (it is weather).

---

## 4. Flight

The flight worker (W1) owns everything in this section: the ship classes, free flight and its controls, camera and HUD, landing, the transitions between a planet's sky and its orbit, jumps from orbit, the hand-over to and from the autopilot, the M5 ship upgrades and the shipwright's actions. W1 continues the M4 `travel` package (W3's in M4) except the lead's contract files (17.1). W2 owns ship weapons and damage (5); W1 owns the hull number they reduce, through `Ships.update` (M4).

The brief's ship stages end with "finally, limited space exploration or combat" (`DESIGN_BRIEF.md` 11), and the roadmap entry asks for expanded flight. M5 lets the owner fly their ship by hand. The M4 autopilot stays for every pad-to-pad trip.

### 4.1 The feasibility gate (W1's first deliverable)

Free flight rests on one technique that nothing in the mod uses yet: the pilot's client simulates the ship and the server checks it (the flight digest's recipe A, summarised here). **W1 builds a piloting spike first and proves it on a dedicated server before anything else in this section.**

The technique:

1. While a synced free-flight flag is set (`ShipBody.DATA_PILOTED`), `getControllingPassenger()` returns the owner (derived from the synced owner UUID and the flag, never from passenger order). `isFlyingVehicle()` returns true; the ship keeps `setNoGravity(true)`, so neither floating kick can fire.
2. The ship's `tick()` simulates movement only where `piloted() && isLocalInstanceAuthoritative()` (the pilot's client; the server is authoritative too when nobody pilots, the digest's recipe A step 3): it cancels interpolation, turns the pilot's input into velocity, then `move(MoverType.SELF, velocity)` and sets yaw and pitch. The velocity lives in a ship field, never in `deltaMovement`: the client ignores position and rotation packets for an entity it controls, but applies motion packets (`handleSetEntityMotion` → `lerpMotion`, no authority check), and the server sends one on every velocity change and every `hurtMarked` (the follow-up probe). Everywhere else the client interpolates. The pilot's client then sends `ServerboundMoveVehiclePacket` every tick (vanilla), and the server checks it in `handleMoveVehicle` (vanilla: a too-fast or colliding move is resynced, never kicked; only NaN kicks).
3. On the server a piloted ship is never moved by `Flights` or `Ships.keepOnPad`. When the server must move it (the orbit transitions, a jump's arrival, the orbit edge, a tow), it also sends `ClientboundMoveVehiclePacket.fromEntity(ship)` to the pilot.
4. Input comes from the pilot's `Input` record (forward, backward, left, right, jump, shift, sprint) and look, read at the tail of `LocalPlayer#rideTick` (a W1 client mixin). Boost and the landing request are C2S payloads; weapons are W2's payloads (5.3).
5. Speed stays under the server's tolerance: no ship exceeds 2.0 blocks per tick (`flight.maxSpeedCap`), so five packets bunched into one server tick still pass the "moved too quickly" check.
6. Clicks: while piloting, `Minecraft#startAttack`, `#continueAttack` and `#startUseItem` are cancelled at their head (otherwise the crosshair hits the ship itself, which is pickable from inside), and W2 reads the attack and use keys for weapons (5.3).
7. Shift no longer dismounts: `locksRiders()` is true while piloted, so `PlayerShipRideMixin` vetoes `wantsToStopRiding` and shift means "down".

**The spike passes** when `FlightClientTest` (19.2), on a dedicated server, flies 400 ticks of scripted input (lift-off, climb, a turn at full speed, boost, a dive, a hover and a landing) with: no disconnect; at every 20-tick check the server's ship within 1.5 blocks of the client's; no more than 2 server corrections in the run; the pilot still aboard; and the third-person camera behind the ship.

**If it fails** after two rounds of fixes, W1 switches to **fallback B** and the lead records it in `DECISIONS.md`: the controlling passenger stays null, the server steers from `pilot.getLastClientInput()` and the pilot's look every tick and calls `move(MoverType.SELF, velocity)`, and clients interpolate over 2 steps instead of 3. Every rule below stays the same, with a slower feel: B adds a round trip to every input. Under B, every speed, thrust, brake and climb of 4.4 is multiplied by `flight.serverSteeringSpeedPct` (67).

### 4.2 The helm

- **Who:** the ship's owner, in the pilot seat. Crew never pilot.
- **When:** from Chapter Five's call (`CALLED`, 6.2), when the rebuilt nav computer unlocks the helm (`TravelData` gains `helm`, a boolean, `optionalFieldOf` default false; set by W4's story at `CALLED`, by `/oldrepublic ship helm <player>`, and for every save already past `CALLED`). Before that, the ship screen's Helm button is greyed ("The nav computer still flies this ship").
- **Taking the helm:** in the pilot seat of a ship that is `READY`, `GROUNDED` or `DISABLED` (sublight flight needs no fuel and no minimum hull; only jumps do), the owner presses the **Helm** key (`Y`, 13.1) or the ship screen's **Take the helm** button. The ship runs the M4 Spool (40 ticks: the ramp closes, struts retract), rises 4 blocks under server control (20 ticks), and then hands control to the pilot: state `FLYING` (appended to `ShipState`; `flying()` includes it), the piloted flag set, `QuestHooks.onHelmTaken(owner)` fired. W1 adds `Ships.busy(record)` (an autopilot run, or the state `FLYING`); the M4 checks that refuse actions in flight use it instead of `Flights.isFlying`, except `Ships.tickBody`, which skips both `Flights.isBody` and `keepOnPad` for a piloted body.
- **Riders:** crew aboard ride along and cannot get off until the ship lands (the M4 rule: riders are locked in and take no damage while the ship flies).
- **Refusals:** the M4 refusals that still apply (not the owner, in combat by the M4 `CombatLock` rule) plus `NO_HELM` (not unlocked). `TravelService.Refusal` appends `NO_HELM`, `BLOCKADED` (4.7), `NO_ROOM` (4.6) and `UNDER_FIRE` (4.7).

### 4.3 Controls while piloting

| Input | Effect |
|---|---|
| Mouse | The pilot looks freely; the ship turns toward where the pilot looks, at the ship's turn rate (yaw), and pitches toward the look within ±60°. With `ClientConfig.invertShipPitch` on, the mouse's vertical axis is inverted at the helm (pushing the mouse forward pitches the nose down) |
| `W` | Thrust along the ship's heading (yaw and pitch) |
| `S` | Brake; once stopped, slow reverse (at most 0.2 blocks per tick) |
| `A` / `D` | Strafe left / right (40% of thrust) |
| Jump (`Space`) / Sneak (`Shift`) | Climb / descend (vertical thrusters, world up and down) |
| Sprint (`Ctrl`) | **Boost**: ×1.25 top speed and thrust for 60 ticks; recharges over 160 ticks; the payload `flight_boost` asks the server, which tracks the meter and refuses an empty one |
| `Y` (Helm) | In the air: **land** (4.6) when low and slow over clear ground, else the HUD says why not |
| Inventory (`E`) | The ship screen (Nav, Status, Hold, Crew); the ship keeps flying with no input and coasts to a hover. While piloting, W1's client sends `ship_action` `OPEN_SCREEN` instead of opening the inventory (in its `LocalPlayer#rideTick` mixin); `HasCustomInventoryScreen` is not used, because it would take the inventory key from every rider at all times |
| Left click / right click | W2's cannons / missiles (5.3) |
| `F5` | Vanilla perspective cycle. Taking the helm switches a first-person pilot to third person once (if `ClientConfig.shipCamera` is on); the M4 +8 `CAMERA_DISTANCE` modifier puts the camera behind the ship |

With no input the ship's speed decays (×0.97 per tick in a sky, ×0.99 in orbit) until it hovers in place. A ship that is not piloted (autopilot, parked) never moves on its own.

### 4.4 Flight model (balance section `flight`, W1)

| | Courier | Interceptor |
|---|---|---|
| Top speed in a planet's sky | 1.0 blocks/tick (20 blocks/s) | 1.2 |
| Top speed in orbit | 1.4 | 1.45 |
| Thrust (acceleration) | 0.04 blocks/tick² | 0.07 |
| Brake | 0.08 | 0.12 |
| Climb and descent | 0.4 blocks/tick | 0.5 |
| Turn rate | 6° per tick | 9° per tick |
| Boost | ×1.25 for 60 ticks, 160 ticks to recharge | the same |
| `THRUSTERS` upgrade (4.8) | +10% top speed, +25% boost length | the same |
| Hard cap (server) | 2.0 blocks/tick (`flight.maxSpeedCap`): no combination reaches it (the fastest, an interceptor in orbit boosted with `THRUSTERS`, is 1.45 × 1.10 × 1.25 = 1.99), and the server resyncs (`ClientboundMoveVehiclePacket`) any accepted move longer than `maxSpeedCap` × 1.1 from the one before | 2.0 |

**Collisions.** The ship collides with blocks and with grounded ships (vanilla `move`, the same on both sides; `maxUpStep` 0). Ships in flight pass through each other (`canBeCollidedWith` stays false while flying, as in M4). The server measures the ship's speed as the distance between its last two accepted positions. When its own replay of a move packet (`move(MoverType.PLAYER, …)`, the digest's recipe A step 5) ends in `horizontalCollision`, or in `verticalCollision` while descending, each 0.25 blocks per tick by which that speed exceeds 0.6 costs 2 hull (`flight.impactHull`). A crash can disable the ship (5.4). Riders take no damage.

**Where a ship can fly.** Anywhere in a planet's sky or an orbit, at any height, except through blocks. For flight the home world is a planet like the others: its sky leads up to `orbit_home`. Chunk loading follows the pilot (vanilla). The speeds above keep chunk loading ahead of the ship at the default render distance; at a lower top speed in a sky the ship never outruns its chunks (W1 checks this in the spike).

### 4.5 The autopilot, both ways

- **Pad to pad (M4, unchanged):** the Nav tab's Launch from a pad runs the M4 sequence (Spool, Climb, Jump, Hyperspace, Descent, Settle).
- **From the air:** while piloting, picking a destination on the Nav tab asks "Hand the helm to the autopilot?". On yes, the server takes control (the piloted flag clears; the client hands over by snapping to its interpolated position, the digest's hand-over step) and runs the M4 sequence from the **Jump** phase (W1 starts this run from where the ship is: the flight's origin is the ship's current position and level, the record's position stays the last landing spot for the restart rule, the riders are recorded as at a launch, `ensureHub` runs for a planet whose hub is not placed, and the run enters at `PHASE_JUMP`): the jump costs fuel and wear as in M4, then Hyperspace, Descent and Settle at the destination's pad. A destination on the same planet is a short hop: the same phases, no fuel, no wear.
- **Taking the helm from the autopilot:** during an M4 Descent the owner may press `Y` to take the helm (if unlocked); the landing is abandoned and the ship stays in the air, piloted. The blockade (6.5) uses its own rule: in `BLOCKADE`, an autopilot run to `holdfast` ends after its Hyperspace phase in Tyrvann's orbit, as a jump to orbit (4.7), with the helm handed to the owner and `onOrbitEntered(owner, TYRVANN, true)` fired.
- **Bays.** A ship that lands by hand inside a hub's footprint (32 blocks from its centre; Milestone 6: 88, `flight.hubBayRange`) is guided onto the first free pad bay (the M4 bay rule, with a short server-driven glide); outside, it lands where it is (4.6).

### 4.6 Landing anywhere

The pilot presses `Y` while the ship is at most 6 blocks above the ground (`flight.landHeight`) and slower than 0.25 blocks per tick (the server's measured speed, 4.4). The server checks the spot below:

- the ship's footprint (the M4 `Pads.isClear` box for its class) is free of blocks and other entities' boxes;
- the ground under the footprint is solid, not water or lava, and level within 1 block across;
- it is not inside a hub's footprint (those land on a bay, 4.5) or inside another player's owned site (a `WorldSites` or `PlanetSites` record with another owner, found with `StructureLocator.pieceAt`).

If the spot passes, the ship settles straight down (the M4 Settle: 20 ticks, struts, dust, the ramp lowers) under server control; the record's position becomes the landing spot; the state becomes `READY`, `GROUNDED` (empty tank) or `DISABLED` (hull below 20), as at the M4 Settle; riders are put down beside the ramp, and `QuestHooks.onPlanetArrived` fires for each rider, as at an M4 pad (17.1). If it fails, the HUD says why ("Not level", "No room", "Too fast", "Too high"; refusal `NO_ROOM`).

A field-landed ship stays put across restarts (its record's position is the field spot, and `keepOnPad` holds it there). The M4 recall, tow and restore work from field spots as from pads, and the free daily recall still brings it to a hub pad. On Ghessar the harbourmaster's `c5_harbourmaster` offers `ship:recall_free`; on Tyrvann, which has no host, Dorrun's `c5_dorrun` offers it (W4).

### 4.7 Sky to orbit, orbit to sky, and jumps

**Up.** A piloted ship that climbs above y 300 in a planet's sky (`flight.orbitCeiling`) enters that planet's orbit (5.1). The server holds a chunk ticket at the arrival point (the M4 `TravelTickets` rule), calls `TravelService.moveAcross` to the orbit at `(x / 8, 100, z / 8)` (`flight.orbitScale` 8; the arrival is clamped to 1,920 blocks from (0, 0), `orbitRadius` − `edgeWarning`), keeps the ship's heading, sends the pilot the vehicle position and `flight_motion` with the speed the server measured from the last two accepted move packets, and shows a 30-tick "breaking atmosphere" overlay and sound (W1 client). `QuestHooks.onOrbitEntered(owner, planet, false)` fires.

**Down.** A piloted ship that descends below y 64 in orbit (`flight.atmosphereLine`) enters the planet's sky at `(x × 8, 290, z × 8)`, with the same overlay in reverse (heat shimmer, then cloud). In orbit the planet always lies below (5.1), so diving anywhere reaches it.

**No flip-flop.** After a transition the opposite one is blocked for 60 ticks, and the arrival heights (100 in orbit, 290 in a sky) are well inside each band.

**The orbit's edge.** Orbits are 4,096 blocks across (`flight.orbitRadius` 2,048 from (0, 0); with the ×8 scale that covers a planet's sky out to 16,384). Within 128 blocks of the edge the HUD warns; at the edge the server moves the ship 16 blocks back inside, sends the pilot the vehicle position and `flight_motion` with zero velocity and a yaw toward (0, 0) that also turns the pilot's look, and tells the pilot ("Leaving the system: use the nav to jump").

A jump to a planet's orbit, and a ship's first dive into a planet's sky, call `PlanetHubs.ensure` for that planet (the M4 rule places a hub the first time a ship sets course for it).

**Jumps from orbit.** In orbit the Nav tab lists every unlocked destination twice: **Land at …** (the autopilot from the air, 4.5) and **Jump to … orbit**. A jump to an orbit costs one fuel cell and the M4 wear, runs the M4 Jump and Hyperspace phases under server control from where the ship is (the run of 4.5, with the hyperspace overlay), then moves the ship and its riders into the destination planet's orbit above the destination's pad (the pad's position ÷ 8, height 160; above (0, 0) while the planet's hub is not placed), with no Descent, facing the planet's hub, with the helm back in the pilot's hands; `QuestHooks.onOrbitEntered(owner, planet, true)` fires. Refusals: the M4 ones (fuel, hull below 20, `CombatLock`), `UNDER_FIRE` (hit in the last 200 ticks, W2's `ShipCombat.underFire`), and `BLOCKADED` (6.1).

### 4.8 Ship upgrades (M5)

M5 upgrades are fitted by the **Saltgate shipwright** (dialogue action `ship:fit`, W1), not from the ship screen; each fits once and is kept through a refit. They use free bits of `ShipRecord.upgrades` (the lead adds the constants in Phase 0):

| Upgrade | Bit | Price | Effect |
|---|---|---|---|
| `CANNONS` | `1 << 4` | 800 credits (free in `ARMED`, 6.5) | twin laser cannons and a gunner turret (5.2, 5.3). `Ships.hasWeapons(record)` is true |
| `SHIELD` | `1 << 5` | 1,000 | a 40-point deflector shield that recharges (5.4) |
| `THRUSTERS` | `1 << 6` | 900 | +10% top speed, +25% boost length (4.4) |
| `MISSILES` | `1 << 7` | 1,200 | a concussion missile rack fed from the hold (5.3; cut order 8) |
| `INTERCEPTOR` | `1 << 18` | the refit (4.9) | the ship's class is the interceptor |
| `PART_SPINDLE_CAPACITOR` | `1 << 19` | the item and 500 credits (11.2) | shield +20, recharge ×1.5; needs `SHIELD` (W2) |
| `PART_HYMN_DRIVE` | `1 << 20` | the item and 500 credits (11.2) | jumps cost no hull wear; the Hyperspace phase is half as long (W1) |

The ship screen's Status tab lists the M5 upgrades with their state. The shipwright also repairs hull at the M4 spacer rate and removes an upgrade for nothing (no refund).

### 4.9 Ship classes: the courier and the interceptor (the interceptor and the refit: cut order 9; `ShipBody` stays)

M4's ship is one class (`CourierShuttle`, named in about 15 places). W1 first moves the shared behaviour into an abstract **`ShipBody extends Entity`** (seats, the registry link, the piloted flag, the free-flight tick, `locksRiders`, the hull effects), keeps `CourierShuttle` as a subclass, and changes every use of `CourierShuttle` as a type (parameters, returns, fields and `instanceof`) outside the courier's own class, model, render state and renderer to `ShipBody`.

| | Courier (M4) | Interceptor (new) |
|---|---|---|
| Entity | `oldrepublic:courier_shuttle`, 7 × 4 × 7 | `oldrepublic:interceptor`, 5 × 2.5 × 5 |
| Look | M4 | A narrow wedge with a bubble canopy, two wing-root cannons and an aft gunner's turret; the owner's path paint and hull code |
| Seats | pilot + 2 (3 with `passenger_seat`) | pilot + gunner |
| Hold | 27 (36 with `cargo_bay_extension`) | 9 |
| Hull | 100 | 80 |
| Flight | 4.4 | 4.4 |
| Shield (with `SHIELD`) | 40 | 60 |

**The refit** (shipwright, action `ship:refit interceptor`): 2,500 credits and 6 `alloy_plate`, with the ship landed at Saltgate, empty of riders and with no more than 9 stacks in its hold (the shipwright says how many to unload). The body is replaced (`Ships.restore`; `Ships.spawn` picks the entity type from `CLASS_INTERCEPTOR`, so `restore` needs no class argument), the record keeps its hull code, fuel, upgrades and crew list (trimmed to 1). W1 adds `ShipRules.maxHull(record)` (100, or `flight.interceptorHull` with `CLASS_INTERCEPTOR`) and uses it wherever M4 writes 100; `Ships.update` clamps hull to it (the lead's `ShipRecord` clamp stays 0–100). A refit to the interceptor sets hull to at most 80; a refit back keeps it. **Back to the courier** (`ship:refit courier`): 500 credits; the shipwright kept the old hull. The M4 seat and cargo upgrades do nothing on an interceptor but stay fitted for a refit back.

### 4.10 Persistence and leaving mid-flight

- **The pilot or a rider leaves the server while flying:** the M4 rule (`Flights.riderLeaving`) extends to free flight. The leaving rider is put down beside the ship's last landing spot (pad or field). A rider who leaves the server in another level than that spot is put off and saved where they are, with slow falling (the M4 rule); on their next join, `ShipEvents.joined` moves them beside that spot with a cross-level `teleport(TeleportTransition)` under a `TravelTickets` ticket, with fall distance reset (the spot of their own ship, or of the ship whose crew they are, `TravelData.crewOf`). If the pilot leaves, the whole ship goes back to that spot (the rescue run of 5.4, no cost), and any crew aboard are put down beside it.
- **Server stop while flying:** at the next start, a ship in `FLYING` is placed at its last landing spot (W1 extends `Flights.recover` to records in `FLYING` that have no running autopilot: they are settled as at `Recovery.ORIGIN`, at the record's position, and the body is restored there).
- **The pilot dies** (only `/kill` reaches a rider): the ship makes the rescue run of 5.4 to its last landing spot.
- **Stuck:** the ship screen offers **Return to last landing** in flight, which makes the rescue run of 5.4 with the riders aboard, costs one fuel cell (or the free daily recall when it is unused) and needs no clear path. The M4 recall and tow still refuse a ship in `FLYING`.
- **Below the world:** a ship body never falls out of the world (`onBelowWorld` is overridden to put it back at its last landing spot; 5.1 for orbits).

### 4.11 Camera, HUD and sound (W1 client)

- **Flight HUD** (replaces the M4 phase HUD while piloting; the phase HUD stays for the autopilot): speed and throttle bar, boost meter, hull bar, height (in a sky) or "Orbit: <planet>" (in orbit), the landing hint ("Y: Land" when the spot below passes), the orbit cue near the ceiling ("Climb to reach orbit") and the edge warning in orbit. W2 draws the shield bar and the combat HUD beside it (5.10).
- **Engine sound:** a looping engine hum whose pitch follows speed, a boost roar, thruster puffs on strafes and climbs.
- **Engine trail:** the M4 `engine_exhaust` particle scaled by thrust; a heat shimmer at the atmosphere line.

### 4.12 Edge cases

- A pilot whose client sends a bad move (too fast, through a wall) is resynced by vanilla, never kicked. The flight test counts corrections (4.1).
- A crew member's client never sends vehicle moves (only the controlling passenger's does).
- `allow-flight=false` on a dedicated server must not kick riders (riders are passengers; the ship has no gravity; the M4 test covers it and M5 repeats it with a piloted ship).
- Two ships in flight never collide, and neither takes hull damage from another player's ship (5.4).
- Peaceful: free flight works; no patrols (5.6).

---

## 5. Space and ship combat

The space worker (W2) owns the orbit dimensions and how they look, asteroids, the derelict station, ship weapons, shields and damage, the enemy starfighters, encounters (patrols and the blockade), the space objectives and bounties, and the combat HUD. W1 owns the ship and its flight (4); W2 reads the pilot's state through W1's frozen client accessor `client.travel.Piloting` (17.1) and writes hull through `Ships.update`.

### 5.1 Orbits

Each known system's orbit is its own dimension, one per `Planets.Planet`: `oldrepublic:orbit_home`, `orbit_selvaren`, `orbit_varkhast`, `orbit_ghessar` and `orbit_tyrvann`. An orbit is a flat band of space above its planet: the planet always lies below (4.7), the system's sun and moon hang in a black, starry sky, and asteroid fields drift through the band.

| | |
|---|---|
| Dimension type | `oldrepublic:orbit`, shared by all five (the space digest's recipe): no `skybox` key (so the overworld sky with stars), black `sky_color` and `fog_color`, transparent clouds and sunrise colour, full `star_brightness` (1.0, twice vanilla's night peak of 0.5), fixed sun and moon angles (angles count from the zenith: 0 overhead, 180 straight down; the sun 35° from the zenith in the south-east, the moon low in the west), no time animation (`timelines` names an empty timeline tag of the mod's own, `#oldrepublic:orbit`, the tag form vanilla uses; the `[]` list form is unproven), `has_ceiling` true (no weather), no beds, no respawn anchors, `ambient_light` 0, height -64 to 320. The follow-up probe confirmed the five visual attributes as `minecraft:visual/<name>` keys (as `day.json` writes them); the dark disc needs the eye below the horizon height and out of water, so it never shows above the atmosphere line |
| Generator | void (the digest's option A): `minecraft:noise` with the `oldrepublic:orbit_void` settings (every router entry 0, `final_density` -1, no aquifers, ores or mob generation) and a fixed biome `oldrepublic:orbit_field` (black sky and fog attributes, no precipitation, no spawns, the asteroid feature in step 0) |
| Bands | the atmosphere line at y 64 (below it the ship enters the planet, 4.7), arrivals at y 100 from below and y 160 by jump, the ceiling at 320; 2,048 blocks from (0, 0) in every direction |
| Asteroids | feature `oldrepublic:asteroid` (W2): lumpy clusters 3–12 blocks across of stone, andesite, tuff, basalt and deepslate, between y 80 and 280; density per orbit: home 1 per 3 chunks, Selvaren 1 per 4, Varkhast 1 per 2 (the "Ash Ring", with blackstone), Ghessar 1 per 3 (with salt and sandstone), Tyrvann 1 per 3 (with scorched basalt). Asteroids are cover and obstacles: ships collide with them (4.4); ship weapons never break blocks |
| Music and sound | `music.space`; a faint, low ambient hum; no wind |

**What the pilot sees** (W2 client):

- **The planet below:** a large sphere impostor drawn camera-relative below the ship (the space digest's planet recipe: submitted in `LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES`, placed between the render distance and 0.9 × the frame's far plane, its radius scaled to match, with an unfogged render type). At y 100 its rim sits about 15° below the horizon; at the atmosphere line it fills the view below the horizon. Each planet has its own colour bands and a texture: the home world blue and green with cloud swirls, Selvaren green with silver lakes, Varkhast rust and ash, Ghessar amber with white salt pans, Tyrvann dark green under a storm whose flashes flicker across the disc.
- **Extra bodies:** Ghessar's twin sun beside the vanilla sun (in `orbit_ghessar`, the same disc W3 draws on the surface, 3.2); a faint nebula band across the stars in every orbit (cosmetic, `ClientConfig.spaceSky`). The twin sun here goes with cut 1.
- **No rain:** the client's rain level is held at 0 in orbits (3.5).

**On foot in space.** Players cannot leave a flying ship (4.2). The only places to stand in an orbit are the derelict station's decks (5.7) and the station's landing deck, where a ship can land by hand (4.6). A player who ends up below y -56 in an orbit (a fall from the station, a command) is put back aboard their ship if it is in that orbit with a free seat, else beside it at its last landing spot (the cross-level move of 4.10).

### 5.2 The gunner seat (cut order 2)

On a ship with `CANNONS`, one seat is the **gunner seat**: the courier's seat 1 (the left bench seat, taken by the first crew member to board) and the interceptor's second seat. A crew member there (never the owner, who pilots) controls a **dorsal turret**: it aims where the gunner looks (all the way round, from 10° below the horizon to straight up) and fires with the attack button (5.3). The turret is drawn on the ship's model (W1 adds the part; W2 sends the aim). The gunner's clicks are cancelled the same way as the pilot's (4.1).

### 5.3 Weapons (missiles: cut order 8)

| | Twin laser cannons | Gunner turret | Concussion missiles |
|---|---|---|---|
| Needs | `CANNONS` | `CANNONS` and a gunner | `MISSILES` and `concussion_missile` items in the ship's hold |
| Fired by | the pilot: hold attack | the gunner: hold attack | the pilot: hold use on a target to lock, release to fire |
| Aim | straight ahead along the ship's heading; the two cannons alternate | where the gunner looks | homing on the locked target |
| Rate | 4 shots per second (every 5 ticks) | about 3 per second (every 7 ticks) | one per 20 ticks |
| Projectile | a red (Sith ship: red; Jedi: green-white; Independent: yellow) bolt, speed 4 blocks per tick, range 96 blocks | the same, speed 4 | a missile, speed 1.6 blocks per tick, turning 6° per tick, 100-tick life |
| Damage | 6 per bolt | 5 per bolt | 30, and 10 to ship targets within 3 blocks |
| Heat | +7 per shot; cools 20 per second after 10 ticks without firing; at 100 the cannons lock for 60 ticks and cool to 40 | its own heat, same rules | none |
| Lock | | | the target within 20° of the nose and 80 blocks, held for 30 ticks (rising tone, then a steady one) |

- **Payload:** the client sends `ship_fire(weapon, pressed)` on press and release (C2S, W2); the server fires while the trigger is held, from the ship's server position and heading (the gunner's aim from the server-side player rotation), checks heat, cooldown and ammunition, and ignores a sender who is not the pilot (or the gunner for the turret).
- **Bolts** are `AbstractHurtingProjectile` entities (`ship_bolt`, `ship_missile`; the combat digest's recipe): they sweep their whole move each tick so fast bolts never pass through a target, and `canHitEntity` excludes every entity of the firing ship (its body and its riders) for the bolt's whole life.
- **What they hurt:** only entities in `#oldrepublic:ship_targets` (the starfighters, the gunship and its generator, 5.5). Ship weapons never hurt players, players' ships, other mobs or blocks. A bolt that hits a block stops with sparks.
- **Missiles** are items: `concussion_missile` (the fence sells 4 for 60 credits; an Armstech 3 schematic makes 4 from 2 durasteel, 1 gunpowder and 1 redstone). The rack fires from the ship's hold, one item per missile.

### 5.4 Shields, hull and the disabled ship

- **Player ships** use the M4 hull (0–100, 80 on an interceptor) and, with `SHIELD`, a shield (40 points; 60 on an interceptor). Hits drain the shield first. The shield recharges 4 points per second after 60 ticks without a hit (`space.shieldRecharge`). Shield points live in memory (a ship restarts with a full shield).
- Enemy fire is applied by W2 (`ShipCombat.hitShip`, 17.1): the shield, then `Ships.update(owner, r -> r.withHull(...))`. The M4 invulnerability of the ship body stays: nothing else hurts it, and riders take no damage while it flies.
- **Disabled:** when a flying ship's hull reaches 0 (enemy fire or a crash, 4.4), its controls lock, alarms sound, it tumbles slowly for 40 ticks (`flight.tumbleTicks`) with sparks and smoke, and then **the rescue** comes (W1's `Ships.disable`): the ship flies itself home with everyone aboard: a server-driven run to its last landing spot that keeps its riders as an M4 launch does (the body moves with `TravelService.moveAcross` under a ticket, then the M4 Descent and Settle at that spot). It is always free and not counted against the daily tow; it is not the M4 tow, which refuses a flying or occupied ship and puts riders off at the jump. The ship arrives with 20 hull (`flight.rescueHull`), state `READY` or `GROUNDED`. Riders are put down beside the ramp. Nothing is lost: hold, fuel and upgrades are kept. The encounter that disabled it ends (5.6). The cost is the repair.
- **No PvP in space:** players' ships never damage each other or each other's riders, whatever the server's PvP setting.

### 5.5 Enemy starfighters

Four `Mob`s with no gravity and the Phantom pattern (a move control that steers velocity toward a target point, with circling, attack runs and break-offs), each in `#oldrepublic:ship_targets`. They exist only in orbits. Health is split into a recharging shield (recharges 5 per second after 60 ticks without a hit) and hull (the mob's health).

| Fighter | Entity | Who flies it | Speed | Shield / hull | Weapons | Behaviour |
|---|---|---|---|---|---|---|
| Hymn interceptor | `hymn_interceptor` | Ulvaine's pilots: a black and violet blade-winged fighter with a singing engine | 1.6 | 10 / 20 | twin lasers, 4 per bolt, 3 per second in bursts | pairs; fast attack runs from above, break away at 24 blocks |
| Kajidic raider | `kajidic_raider` | Vessba's cartel and freelance pirates: a squat, patched heavy fighter | 1.2 | 20 / 40 | lasers, 5 per bolt; a missile every 200 ticks (15 damage, a lock warning first) | groups of 2–3; circles and strafes from the side |
| Hask fighter | `hask_fighter` | Clan Hask: a forward-swept Mandalorian fighter in clan colours | 1.4 | 15 / 30 | lasers, 5 per bolt | trios in formation; fights for the player in the blockade when the clan stands with them (6.5); otherwise hostile only to players with clan standing Hostile |
| Blockade gunship | `blockade_gunship` (with `gunship_generator`) | Ulvaine's blockade: a slab-sided gunship 24 blocks long | 0.3 | the generator: 80 hull; the gunship: 400 hull | four turrets, 3 per bolt, 2 per second each, at targets within 64 blocks; launches 2 Hymn interceptors every 30 seconds while the generator stands (at most 6 alive) | holds a slow orbit around its post; while the generator (a separate small entity fixed to its spine) stands, the gunship takes no damage |

- **Kills** pay XP to the shooter (interceptor 60, raider 80, Hask fighter 70, gunship 600; the M1 anti-grind fatigue applies) and credits straight into the shooter's purse (kajidic raider 15, others 10). Nothing drops in space (items would fall forever).
- Fighters fire only while their nose is within 10° of the target (`space.fighterAimCone`), and their bolts spread 2° (`space.fighterSpread`).
- Peaceful: no fighters spawn, except the blockade's scripted pass (6.5).

### 5.6 Encounters

Encounters are in-memory sessions per owner (the M3 and M4 boss-session rule: nothing saved; a restart or the owner leaving the orbit, logging out or being disabled ends it, and its fighters vanish).

**Patrols.** While an owner pilots a ship **with cannons** in an orbit (unarmed couriers are left alone), a patrol may find them: a check every 2,400 ticks of flight, 25% chance (`space.patrolChance`), never within 1,200 ticks of the last encounter. 2–4 fighters arrive 120 blocks away, hail on the comm (a subtitled line) and attack.

| Orbit | Patrol |
|---|---|
| Home, Selvaren, Varkhast | kajidic raiders (pirates) |
| Ghessar | kajidic raiders; for a player at Hostile kajidic standing, a larger group (3–4, `space.hostilePatrolSize`) |
| Tyrvann | Hymn interceptors until the owner's blockade is broken; afterwards Hask fighters if the owner's clan standing is Hostile, else kajidic raiders |

A patrol ends when every fighter is destroyed (300 XP bonus), or when the ship gets more than 200 blocks from all of them, or jumps.

**The blockade** (Chapter Five, 6.5): `SpaceEncounters.startBlockade(owner)` (W2), called when the owner's ship arrives in Tyrvann's orbit during `BLOCKADE` (by jump; an autopilot arrival hands over the helm first, 4.5). Around the arrival: the gunship and its generator 150 blocks ahead; wave 1 (3 Hymn interceptors), then wave 2 (4) when wave 1 is down; the gunship's own launches throughout. If `ChapterFive.clanStands(owner)` (which includes the paid help, 6.5), 3 Hask fighters in the gunship's escort break formation at the start and fight on the player's side. The blockade is broken when the gunship is destroyed: `QuestHooks.onBlockadeBroken(owner)`. A disabled ship (5.4) resets the blockade; the stage stays `BLOCKADE`, and the failure count goes up (6.5). Peaceful: the scripted pass (6.5): the interceptors hail and turn away, the gunship lets the ship through, and `onBlockadeBroken` fires after 200 ticks.

Every point W2 places in an orbit (a patrol's arrival, a bounty's point, a buoy, the blockade) lies within 1,920 blocks of (0, 0).

**Co-op.** Other players' armed ships within 128 blocks join any encounter: their kills count for the owner's encounter and pay the shooter. Only the owner's chapter, contract or bounty moves.

### 5.7 The derelict station (cut order 3)

**The Silent Spindle**, a Republic listening station from the old wars, drifts in the home system's orbit at (420, 150, -380): one shared site per server (`WorldSites` record, `SiteKind.DERELICT_STATION`), placed when a player first comes within 400 blocks: W2 places it with `RuntimeSites.stamp` (no terrain adaptation) after loading the chunks of its box (as `RuntimeSites.loadForSunken` does) and records it in `WorldSites` (W2's template `station/spindle` from a new `gen_structures_space.py`, about 64 × 40 × 48, with anchors `SPINDLE_DECK` (the landing deck), `SPINDLE_POST_1`–`4` (the droid posts), `SPINDLE_LOG` (the log terminal) and `SPINDLE_VAULT` (the vault chest)):

- a **landing deck** on the docking ring, wide enough for one courier or two interceptors (ships land by hand, 4.6);
- airlock corridors, a hydroponics bay gone to black vines, a reactor room with a flickering core, and the command deck;
- **security droids** (the M4 `security_droids` spawner group) on four posts, restored by W2: once per in-game day, when a player comes within 64 blocks, the group spawns at each empty post (`SpawnerGroups.spawn`, bound to the post);
- a **log terminal** on the command deck (W2's block at `SPINDLE_LOG`; using it opens W4's dialogue `spindle_log` through `Dialogues.open`; W4 writes the lines: what the station heard before it went silent; codex `the_spindle`);
- **the vault** (`chests/spindle_vault`, once per player per in-game day, the M4 cache rule): alloy plates, a power core, masterwork fragments, a 10% chance of the *Spindle Capacitor* legendary schematic (11.2).

The station has gravity like any block world; its open hatches face the void (5.1 catches a fall).

### 5.8 Space bounties (cut order 7)

Each board offers **one bounty a day** (contract templates `ghessar_bounty` at Saltgate and `tyrvann_bounty` at the holdfast; W4's data, 7.2; objective `space_clear` with an `ace`): a named ace (`hymn_interceptor` or `kajidic_raider` with doubled shield and hull, a faster weapon and a name from the lang list `bounty.oldrepublic.ace.*`) and 2 wingmen, waiting at a marked point in one of the owner's unlocked orbits (seeded by the day). When W2 reports the `space_clear` done (5.9), W4's mission service pays this in place of the contract formula: 600 XP, 400 credits and +15 standing with the board's faction (`factions_m5.bountyXp`, `bountyCredits`, `bountyRep`), one roll on W2's loot table `gameplay/space_bounty` (2 sunstone or 2 stormglass shards; 5% a hymnstone shard, a number in the table), and the drive schematic roll (11.2, 11.4). One per board per player per day; outside the M3 daily soft cap. The fence's cipher (7.1) rerolls the day's bounty at one board once. Bounties count for the rank 4 deed (8.5).

### 5.9 Space objectives (W2's runtime, `SpaceMissions`)

W4's mission service drives the two appended objective types like any other (7.2): it starts each through `SpaceMissions.start` (17.1), and W2 reports the end through `QuestHooks.onSpaceObjectiveDone(p, missionId, success)`. W2 implements the runtime:

- **`space_run`**: a `sealed_cargo` item is put in the ship's hold; the destination is a **nav buoy** (`nav_buoy`, a blinking marker entity, W2) 600–1,000 blocks away in the same orbit. A patrol along the route tries to **scan** the ship: a patrol fighter within 48 blocks with line of sight for 60 ticks fills the HUD's scan meter (it drains when broken). A completed scan fails the run; destroying or outrunning the patrol stops it. Reaching the buoy with the cargo completes it. For `c5_cargo` the patrol is Clan Hask's: each of their fighters destroyed costs clan standing -10, counted as a hostile kill inside the daily cap (7, the M3 rule).
- **`space_clear`**: destroy the marked group (3–6 fighters, or an ace and wingmen) at a point in an orbit. The point is shown on the HUD from anywhere in that orbit.

### 5.10 Combat HUD (W2 client)

Shown while piloting or gunning, beside W1's flight HUD (4.11): the shield bar; brackets on ship targets within 160 blocks (red hostile, green allied, white neutral) with the hull and shield of the one nearest the crosshair; a lead pip for that target; the heat bar (and the turret's heat for a gunner); the missile count and lock ring with its tones; a red edge pulse and warning tone for an incoming missile; the scan meter; the encounter's comm lines as subtitles (the M4 bark subtitle style).

---

## 6. Chapter Five: "The Long Hymn"

The story worker (W4) owns this chapter. Its state lives in a new `Chapter5` record inside `StoryData` (`optionalFieldOf("chapter5")`; the top-level codec uses 8 of 16 fields, so there is room, and nested records keep every codec under 16 fields). Other workers read it only through the frozen accessor `quest.story.ChapterFive` (17.1): `stage(p)`, `atLeast(p, stage)`, `way(p)`, `clanStands(p)`, `verses(p)` and `ulvaineOutcome(p)`. Other workers report into the chapter through `QuestHooks` (17.1), never by writing `StoryData`. `ChapterFourStage` stays at `COMPLETE`. Stages only move forward, and every reward is protected by a one-time flag or by the stage change (the M3 rule).

### 6.1 Stages (`ChapterFiveStage`, stored by ordinal, append only)

| Stage | Enter when | Objective (journal) | Leaves when |
|---|---|---|---|
| `NOT_STARTED` | default | | Chapter Four `COMPLETE` (old saves on join included) |
| `WAITING` | above | (none shown) | 24,000 ticks after entering, or waking from sleep, at a safe moment (the M4 seeker rule, `SeekerScene.isSafe`: on the home world, not hurt by a mob within `story.crossingSafeTicks`, on the ground or riding, not in lava, not asleep, no hostile within `chapter_four.seekerHostileRange` (24); M5 adds: not riding a ship, and no open dialogue, `Dialogues.session(p) == null`). The offers and confrontations of section 8 use the same rule |
| `CALLED` | the call plays (6.2); the `saltgate` destination and the helm unlock (4.2) | "Find the Buyer's factor at Saltgate, on Ghessar" | the owner arrives on Ghessar (`QuestHooks.onPlanetArrived`: any touchdown, flown or autopiloted, 17.1) |
| `SALTGATE` | arrival | "Get to the factor (choose a way)", listing the three ways and their progress (6.3) | one way is complete |
| `THE_FACTOR` | a way is complete; the counting-house mission starts | "Reach Imrel Dask" | Dask's fate is chosen (6.4) |
| `ARMED` | Dask's message plays; Tyrvann's coordinates are loaded into the nav (the `holdfast` destination unlocks, refused until `BLOCKADE`, 6.5) | "Have the Saltgate shipwright fit cannons" | the ship has cannons (`Ships.hasWeapons`, W1; a ship that already has them passes at once) |
| `BLOCKADE` | above | "Break Ulvaine's blockade over Tyrvann" | the blockade is broken (`QuestHooks.onBlockadeBroken`, W2, 6.5) |
| `TYRVANN` | above | "Land on Tyrvann" | the owner lands anywhere on Tyrvann (`QuestHooks.onPlanetArrived`) |
| `VERSES` | arrival; the owner's three verse stone ring points are set (each stone is placed when the owner comes near, 3.3.2) | "Silence the verse stones (n/3)" | 3 stones silenced (`QuestHooks.onVerseSilenced`, W3) **and** level ≥ 17 (`chapter_five.duelLevel`); below the level the journal says "You are not ready (level 17)" |
| `THE_HYMNAL` | above; the Hymnal's door opens for the owner | "Face Darth Ulvaine in the Hymnal (recommended level 18)" | Ulvaine yields and the owner chooses (6.7) |
| `AFTERMATH` | | "Report to …" | the report is done |
| `COMPLETE` | | the ongoing loop (6.8) | |

Steps that can happen out of order are flags, not stages (the M4 rule). The three ways at Saltgate progress in parallel and the first to finish wins; the others stay open as optional faction missions. Verse stones exist only from `VERSES` on (3.3.2). A ship that already has cannons skips `ARMED` in the same tick. Before the owner's blockade is broken, the owner's own ship reaches Tyrvann only by the jump that starts the battle: the nav refuses Tyrvann before `BLOCKADE` (refusal `BLOCKADED`, 4.7).

### 6.2 The call and the helm

| Path | The call | Rewards |
|---|---|---|
| Jedi | Saelen (`c5_call_saelen`, at the camp): the Watch's contacts in the Outer Rim have seen a Hutt's factor asking after "a frontier Jedi who makes stones sing". "Whoever hired the Mandalorian has not given up. Go and find out why." | 300 XP, the Ghessar coordinates, entry `the_factor_asks` |
| Sith | Sorn (`c5_call_sorn`): "Someone is spending a fortune on you, and spending it through a Hutt. Find out who, and whether they are worth killing." | the same |
| Independent | The Compass hums: Tessaly Arn's echo repeats a docking notice she has no business knowing, a factor's ship berthed at Saltgate under a false flag. "Somebody wants you sung, not killed. I would want to know why." | the same |

Old saves: a player whose Chapter Four ended in M4 with Kaast **spared** already holds the broker lead; their mentor adds one line ("Kaast named a Hutt's factor. Here he is.") and nothing else changes.

**The helm lesson** (optional, shown as sub-objectives under `CALLED`, each paid once):

| Sub-objective | Done when | Reward |
|---|---|---|
| "Take the helm" | the owner flies the ship by hand for the first time (`QuestHooks.onHelmTaken`, W1) | 200 XP, entry `the_helm` |
| "Reach orbit" | the ship enters space from any planet (`QuestHooks.onOrbitEntered`, W1) | 300 XP, codex `space` |
| "Jump from orbit" | a hyperspace jump from orbit completes (`onOrbitEntered` with `byJump` true) | 300 XP |

A player who takes the M4 autopilot to Saltgate skips the lesson; its sub-objectives stay in the journal as hints until done, and the blockade (6.5) teaches flying later anyway.

### 6.3 Saltgate: three ways to the factor

On arrival, the M4 arrival rewards (3.1; W4 does not pay them again): banner "Ghessar" (subtitle "Where the Hutts keep their sand"), 400 XP, entry `saltgate`, codex `ghessar`; near the hub, 200 XP and codex `the_vessba_kajidic`. The **harbourmaster** (a scarred human in the kajidic's colours at the pad bays, `HubRole.HOST`) meets the player at the pad (`c5_harbourmaster`): docking is free for anyone the kajidic is curious about, and the kajidic is very curious about the player.

Clan Hask's first standing is set here, once, from Chapter Four (7). **Varra Hask** waits in the cantina (`c5_varra`); her opening line depends on Kaast's fate.

| Way | Steps (story missions, kind `story`, W4) | Rewards |
|---|---|---|
| **The kajidic's way** | Vessba's majordomo, **Ommo Teel** (`c5_majordomo`), sets two jobs, in either order: **"A Debt in the Canyons"** (`c5_debt`, `recover`: a debtor's strongbox from a camp of desperate salvagers in Ghessar's canyons, `debtor_camp` site (3.2); the debtor (`hub_host` variant `debtor` at `NPC_DEBTOR`, dialogue `c5_debtor`) offers the choice: take the box by force, pay the debt from the player's own credits (200), or let the debtor run and claim the box was empty, which counts only with a Mind Trick or 150 credits of bribe to Teel) and **"The Quiet Cargo"** (`c5_cargo`, a space run: carry a sealed crate from Ghessar's orbit to a buoy at the edge of the system without being scanned by a Clan Hask patrol, `space_run` objective, 5.9). Then **Vessba the Hutt** grants an audience (`c5_vessba`) and sells the factor out: "Dask's coin is good. Mine is better." | each job 1,200 XP, 150 credits, kajidic +60; the audience: kajidic +80, entry `the_kajidic_way` |
| **The clan's way** | Varra Hask wants the factor because the Buyer's coin hired her clan to guard a Sith's temple, a contract the clan's elders took and its warriors despise. **"The Matriarch"** (`c5_matriarch`, `clear`: a **sand scour matriarch** and her brood in the dunes, the clan's test of a hunter). If Kaast died by the player's hand, Varra asks for a **duel of honour** first (`c5_honour`, `spar` against her champion, a Clan Hask warrior, under the M4 spar rules with a real blade: yield at 20%). Then the clan breaks into the counting-house beside the player (the counting-house mission below runs with 3 Clan Hask warriors as allies: W4 spawns them through W8's `hask_warriors` group with the mission's owner as `owner`, and W8 makes a warrior spawned with an owner Allied to the owner's party whatever the clan standing). | matriarch 1,500 XP, 150 credits, clan +80; duel of honour 800 XP, clan +100 (and the clan's grudge for Kaast is settled, 7); entry `the_clans_way` |
| **The Force's way** | At the counting-house door: **Mind Trick** on the doorman (any path; needs `force_mind_trick`), or **Fear** on the door guards (needs `force_fear`), or for a player with neither, a Force Push through the door (the guards then fight). The dialogue `c5_doorman` offers each option the player can use (conditions `ability:knows`). | 600 XP; kajidic -50 (Mind Trick: -25, they never quite work out what happened); entry `the_forces_way` |

**The counting-house** (`c5_counting_house`, `clear`, driven): inside Saltgate's walls, behind the cantina, a two-floor strongroom (part of the Saltgate template, 3.2). Cartel enforcers guard it: none if the kajidic sold Dask out (Vessba's guards stand aside), 6 if the clan's way (and the clan fights with the player), 4 after a Mind Trick (they are elsewhere until the alarm), 8 after Fear or a forced door. At the top, Imrel Dask. 1,500 XP, 150 credits.

### 6.4 The factor

Dask is a tired human accountant in a good coat. He is not a standing hub person: the counting-house mission spawns him at `NPC_DASK` for its owner (`hub_host` variant `dask`, invulnerable, owner-tagged; he talks only to his owner, `c5_dask`) and removes him after the choice. Two owners' missions at once each get their own Dask. He talks without a fight; he has been expecting this since Kaast failed.

- He names the Buyer: **Darth Ulvaine**, "who was a voice on the Dark Council's edges until she started asking for things the Council would not give her".
- He explains the bounty: she wants the player **alive**, because their Resonance makes stones sing, and the thing she has found under Tyrvann's storms needs a singer.
- He plays her last message on his desk projector: a blue hologram of Ulvaine (W8's model, drawn translucent, 15.3) speaks four lines to Dask about "the frontier child who hears the stone" and closes Tyrvann's sky "until the singer arrives". Entry `the_buyers_face`, codex `darth_ulvaine`.
- He hands over Tyrvann's coordinates and the blockade's patrol pattern.

**The choice** (`c5_dask`):

| Choice | Result |
|---|---|
| **Let him go** | He flies out at dawn and is never seen again. Kajidic +40 (he paid his debts to Vessba first), light +3 |
| **Hand him to the clan** | Varra takes him to answer for the clan's dishonour. Clan +80, kajidic -60 (Dask was Vessba's asset). Only offered if the player has met Varra |
| **End him** | Kajidic -100, clan -20 (the clan wanted him alive), dark -5, entry `dask_ended` |

Every choice: 300 XP, entry `the_factor`, flag `c5.dask.<choice>`; the stage moves to `ARMED`.

### 6.5 Arming and the blockade

**Cannons.** The Saltgate **shipwright** (`HubRole.VENDOR`, variant `saltgate_shipwright`, whose dialogue `vendor_saltgate_shipwright` holds his services, 7.1) fits a pair of laser cannons to any ship, free for a player in `ARMED` (the factor's confiscated funds pay; action `c5:fit_cannons`, a one-time flag `c5.cannons_paid`). He fits the story's cannons at any standing; the refusals of section 7 apply only to his paid services. The fitting is the `CANNONS` upgrade (4.8). 200 XP, entry `teeth`. The shipwright also offers the interceptor refit (4.9) and the other combat upgrades at their normal price.

**The blockade** (W2 runs the encounter, 5.6): each time the owner's ship arrives in Tyrvann's orbit during `BLOCKADE` (`onOrbitEntered(owner, TYRVANN, true)`), Ulvaine's blockade is waiting: two waves of **Hymn interceptors** (3, then 4) and a **blockade gunship** that launches more until its shield generator is destroyed.

- If `ChapterFive.clanStands(p)` (the clan's way was taken, clan standing is Trusted or higher when the battle starts, or the player hired the clan's help: flag `c5.clan_hired`), the gunship's escort includes 3 **Clan Hask fighters** that break formation at the start and fight on the player's side: Varra's voice on the comm ("The clan's contract ends today.").
- The blockade is broken when the gunship is destroyed. `QuestHooks.onBlockadeBroken(owner)`: 2,000 XP, 200 credits, entry `the_blockade`, codex `hymn_interceptors`.
- If the player's ship is disabled (5.4), it is rescued to its last landing spot (5.4), the blockade resets, and the stage stays `BLOCKADE`. After 2 failures the journal sends a player without clan support to Varra: her `c5_varra` option "Fly in with the clan" (W4's action `c5:hire_clan`) takes 1,000 credits for fuel and ammunition and sets `c5.clan_hired`.
- Peaceful: the blockade is a scripted pass: the interceptors hail and turn away from "the singer", and the gunship lets the ship through. The stage still moves on.
- Co-op: crew aboard the owner's ship share the fight (the gunner seat, 5.2). Other players' ships in the same orbit can join; their kills count for the owner's encounter and they get kill XP, but only the owner's chapter moves.

Tyrvann's destination (`holdfast`) unlocks at `ARMED` with Dask's coordinates, but the nav refuses it until `BLOCKADE` (refusal `BLOCKADED`). In `BLOCKADE` every route to Tyrvann arrives in orbit and starts the battle: a jump to its orbit, or the autopilot (in `BLOCKADE`, W1 ends an autopilot run to `holdfast` after its Hyperspace phase in Tyrvann's orbit, as a jump to orbit (4.7), hands the helm to the owner and fires `onOrbitEntered(owner, TYRVANN, true)`). After the blockade, Tyrvann is an ordinary destination; its orbit keeps random patrols (5.6).

### 6.6 Tyrvann and the verse stones

On the first landing, the M4 arrival rewards (3.1): banner "Tyrvann" (subtitle "The storm that never ends"), 400 XP, entry `tyrvann`, codex `tyrvann`; near the holdfast, 200 XP and codex `clan_hask`. The holdfast's welcome follows clan standing (7): Trusted or higher, the huntmaster **Dorrun Hask** greets the player at the pad (`c5_dorrun`) and marks the three verse stones; Neutral, the holdfast serves but nobody talks (the stones come from Dask's data instead); Distrusted or lower, the holdfast's gate does not open for the player (3.3.1) and its warriors are Wary (a player can still land on the open pad and leave).

**The verse stones** (3.3, W3's sites and block, W4's story): three per player, placed 250–450 blocks from the holdfast in three directions (one in the stormwood, one in the mire, one on the basalt). Each is a ring of black stone around a tall singing monolith (`verse_stone`, a block that hums and glows faintly when its verse is "awake"). At each:

1. **Hymnsworn** guard it (3 at the first, 4 at the second, 5 with a storm-caller at the third, W8). They try to keep the stone singing: while any guard lives, the stone pulses a slowing hum (Slowness I within 8 blocks every 3 seconds, `planets.humSlowRange` and `planets.humSlowInterval`).
2. With the guards down, the player **silences** the stone: hold the use key on the monolith to channel for 100 ticks (`planets.silenceTicks`); damage taken interrupts it (W3's block, reporting `QuestHooks.onVerseSilenced(p, index)`). A Force power cast at the stone during the channel shortens it by 40 ticks once (the stone "answers" the Force).
3. When the stone falls silent, a **vision** plays (a 6-second screen tint and three subtitled lines, `verse_vision` effect, 15.3): the First Cantor composing the Hymn; the choir of bound singers; the Hymn sung over a city that fell quiet. Entries `verse_one`, `verse_two`, `verse_three`; codex `the_long_hymn` with the second, `the_first_cantor` with the third.

Each stone: 1,500 XP, 100 credits. The third also gives 1 SP (`chapter_five.versesSp`).

### 6.7 The Hymnal, the fight and the choice

**The Hymnal** (3.3) is a shared site, one per server, placed from `VERSES` on (3.3.3): a sunken temple in the basalt ridges, its doorway a cleft under a stone arch where lightning strikes the arch every 20–40 seconds. Inside: a descending processional hall (Hymnsworn, 2 storm-callers, a stormback in a pit), then the **Heart**, a round hall around the dormant song-engine (W3's template and anchors, 3.3.3). The Heart's door opens for a player at `THE_HYMNAL` or later; a helper may enter with them.

**The fight** (12.2, W8). Ulvaine yields at 10%.

**The choice** (`c5_ulvaine_yield`, owner only):

| Choice | Scene | Rewards and costs |
|---|---|---|
| **Spare her** | She drops her saber, says the Hymn will outlast them both, and walks out into the storm. The engine falls silent behind her. | +8 light, entry `ulvaine_spared`. Her saber's crystal (a red crystal, attuned to the player, cosmetic hue, the M1 rule) |
| **Bind her** | Jedi: the Watch takes her to Tython's cells. Sith: the player delivers her to the Dark Council, who reward the hand that brought her. Independent: Clan Hask collects her for the bounty she put on others. | Jedi: +5 light, Jedi Order +100. Sith: Sith Empire +150, the Council's writ (`ulvaines_writ`, a keepsake that counts as a deed for the Darth ceremony, 8.5). Independent: clan +150, 500 credits. Entry `ulvaine_bound` |
| **End her** | A short, unshowy end in the dark of the engine. | -10 dark, the *Cantor's Emitter* legendary schematic (`emitter_cantor_legendary`, 11.2), entry `ulvaine_ended`; Jedi trust -10, Sith trust +10 |

The fight's own reward (5,000 XP, 2 SP, 400 credits, Resonance `ulvaine` +600) is paid once, whatever the choice (`c5.ulvaine_paid`). The Heart stays open afterwards for the raid (12.3) and the daily echo (12.2).

**Report** to the mentor (Jedi: Saelen, `c5_hub_saelen`; Sith: Sorn, `c5_hub_sorn`; Independent: the Compass, from the journal). Lines react to the choice. 1,000 XP, trust +10, Resonance `chapter_five` +400, banner `chapter5`, entry `report_c5`. The rank 4 checklist appears in the Standing panel (8.5).

### 6.8 The ongoing loop

- **The Long Hymn** raid (12.3), weekly for rewards.
- **Ulvaine's echo** in the Heart (12.2), daily.
- **Kajidic contracts** at Saltgate's terminal and **clan contracts** at the holdfast's board (7.2), with the M3 daily soft cap.
- **Space bounties** (5.8), daily, from either board.
- Legendary components (11), crystal tuning (10.4), the second bar (9.1).

### 6.9 Old saves and edge cases

- M4 saves at Chapter Four `COMPLETE` enter `WAITING` on join.
- **A player who never flies by hand** can finish the chapter by the clan's or the Force's way; the kajidic's cargo run and the blockade need the helm. The blockade needs one flight in Tyrvann's orbit: the jump there is the autopilot's, and the battle starts on arrival with the ship under the player's control (the autopilot hands over the helm, 4.5). The battle's HUD teaches the controls.
- If the shared Hymnal cannot be placed (no fitting ridge in 3 ring sweeps), it clears a stand near its ring point (the M4 `PlanetSiteSearch.placeClearing` fallback of `PlanetSites.place`) and logs it.
- If the owner leaves the Heart during the fight, the M4 leave rules hold (12.2).
- Peaceful: the blockade is scripted (6.5); the matriarch hunt and the counting-house are `clear` missions, so they complete on arrival (the M3 rule, `Objectives.resolveOnArrival`); verse stones have no guards and silence on use; Ulvaine talks and yields at once (the M4 rule).
- `flight.enabled` false (14.4): the blockade is the scripted pass, and the kajidic's way is the debt job alone.
- Co-op: a helper in the owner's Ulvaine fight gets 1,200 XP (`ulvaine.helperXp`) and one roll on `gameplay/ulvaine_helper` (W4's loot table: 2 sunstone, 2 stormglass shards or 2 clan iron, equally likely), once per in-game day. W4 pays it on the helper's `onBossDefeated` and records the day in the helper's story counter `c5.helper_day`.
- A player who loses Dask's data (the nav load is not an item) loses nothing: the coordinates live in `TravelData`.
- A player who Falls or Returns mid-chapter (8.3): the chapter continues; mentor lines follow the new path, and the report goes to the new mentor.

### 6.10 Journal (Chapter Five)

Phase 0 appends these ids and the chapter header `chapter_header.5` ("V. The Long Hymn") to `JournalEntries` (append only) and adds `CHAPTER_FIVE_FIRST = THE_FACTOR_ASKS` to `CHAPTER_FIRSTS`. `JournalScreen.buildLeftPage` gains a first branch for Chapter Five at `CALLED` or later (lead).

- **Entries:** `the_factor_asks`, `the_helm`, `saltgate`, `the_kajidic_way`, `the_clans_way`, `the_forces_way`, `the_factor`, `the_buyers_face`, `dask_ended`, `teeth`, `the_blockade`, `tyrvann`, `verse_one`, `verse_two`, `verse_three`, `the_hymnal`, `ulvaine_spared`, `ulvaine_bound`, `ulvaine_ended`, `report_c5`; for section 8: `the_fall_offered`, `a_gift_for_the_empire`, `the_last_lesson`, `kneel`, `the_return_offered`, `amends`, `the_overseers_due`, `welcomed`, `master`, `darth`, `wayfarer`; for section 12: `the_first_cantor_falls`.
- **Codex:** `space`, `ghessar`, `tyrvann`, `the_vessba_kajidic`, `clan_hask`, `darth_ulvaine`, `the_long_hymn`, `the_first_cantor`, `hymn_interceptors`, `hymnsworn`, `sand_scours`, `stormbacks`, `cartel_enforcers`, `starfighters`, `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `mind_trick`, `dual_blades`, `legendary_components`, `the_spindle`.
- **Unlocks not named elsewhere:** `the_hymnal` when the Heart's door first opens for the owner; `starfighters` on the first `onShipKill`; `cartel_enforcers` on the first enforcer killed or Mind-Tricked; `hymnsworn`, `sand_scours` and `stormbacks` on the first kill of one (`onFoeKilled`); each power's page and `mind_trick` from `QuestHooks.onNodeLearned` (W4); `dual_blades` on the first off-hand strike and `legendary_components` on the first legendary schematic learned (W7, through `Journal.unlockCodex`). The verse entries, the visions and the third stone's SP follow the order the stones are silenced, whatever their index.

### 6.11 Dialogue conditions and actions (registered by their owners)

| Kind | Id (argument) | Owner | Meaning |
|---|---|---|---|
| condition | `c5:stage` (stage id), `c5:stage_is` (stage id) | W4 | at least / exactly this stage |
| condition | `c5:way` (`kajidic` / `clan` / `force`) | W4 | that way is complete |
| condition | `c5:kaast` (`spare` / `strip` / `end`) | W4 | Chapter Four's outcome (reads `c4.choice.*`) |
| condition | `c5:yield` | W4 | Ulvaine has yielded to this owner and waits for the choice |
| condition | `ability:knows` (ability id) | W6 | the player knows the power and can afford its base cost now |
| condition | `turn:offered` (`fall` / `return`), `turn:stage` (stage id) | W5 | 8.3 |
| condition | `rank4:ready` | W5 | 8.5 |
| condition | `ship:weapons` | W1 | the ship has cannons |
| action | `c5:way` (`kajidic` / `clan` / `force`), `c5:mission` (mission id), `c5:dask` (`release` / `clan` / `end`), `c5:report`, `c5:fit_cannons`, `c5:cipher`, `c5:buy_shard`, `c5:hire_clan` | W4 | |
| action | `ulvaine:resolve` (`spare` / `bind` / `end`) | W4 | calls `UlvaineFight.resolve` (W8) |
| action | `mind_trick:use`, `fear:use` | W6 | spends the power's cost and starts its cooldown as if cast; the dialogue then moves on |
| action | `turn:accept`, `turn:decline`, `turn:deliver`, `turn:rite`, `turn:resolve` (`release` / `trophy`) | W5 | 8.3 |
| action | `rank4:ceremony`, `rank4:wayfarer` | W5 | 8.5 |
| action | `ship:refit` (`courier` / `interceptor`), `ship:fit` (upgrade id) | W1 | the shipwright's services |

Tier checks use the existing `reputation_min` with a tier name (`{"faction": "clan_hask", "value": "trusted"}`).

**Dialogues added in M5** (W4 unless noted): `c5_call_saelen`, `c5_call_sorn`, `c5_hub_saelen`, `c5_hub_sorn`, `c5_harbourmaster`, `c5_majordomo`, `c5_vessba`, `c5_varra`, `c5_doorman`, `c5_debtor`, `c5_dask`, `c5_dorrun`, `c5_ulvaine_yield`, `spindle_log` (5.7), `vendor_cartel_fence`, `vendor_hask_armourer`, `vendor_saltgate_shipwright` (W4 writes it; W1 owns its `ship:*` actions); W5: `turn_fall_offer`, `turn_return_offer`, `turn_ansa`, `turn_kaddor`, `turn_warden`, `turn_last_lesson`, `turn_overseers_due`, `turn_kneel`, `turn_welcomed`, `rank4_saelen`, `rank4_sorn`, `rank4_compass`. While a player's Fall is at `TASK`, W5 redirects `c4_ansa` and `c4_ansa_meet` to `turn_ansa`, and `c4_kaddor` and `c4_kaddor_meet` to `turn_kaddor` (`DialogueRedirects`). W4's `HubNpcs.onUsed` opens each host's dialogue from a table by variant: `harbourmaster` `c5_harbourmaster`, `teel` `c5_majordomo`, `varra` `c5_varra`, `dorrun` `c5_dorrun`, `doorman` `c5_doorman`, `dask` `c5_dask`, `warden` `turn_warden`. The bartender only barks.

---

## 7. Factions: the Vessba kajidic and Clan Hask

The story worker (W4) owns both factions' standing rules, vendors and contracts. Phase 0 appends the two `Faction` values (17.1); everything else that keys off a faction (the Standing panel rows, vendor refusals, the attitude rule, mission issuers) is made to handle any number of factions in the same sweep, so no worker edits a `switch` over `Faction`.

| | Vessba kajidic | Clan Hask |
|---|---|---|
| `Faction` (appended) | `VESSBA_KAJIDIC`, id `vessba_kajidic`, "the Vessba Kajidic" | `CLAN_HASK`, id `clan_hask`, "Clan Hask" |
| Who | The Hutt crime family that owns Saltgate: **Vessba the Hutt**, her majordomo **Ommo Teel**, her harbourmaster, and the enforcers who keep the port's peace for a price. They broker anything, including the Buyer's contracts | A Mandalorian clan of hunters and pilots, a few dozen warriors under the huntmaster **Dorrun Hask**, camped on Tyrvann. **Torvin Kaast** was one of theirs until he left to hunt alone. Ulvaine's coin hired the clan's elders to guard Tyrvann's sky; **Varra Hask**, Dorrun's sister and the clan's envoy, wants out of the contract |
| `Allegiance` (appended, combat sides) | `KAJIDIC` | `CLAN` |
| Starting standing | 0 on first arrival at Saltgate | Set once on first arrival at Saltgate from Chapter Four: Kaast **spared** +100 (Trusted: "You let our cousin live"); **stripped** 0, and Varra asks for his helmet back (giving her `kaast_helmet`, which she keeps, gives +100, once; keeping it changes nothing); **ended** -150 (Distrusted) until the duel of honour (6.3) settles it (+100) |
| Gains | Saltgate jobs (6.3) +60 each, the audience +80, letting Dask go +40, contracts +25, space bounties on the Saltgate board +15 | the clan's way +80, the duel of honour +100, Dask handed over +80, contracts +25, space bounties on the holdfast board +15, binding Ulvaine as an Independent +150 |
| Losses | the Force's way -50 (-25 with Mind Trick), Dask to the clan -60, Dask ended -100, killing an enforcer (the M3 kill rule: -10 when it counted as hostile, at most 200 a day; otherwise -40 and `KILLED_SOLDIER`, never capped) | Dask ended -20, killing a clan warrior (the same rule), each fighter of the `c5_cargo` patrol destroyed (-10, counted as hostile, inside the cap) |
| Shared gains | none (the M2 Republic/Jedi share stays the only one) | none |

Kills are reported the M3 way. W8's five new foes call `QuestHooks.onFoeKilled` (17.1); for `cartel_enforcer` and `hask_warrior` it carries the faction and `Attitudes.hostileSnapshot(victim, killer)`, and W4 applies `KillReputation.apply`. Starfighter kills change no standing, except the `c5_cargo` patrol's, which W4 reads from `onShipKill`.

**Tiers** use the M2 thresholds unchanged (Hostile -500, Distrusted -100, Trusted 100, Honoured 500). What they gate:

| Tier | Kajidic | Clan |
|---|---|---|
| Hostile | enforcers in Saltgate attack on sight, except at the pad bays and in the medbay; the pads still serve (the harbourmaster lets anyone land) | holdfast warriors attack on sight, except at the pad and in the medbay; the holdfast pad still serves |
| Distrusted | enforcers are Wary (they warn, then fight if the player draws a weapon within 8 blocks); vendors refuse, except the shipwright's free cannons in `ARMED` (6.5) | the holdfast gate is shut; warriors Wary; vendors refuse |
| Neutral | vendors serve; contracts open | vendors serve; contracts open |
| Trusted | enforcers are Allied; the fence's back room (rare stock); the clan's blockade support is unaffected | Dorrun greets the player; the armourer's clan stock; **the clan stands with the player at the blockade** (6.5) |
| Honoured | prices ×0.9 (the M4 rule); the fence's legendary schematic (11.2) | prices ×0.9; the armourer's legendary schematic |

Neither faction refuses a player for being Jedi or Sith; the kajidic serves anyone who pays, and the clan judges deeds, not orders.

### 7.1 Vendors (data in `data/oldrepublic/vendor/`, W4)

| Vendor | Where | Stock (price in credits) |
|---|---|---|
| `cartel_fence` | Saltgate cantina | sunstone ×4 (60), stormglass ×4 (80), repair kit (40), concussion missiles ×4 (60, 5.3), hyperfuel cell (the M4 spacer price); Trusted: the jet pack mod (M4 item, 500); Honoured: the *Storm Cell* legendary schematic (2,000, 11.2). Two services in the fence's dialogue (`vendor_cartel_fence`, W4's actions): the cipher, which rerolls today's space bounty at one board (`c5:cipher`, 50 credits, once a day, 5.8), and at Trusted one hymnstone shard (`c5:buy_shard`, 400 credits, once a week, counter `c5.shard_week`) |
| `saltgate_shipwright` | Saltgate hangar | no stock lines. His dialogue `vendor_saltgate_shipwright` (W4's text, W1's `ship:*` actions) offers the interceptor refit (4.9, 2,500 credits and 6 alloy plates), cannons (800), shield generator (1,000), thrusters (900), missile rack (1,200), hull repair (the M4 spacer rate) and the legendary ship parts (500, 11.1); in `ARMED`, the free cannons (`c5:fit_cannons`, 6.5) |
| `hask_armourer` | Tyrvann holdfast | Mandalorian armour pieces at Standard quality (the M4 set; 300–450), clan iron ×2 (240); Honoured: the *Hask Pommel* and *Clan-Iron Plating* legendary schematics (2,000 each, 11.2) |
| `spacer` (M4, reused stock) | both hubs | fuel and the M4 ship parts, as at the M4 hubs |

Daily limits follow the M4 rule (per stock entry, `daily_limit`; a stock line has no weekly limit, so weekly offers are dialogue services). The vendors are `HubRole.VENDOR` NPCs spawned from the hub templates' anchors (3.2, 3.3); `HubNpcs.onUsed` opens a vendor's `vendor_<variant>` dialogue when it exists, else the stock screen (M4). The fence's stock belongs to `vessba_kajidic` and the armourer's to `clan_hask`, so the M4 refusals below Neutral apply; the shipwright's paid services need Neutral kajidic standing in his dialogue (`reputation_min`), except the story's free cannons; the spacer's stock has no faction (M4).

### 7.2 Contracts (W4; the M3 contract rules and daily soft cap)

| Board | Issuer | Templates (objective) |
|---|---|---|
| Saltgate `contract_terminal` | `vessba_kajidic` | `ghessar_collect` (`clear`: a debtor camp), `ghessar_recover` (`recover`: cargo from a crashed freighter), `ghessar_cull` (`clear`: a sand scour brood), `ghessar_escort` (`escort`: a kajidic factor to a canyon post), `ghessar_run` (`space_run`, 5.9), `ghessar_bounty` (`space_clear` with an ace, one a day, 5.8) |
| Holdfast board (`contract_terminal`, clan skin) | `clan_hask` | `tyrvann_hunt` (`clear`: a stormback), `tyrvann_spar` (`spar`: a clan warrior), `tyrvann_cleanse` (`clear`: a Hymnsworn shrine), `tyrvann_recover` (`recover`: a fallen warrior's helmet from the mire), `tyrvann_patrol` (`space_clear`, 5.9), `tyrvann_bounty` (`space_clear` with an ace, one a day, 5.8) |

Peaceful: the boards offer no `space_clear` contract and no bounty; `space_run` contracts complete at the buoy. Contract XP follows the M3 formula; credits the M4 formula (25 + 5 × level). `MissionDefinition`'s `planet` field accepts any `Planets.Planet` id (the Phase 0 sweep), so `ghessar` and `tyrvann` contracts validate. The two space objective types (`space_run`, `space_clear`) are appended to `MissionDefinition.Objective` in Phase 0; W4's mission service drives them like any other objective and starts each through `SpaceMissions` (17.1); W2 implements the runtime (5.9) and reports the end through `QuestHooks.onSpaceObjectiveDone`.

### 7.3 The Standing panel

The Force screen's Standing panel lists every faction in `Faction.values()` order with its tier and number (the lead makes the list data-driven in Phase 0); the two new rows appear only once the faction has been met (`faction_met:<id>`, an identity choice, set on first arrival at Saltgate for both, since Varra is there too).

---

## 8. Paths and ranks: the Fall, the Return, Master, Darth and Wayfarer

The paths worker (W5) owns this section: both questlines and the rank 4 ceremonies and their rewards. The robes and the mantle are W7's items (17.2); W5 grants them. Questline state lives in a new `Turning` record inside `StoryData` (`optionalFieldOf("turning")`). Other workers read it through the frozen accessor `quest.story.Turnings` (`stage(p)`, `kind(p)`, `done(p, kind)`; 17.1).

### 8.1 Rules that stay

- A path chosen in Chapter Two stays final except through these two questlines. The operator command `/oldrepublic identity path <player> <path> force` keeps its M2 behaviour (it restarts the story path at its first lesson; it is a test and recovery tool, not a game feature).
- Independents keep the M2 late join until Chapter Three's summons, and have no questline (an Independent who wants a master joins one the M2 way, or stays free). Their fourth-rank equivalent is the Wayfarer title (8.5).
- A player can **Fall once and Return once**, in either order, per character (`turn.fall.done`, `turn.return.done` in `IdentityData.choices`). A Jedi who Falls and later Returns is a Jedi for good.

### 8.2 The offer

| | The Fall | The Return |
|---|---|---|
| Who | A **Jedi** with Chapter Four `COMPLETE` | A **Sith** with Chapter Four `COMPLETE` |
| When | alignment ≤ -50 (`turning.fallAlignment`) continuously for one in-game day (the check runs on the slow story tick and stores the day it first saw the band) | alignment ≥ +50 (`turning.returnAlignment`) for one in-game day |
| Who offers | an Imperial acolyte sent by Overseer Sorn arrives near the player at a safe moment (`turn_fall_offer`): "The Overseer says you have stopped pretending. He would like to stop pretending too." | a Watch apprentice from the Greenwatch arrives (`turn_return_offer`): "Master Saelen asked me to find you. She says the door was never locked." |
| Declining | the offer comes back 3 in-game days later (`turning.reofferDays`) while the band holds; a third refusal ends the offers until the alignment leaves the band and comes back | the same |

While a Jedi sits in the dark band their Jedi powers are already refused (the M3 `AbilityCosts` rule); the Fall is the way forward the M3 rule was waiting for.

### 8.3 The steps (`TurningStage`, stored by ordinal, append only: `NONE`, `OFFERED`, `TASK`, `CONFRONTATION`, `RITE`, `COMPLETE`)

**The Fall (Jedi to Sith)**

| Step | Task | Rewards |
|---|---|---|
| `OFFERED` | accept the offer (`turn:accept`) | entry `the_fall_offered` |
| `TASK`: "A Gift for the Empire" | Fly to the Greenwatch on Selvaren. Keeper Ansa Veylin, who trusts any Jedi, lets the player study the **Watch holocron** (`watch_holocron`, a keepsake item). Carry it to the Proving on Varkhast and give it to Overseer Vael Kaddor (dialogue option `turn:deliver`). If the player is struck down or drops it, Ansa takes it back and the step restarts. | 1,500 XP, Sith Empire +100, Jedi Order -150, dark -5, entry `a_gift_for_the_empire` |
| `CONFRONTATION`: "The Last Lesson" | Back on the home world, at a safe moment, **Master Saelen** comes to the player (`StoryMentors.comeTo`, 17.1; the camp stays where it is): she knows. She fights at full strength (the mentor duellist profile with real damage, 12.1) and yields at 10%. The choice (`turn_last_lesson`): **let her go** (she walks back to the old camp; she will not fight again) or **take her crystal** (dark -8; a green crystal attuned to the player, a trophy, cosmetic hue) | 1,500 XP, entry `the_last_lesson` |
| `RITE`: "Kneel" | Sorn comes to the player (`StoryMentors.comeTo` with a new camp) and names them Sith (`turn_kneel`, action `turn:rite`). The path changes (8.4). If Sorn's blade was broken in an earlier Return, he comes anyway, with a new blade and a longer memory. | 1,000 XP, Resonance `the_fall` +300, banner `turning`, entry `kneel` |

**The Return (Sith to Jedi)**

| Step | Task | Rewards |
|---|---|---|
| `OFFERED` | accept the offer | entry `the_return_offered` |
| `TASK`: "Amends" | Three Republic prisoners are held at an Imperial detention post on Varkhast, 120–200 blocks from the Proving (`detention_post` site, 3.4, W3). Free them: defeat the post's four guards (two security droids, the M4 `security_droids` group with `count` 2, and two Imperial troopers from the M3 `Troopers.spawnSquad` with `IncidentMode.ARMY_AND_OWNER` for the owner, so the fight counts as hostile: -10 each, inside the cap, with kill XP), or Mind Trick the warden (`turn_warden`) and walk them out; Peaceful: the warden opens the cells after a talk. Then walk them to the player's ship (W5's own escort runtime, 17.2; not the mission objective). At the ramp they board as the task's passengers, not as riders: W5 removes the three NPCs and records them in `turning.task`. At the next touchdown on a Greenwatch pad bay (`QuestHooks.onPlanetArrived`, any flight or the autopilot), W5 puts them down beside the ramp. If the ship is disabled or recalled first, or the player dies, they are back in their cells and the step restarts. | 1,500 XP, Jedi Order +100, Sith Empire -150, light +5, entry `amends` |
| `CONFRONTATION`: "The Overseer's Due" | On the home world, at a safe moment, **Overseer Sorn** comes to collect what he is owed (`StoryMentors.comeTo`; the camp stays where it is). He fights at full strength and yields at 10%. The choice (`turn_overseers_due`): **let him go**, or **break his blade** (light +3; the M2 "spare" register, and Sorn's saber is gone) | 1,500 XP, entry `the_overseers_due` |
| `RITE`: "Welcomed" | Saelen comes to the player (`StoryMentors.comeTo` with a new camp) and welcomes them into the Watch (`turn_welcomed`). If Saelen's crystal was taken in an earlier Fall, she comes anyway, with a new blade and no grudge she will admit to. | 1,000 XP, Resonance `the_return` +300, banner `turning`, entry `welcomed` |

Peaceful: the confrontation becomes a conversation in which the mentor yields at once (the M4 rule). A death during the confrontation resets it to its start after 1,200 ticks; nothing is lost.

### 8.4 What turning costs

The brief: "Do not let players cheaply swap paths to collect every exclusive benefit." Turning is permanent for the questline, takes days of play, and costs:

| What | Change at the rite |
|---|---|
| Path | the new path. W5 stores the story path first (`StoryData.path`), then calls `Identity.setPath(p, to, true)`, so `StoryService.onPathChanged` (which restarts the lessons for an operator's forced change) sees the story path already matching and does nothing. `setPath` also makes the M2 join changes once per path (+150, -100, the new mentor's trust to 20) and keeps the rank; the rows below run after it and set the final values |
| Rank | `min(rank, 2)` on the new path: after `setPath`, W5 calls `Identity.setRank(p, Math.min(rank, 2))` (`promote` never lowers a rank). A Knight or a Master who Falls is an Apprentice. Rank 3 and 4 are earned again on the new path, with its requirements (8.5). At the rite W5 replaces the story flag `c3.rank3_done` with `c3.rank3_paid` and the choice `rank4.done` with `rank4.paid`, so the new path's Knighting or Naming opens again. A ceremony held again gives the rank, the robe, the rank's granted node, the entry and the banner, but no SP and no XP. The Resonance milestones are one-time already |
| Granted powers | the old path's two signature powers (Heal and Barrier, or Lightning and Choke) and its rank grants (`knights_vigil`, `force_valor`; or `lords_dominion`, `force_rage`) are removed with `SkillService.revoke`. The new path's two signature powers are granted (the M3 `SkillService.grant`, which never refunds). The old tree's other granted nodes (the holocron passives) stay, and work only while the alignment meets their gate, as now |
| Bought nodes | every node in the old path's tree the player bought is removed with `SkillService.revoke`; **half** the points spent on them all together, rounded down, are refunded with `Progression.grantSkillPoints`; the rest is lost |
| Slots | slots 7 and 8 close with rank 4 (9.1); their powers go back to the list |
| Reputation | W5 reads both orders' reputation before `setPath`. After it, the old path's faction ends at `min(before, -500)` (Hostile) and the new path's at `max(before, 0) + 150`, each applied as one `Identity.changeReputation(p, f, end - now, "turn.<fall/return>", false)`, so the toast shows the net change and the join bonus is not added twice |
| Trust | after `setPath`, the new mentor's trust is set to 40 (`turning.newTrust`) and the old mentor's to 0, each by one `Identity.addTrust` of the difference (there is no setter). The old mentor stays at the old camp, cold: using them plays one parting line (`turning.oldrepublic.cold.jedi` or `.sith`) instead of the M2 `not_now`. They give no sessions or lessons, as for any mentor of the other path |
| Robes | rank 3 and 4 robes of the old path keep their armour; their set bonuses already fail on alignment (the M3 gate, `SetBonusRules`) |
| Story | Chapter Two's path stage stays `COMPLETE`; Chapters Three to Five keep their records. Later mentor lines follow the new path |

The Standing panel (W4) shows a "Turned" line under the rank, with the in-game day of the rite (`turning.turned_day`, 14.1).

### 8.5 Rank 4: Master, Darth and Wayfarer

**Requirements** (`TurningRules.rankFour`, shown as a checklist in the Standing panel from Chapter Five `AFTERMATH`, in the style of the M3 rank 3 checklist):

| Requirement | Master (Jedi) / Darth (Sith) | Wayfarer (Independent) |
|---|---|---|
| Story | Chapter Five `COMPLETE` | Chapter Five `COMPLETE` |
| Level | 20 | 20 |
| Rank | 3 on the current path | the Pathfinder title (M3) |
| Mentor trust | 90 | none |
| Reputation | own faction 800 | Trusted (100) with three factions |
| Alignment | +50 or more (Jedi) / -50 or less (Sith) | none |
| Mastery | two powers at mastery rank 3 | two powers at mastery rank 3 |
| Deeds (any 2) | the Long Hymn cleared; Ulvaine's echo defeated 3 times; 5 space bounties; 5 contracts on Ghessar or Tyrvann; Ulvaine's writ held (Sith only, 6.7) | the same, without the writ |

**The ceremony** (dialogue `rank4_saelen`, `rank4_sorn`, or the Compass for a Wayfarer; action `rank4:ceremony` / `rank4:wayfarer`; the identity choice `rank4.done`, and after a turn `rank4.paid`, which stops the SP and XP being paid twice):

| | Master | Darth | Wayfarer |
|---|---|---|---|
| Scene | Saelen speaks for the Council on Tython: the player is a Master of the Order, "and the first thing a Master learns is how little that changes" | Sorn reads a writ from the Dark Council. The player chooses their Darth name from a list of six (lang `rank4.oldrepublic.darth_name.1`–`6`, 18.5, kept as the choice `rank4.darth.<n>`, 14.1; shown as the rank title, "Darth" plus the name) | Tessaly Arn's echo, through the Compass, gives the player her mantle: "The road was always yours." |
| Rank | `Identity.promote(p, 4)` | `Identity.promote(p, 4)` | title `title.wayfarer` (rank stays 0, the M3 Pathfinder rule) |
| Granted power | `force_valor` (9.4) | `force_rage` (9.7) | `wayfarers_stride` (a passive named **Wayfarer's Grace**, since Speed's `speed_wayfarer` is already "Wayfarer's Stride": +10% maximum Force Energy, Force Speed costs 20% less) |
| Robe | `jedi_master_robe` | `sith_darth_robe` | `wayfarer_mantle` |
| Slots | second bar slots 7 and 8 open (9.1) | the same | the same |
| Other | +2 SP, 1,500 XP, Resonance `third_rank` +900, entry `master`, effect `rank_four` (variant 0), banner `rank_four` | the same, entry `darth`, effect `rank_four` (variant 1) | the same, entry `wayfarer`, effect `rank_four` (variant 2) |

W4's Standing panel adds `title.wayfarer` to its titles row and shows a Darth's chosen name after the rank.

**Robes** (W7 makes them, W5 grants them; armour items in the M3 robe style, rendered by `RobeArmorRenderer` with new textures): `jedi_master_robe` and `sith_darth_robe` count as pieces of the `jedi_robes` and `sith_robes` sets (the M3 rank-3 robe rule) with +1 armour over the rank-3 robe and a cosmetic outer layer (a longer cloak on the Master robe; a high-collared mantle on the Darth robe). `wayfarer_mantle` is a chest piece and counts as the `spacer` set's jacket. All three are named, not craftable, not salvageable, and reissued once a day by the mentor or the Compass (counter `rank4.reissue.<item>`) when the player neither carries, wears nor keeps one in their ender chest.

---

## 9. Force powers and the second power bar

The powers worker (W6) owns the six powers, their skill tree nodes, the second bar's runtime and HUD, the three new control kinds, and the mod's first status effects. The lead changes `SkillData.SLOT_COUNT` and adds the control kind constants in Phase 0 (17.1).

The brief lists Stasis, Protect, Valor, mind influence, Drain, Fear, Rage and Crush for later milestones (`DESIGN_BRIEF.md` 6). **Protect** and **Crush** already exist as M4 branches (Barrier's Shelter and the Watch's `knights_vigil`; Choke's `choke_crush`), so M5 adds the other six. Each follows the M3 and M4 power rules: a node in a tree, one input style, a cost and cooldown, two exclusive branches at 1 SP each, mastery ranks at 50, 150 and 400 uses, counterplay, an arm pose, an effect event and sounds.

### 9.1 The second power bar

- **Eight slots.** `SkillData.SLOT_COUNT` goes from 4 to 8 (the codec pads and truncates to it, so old saves gain four empty slots). Slots 1–4 are the first bar (R, Z, V, B, unchanged); slots 5–8 are the **second bar**: hold **Focus** (`Left Alt`, rebindable) and press R, Z, V or B. While Focus is held, R, Z, V and B send only slots 5–8. The bar is chosen when the key goes down: a held HOLD_RELEASE or CHANNEL power keeps its slot until its key is released, whatever Focus does in between.
- **Opening:** the first bar keeps the M3 rule (3 open, the 4th at rank 2 or "No Master"), stored as now in `SkillData.openSlots`. The second bar has its own count, `SkillData.openSlots2` (`open_slots_2`, `optionalFieldOf`, default 0; lead, Phase 0): 2 from level 15 (`skills.secondBarLevel`), so every M4 save at level 15 has slots 5 and 6, and 4 at rank 4 or with the Wayfarer title (8.5). Slot i (0–7) is open when `Skills.isOpen(p, i)` says so: i < `openSlots` for slots 0–3, and i − 4 < `openSlots2` for slots 4–7. `SkillRules.openSlots` keeps its signature; W6 adds `SkillRules.openSlots2(int level, boolean rankFour, SkillBalance b)`, and `SkillService.assignSlot`, `SkillRules.firstFreeSlot`, `Skills.slot` and `AbilityRuntime` test `Skills.isOpen` instead of one count.
- **Payloads:** `AbilityInputPayload` and `AbilityStatePayload` carry slots 0–7 (Force Sense keeps its slot 8).
- **Sound:** pressing Focus plays `force.focus`, a soft click, for the local player only (client-side).
- **HUD:** `ForceHud` draws the second bar's four sockets above the first, dimmed until Focus is held; while it is held the first bar dims and the key labels read "Alt+R" and so on. The skills screen's slot strip shows eight sockets, locked ones with their rule.
- **Controls test:** the key-clash test (`IdentityBuildsClientTest`) gains Focus and the flight keys (13.1).

### 9.2 New control kinds and status effects

`force.ControlEffects` gains three kinds (string constants, lead): **`fear`**, **`stasis`** and **`pacify`**. Each length comes from `ControlEffects.diminished(target, ticks, kind)` alone; it applies the duration modifiers itself, so never call `duration` as well. That is the M2 rule, unchanged: on `#oldrepublic:push_resistant` targets only, the second control of any kind within 200 ticks (`force_push.staggerDrWindowTicks`) lasts half as long, and the third and later do nothing; other targets always get the full length. Bosses (`#c:bosses`) are immune to `fear` and `pacify`. On a boss, `stasis` lasts 25% (a `DurationModifier` W6 registers for the `stasis` kind); it multiplies with the boss's own modifier, so Kaast (×0.6) takes 15%. Ulvaine breaks it after at most 20 ticks.

The runtime follows the combat digest's recipes:

| Kind | On mobs | On players (PvP on) |
|---|---|---|
| `stasis` | status effect `oldrepublic:stasis` plus a head-cancel of `LivingEntity#isImmobile` (true while the effect is on; vanilla's body is only `isDeadOrDying()`, so the cancel hides nothing): `aiStep` then zeroes `xxa`, `zza` and `jumping` and skips `serverAiStep` (the follow-up probe), so no AI and no input, while gravity and knockback still apply. `Player#isImmobile`'s override carries `// API-CHECK`. A flying target also gets `setYya(0)` and no gravity while frozen. Never `setNoAi` | the M3 `Holds` |
| `fear` | status effect `oldrepublic:fear` (icon and timer) plus a priority-0 flee goal on a `PathfinderMob` (`DefaultRandomPos.getPosAway(mob, 16, 7, source)`, run speed ×1.3) and `setTarget(null)` each second. Mobs without goals get Slowness II instead | Slowness II and no sprinting |
| `pacify` | status effect `oldrepublic:pacified`; `setTarget(null)` each tick and a head-cancel of `Mob#setTarget` while it lasts; the mob wanders. Any damage from a player ends it | not applicable |

The two older fears (the M4 `choke_dread` branch in `force/choke/ForceChoke`, and the M2 `dread_presence` passive in `saber/SaberTechniques`, each with its own flee map) move onto the shared `fear` kind, keeping their durations. W6 changes `ForceChoke` and adds `force/fear/ForceFear.apply(LivingEntity target, @Nullable ServerPlayer source, int ticks)`; at W6's request, W7 calls it from `SaberTechniques` and deletes its own map.

**Status effects** (W6's `force/ForceMobEffects`; `force/ForceEffects` stays the M2 class of effect-event ids), registered with `Registry.registerForHolder` on `BuiltInRegistries.MOB_EFFECT`: `stasis`, `fear`, `pacified`, `valor`, `rage`, `drained`. Each has an icon (`textures/mob_effect/<id>.png`, 18 × 18) and lang. Effects are applied with particles off and the icon on (`new MobEffectInstance(holder, ticks, 0, false, false, true)`); the mod's own effects draw them. The icon path and the `Mob#setTarget` cancel carry `// API-CHECK`. Valor and Rage keep their numbers in balance: when the power fires, W6 adds their attribute modifiers to each affected player as transient modifiers (fixed ids; amounts from `force_valor` and `force_rage`, or from the owned branch). A server tick check keeps them in step with the effect: they go when it ends for any reason (expiry, death, milk, `/effect clear`), and come back for a player who logs in with it. The effects themselves carry only the icon, the timer and the name (a `MobEffect`'s own modifiers are fixed when it registers, before `balance.json` is read).

### 9.3 Force Stasis (`force_stasis`, Jedi)

| Field | Value |
|---|---|
| Purpose | Control: take one dangerous enemy out of a fight for a few seconds without hurting it |
| Unlock | Jedi tree, level 15, 2 SP, `taught_by: ["jedi"]` (Saelen's session, the M3 rule) |
| Input | TAP, at the target under the crosshair within 16 blocks (`PullTargeting`, the M2 single-target aim with its aim-assist cone, as Pull and Lightning use it). Can be slotted |
| Cost / cooldown | 30 FE / 400 ticks |
| Effect | `stasis` for 60 ticks: the target hangs motionless in a pale shimmer. 8 or more damage in one hit, or 20 ticks after the first hit, ends it |
| Branches (group `stasis_shape`, level 16, 1 SP) | **Stasis Field** (`stasis_field`): every hostile within 4 blocks of the target is held for 40 ticks. **Stasis Lock** (`stasis_lock`): one target for 100 ticks, not ended by the first two hits |
| Mastery | +1 per target held. Each rank: +5 / +10 / +20 ticks |
| Counterplay | Bosses at 25% (9.2); the M2 diminishing returns on push-resistant targets; `#oldrepublic:force_resistant` mobs (every Hymnsworn, the First Cantor's choir) at half duration |
| Animation | One palm out, fingers spread, held (`PowerAnimations.Kind.STASIS`) |
| VFX / SFX | Rime-like motes settle on the target and hang still; the air around it bends and stops moving (a frozen heat haze); on break the motes fall as grit. `force.stasis.cast` (a glassy chime), `force.stasis.loop` on the target, `force.stasis.break` |

### 9.4 Force Valor (`force_valor`, Jedi Master)

| Field | Value |
|---|---|
| Purpose | A Master's rallying power: a short window of courage for the caster and allies |
| Unlock | Granted at the Master ceremony (8.5); Jedi tree node, `granted_only` |
| Input | TAP. Can be slotted |
| Cost / cooldown | 35 FE / 900 ticks |
| Effect | `valor` for 200 ticks on the caster and, within 8 blocks, the players Shared Light counts as allies (`ForceHeal.isShareTarget`): +20% attack damage, +4 armour, knockback resistance +0.5 |
| Branches (group `valor_shape`, level 20, 1 SP) | **Rallying Light** (`valor_shared`): radius 16, and allies regain 4 health at the start. **Steadfast** (`valor_steadfast`): the caster alone, +30% attack damage, immune to `stagger` for the duration |
| Mastery | +1 per cast that touches an ally or an enemy in combat. Each rank: +20 ticks |
| Counterplay | None beyond its cooldown; it is a Master's reward |
| Animation | Saber hand raised, the other hand open at the chest (`VALOR`) |
| VFX / SFX | A warm gust lifts bright dust around the caster and each ally in uneven puffs, and soft motes rise from them for a moment; `force.valor.cast` (a low swell), `force.valor.end` |

### 9.5 Force Drain (`force_drain`, Sith)

| Field | Value |
|---|---|
| Purpose | Sustain for Sith: take life and Force from a living enemy |
| Unlock | Sith tree, level 15, 2 SP, `taught_by: ["sith"]` |
| Input | CHANNEL, at one living target within 10 blocks in sight. Can be slotted |
| Cost / cooldown | 10 FE to start, +2 FE per pulse; up to 6 pulses (one per 10 ticks); 300 ticks from the end |
| Effect | Each pulse: 2 damage (damage type `force_drain`, in `#oldrepublic:force_harm`), heals the caster 1.5 and gives back 2 FE; the target gets `drained` (Slowness I) while channelled. Ends when the target leaves range or sight, or the caster is staggered |
| Branches (group `drain_shape`, level 16, 1 SP) | **Drain Life** (`drain_life`): heal ×2, damage ×0.75. **Drain Essence** (`drain_essence`): +4 FE per pulse, and each pulse interrupts a Force-using enemy's wind-up (`ForceReactions.interruptWindup` with a new cause, `drain`; bosses decide through their handler). On a player (PvP on), their powers cost double while drained |
| Mastery | +1 per pulse that lands. Each rank: +0.25 damage per pulse |
| Counterplay | `#oldrepublic:droids` and the starfighters have no life to drain (refused); line of sight; stagger |
| Animation | One hand out, fingers curled, drawn slowly back (`DRAIN`) |
| VFX / SFX | A thin stream of dark-red motes drifting from the target to the caster's hand, the target's skin greying slightly while it lasts; `force.drain.loop`, `force.drain.end` |

### 9.6 Force Fear (`force_fear`, Sith)

| Field | Value |
|---|---|
| Purpose | Crowd control for Sith: break a group's nerve |
| Unlock | Sith tree, level 16, 2 SP, `taught_by: ["sith"]` |
| Input | TAP: a 60° cone, 10 blocks. Can be slotted |
| Cost / cooldown | 30 FE / 600 ticks |
| Effect | `fear` for 80 ticks on every hostile in the cone |
| Branches (group `fear_shape`, level 17, 1 SP) | **Terror** (`fear_terror`): all around the caster, radius 8, 60 ticks (the ripple goes out in broken, uneven arcs). **Nightmare** (`fear_dread`): one target for 160 ticks, and it takes +15% damage while afraid |
| Mastery | +1 per enemy frightened. Each rank: +10 ticks |
| Counterplay | Bosses, droids and `#oldrepublic:fearless` (Clan Hask warriors, the First Cantor's choir) are immune; diminishing returns |
| Animation | A cupped hand thrust forward and twisted (`FEAR`) |
| VFX / SFX | A ripple of bent air through the cone, wisps of black smoke curling at each victim's feet, a shiver in their pose; `force.fear.cast` (a whispering rush) |
| Dialogue | `fear:use` (6.3): the counting-house guards stand aside |

### 9.7 Force Rage (`force_rage`, Sith Darth)

| Field | Value |
|---|---|
| Purpose | A Darth's surrender to fury: hit harder and faster, at a price |
| Unlock | Granted at the Darth ceremony (8.5); Sith tree node, `granted_only` |
| Input | TAP. Can be slotted |
| Cost / cooldown | 30 FE / 900 ticks |
| Effect | `rage` for 200 ticks: +30% melee damage, +15% attack speed, +10% movement speed; the caster takes +10% damage and regenerates no Force Energy while it lasts |
| Branches (group `rage_shape`, level 20, 1 SP) | **Bloodthirst** (`rage_unbound`): each saber hit heals 1. **Cold Fury** (`rage_cold`): no damage-taken penalty, but +20% damage instead of +30% |
| Mastery | +1 per hit landed during Rage. Each rank: +20 ticks |
| Counterplay | The damage-taken penalty; it stops Force Energy regeneration |
| Animation | Shoulders hunched, saber arm low: a posture offset added to the idle and walk pose for the effect's length (`RAGE`), never replacing a swing or another power's pose |
| VFX / SFX | A heat haze around the caster, embers trailing the blade, a dark-red edge vignette for the caster only (steady with flashing off); `force.rage.cast` (a growl in the Force), `force.rage.loop` (caster only), `force.rage.end` |

### 9.8 Mind Trick (`force_mind_trick`, Exploration, every path)

| Field | Value |
|---|---|
| Purpose | The brief's mind influence: a weak-minded enemy stops fighting, and some conversations open |
| Unlock | Exploration tree, level 14, 2 SP, any path (no teacher) |
| Input | TAP, at a humanoid under the crosshair within 8 blocks. Can be slotted |
| Cost / cooldown | 25 FE / 600 ticks |
| Effect | In combat: `pacify` for 120 ticks on a target in `#oldrepublic:mind_trickable` (`republic_trooper`, `sith_trooper`, `crystal_poacher`, `poacher_captain`, `cartel_enforcer`: troopers and salvage crews, poachers, the cartel and its wall guards); others refuse it with a line ("His mind is closed to you"). Out of combat: the dialogue action `mind_trick:use` (6.3, 6.11, 8.3) spends the cost and cooldown and moves the conversation on |
| Branches (group `mind_trick_shape`, level 15, 1 SP) | **Confusion** (`mind_trick_confuse`): for 80 ticks the target attacks its nearest ally instead of wandering. While it lasts, the same-side rule does not protect it or its victims: the lead adds a hook to `Allegiances.friendly` that returns false while either entity is confused (W6 supplies the state, Phase 0), and pacify's `setTarget` cancel lets W6's own target through. **Many Minds** (`mind_trick_mass`): up to 3 targets within 6 blocks of the first, 80 ticks |
| Mastery | +1 per successful trick (dialogue uses count). Each rank: +20 ticks |
| Counterplay | Droids, beasts, Force users (acolytes, Hymnsworn, mentors, bosses) and `#oldrepublic:strong_willed` (Clan Hask warriors, Vessba) are immune |
| Animation | Two fingers raised beside the face, a small wave (`MIND_TRICK`) |
| VFX / SFX | A faint ripple of bent air at the target's head, and they blink slowly and lower their weapon; `force.mind_trick.cast` (a soft, low tone) |
| Alignment | None for the power itself; dialogue uses carry their own alignment shifts where the story gives one |

### 9.9 Trees and points

W6 adds the nodes above to `skill_tree/{jedi,sith,exploration}.json` (W6 owns the tree files in M5) and the forms nodes to `saber.json` on W7's request (10.1). New purchasable points: Jedi up to 3 (Stasis) + 3 (Mind Trick) + 1 (a Valor branch) = 7; Sith up to 3 (Drain) + 3 (Fear) + 3 (Mind Trick) + 1 (a Rage branch) = 10; plus the three forms (2 SP each). The M5 supply (section 16) is about 8 points from level 15 to level 20 and 2 more at rank 4, so players choose.

Alignment opposition (the M3 `AbilityCosts` rule) applies to the Jedi and Sith powers as to every power in those trees; Mind Trick is in the Exploration tree and has none.

---

## 10. Lightsabers: forms, dual-wielding, blade length, crystal tuning and hum profiles

The saber and crafting worker (W7) owns this section and section 11. The lead adds the saved fields in Phase 0 (17.1): `SaberConfig.length`, `CrystalAttunement.tuning` and the enum appends.

### 10.1 Three forms

`SaberForm` appends `MAKASHI("form_makashi")`, `DJEM_SO("form_djem_so")` and `JAR_KAI("form_jar_kai")`; `SaberForm.modifiers` gains their cases (an exhaustive switch; Phase 0 writes placeholders and W7 replaces them). W7 extends `SaberForm.Modifiers` (its own file; the record has no codec, so the 16-field rule does not apply) with the fields the new forms need. The form nodes go into `skill_tree/saber.json` (kind `form`, 2 SP; W6's file, written to W7's request). Changing form keeps the M2 rules (20 ticks to settle, refused for 60 ticks after combat).

| Form | Node, level | Effect |
|---|---|---|
| **Makashi** (the duellist) | `form_makashi`, 15 | parry window +2 ticks; a riposte (the M2 `riposte` passive's +40% to the first strike within 20 ticks after a parry; with the passive too, the two do not stack, and Makashi lengthens the window to 30 ticks instead); +15% damage against saber users (any `SaberDuelist`, the M2 interface: the mentors, acolytes, Orren Vasch, the hub's spar partners and Ulvaine); guard drained 20% faster by blaster bolts |
| **Djem So** (power) | `form_djem_so`, 16 | heavy strikes +20% damage and 2 ticks faster to wind up; a parried attacker is staggered for 10 ticks; deflected bolts fly back at their shooter 60% of the time; attack speed -5% |
| **Jar'Kai** (two blades, cut order 6) | `form_jar_kai`, 17 | only while dual-wielding (otherwise it acts as Unformed): off-hand strikes lose their penalty (10.2); attack speed +10%; guard stamina +15; parry window -1 tick |

### 10.2 Dual-wielding

A player with a lightsaber in each hand (two `LightsaberItem`s, never a staff; training sabers count for spars) fights with both:

- **Drawing:** the toggle key (`G`) ignites and retracts both; each blade keeps its own colour, crystal and hum.
- **Striking:** the attack button strikes with the main hand when its cooldown is ready; when the main hand is still recovering and the off hand is ready, it strikes with the off hand instead (the client swings the off hand and sends `offhand_strike(targetId)`, a C2S payload, W7). The server checks the off saber's reach (`Player#isWithinAttackRange(offStack, …)`), its own cooldown ticker, that the blade is lit and the target alive, then deals the off saber's damage through `hurtServer` with the saber damage source, telling `SaberCombat` which hand struck.
- **Penalty:** an off-hand strike deals 70% of the off saber's damage and recovers 20% slower (`saber.offhandDamage`, `saber.offhandSpeed`), unless the form is Jar'Kai.
- **Techniques:** finishers, heavies, lunges and Saber Throw use the main hand only. Guarding with two blades gives +1 parry tick.
- **Animation:** first person uses vanilla's off-hand swing (both hands render already); third person poses the off arm in a low guard while it is not swinging (a tail mixin on `HumanoidModel#setupAnim`, the combat digest's recipe, beside `HumanoidModelSaberMixin`). Alternating swings set the swing fields directly so they can follow each other faster than half a swing.

### 10.3 Blade length (cut order 5)

`SaberConfig` gains `length` (0 standard, 1 short, 2 long; `optionalFieldOf`, default 0). It is set on the workbench's assembly tab with a three-way switch, free, on any single saber (staffs stay standard).

| Length | Reach | Attack speed | Damage | Blade drawn |
|---|---|---|---|---|
| Short (a shoto) | -0.5 | +10% | -10% | ×0.7 |
| Standard | | | | ×1 |
| Long | +0.5 | -10% | +5% | ×1.15 |

Every saber carries an `ATTACK_RANGE` component built from its length and parts. W7 rewrites it whenever the config changes, with creative values as well, so each hand reads its own blade's reach (the combat digest: the component replaces the default range in `isWithinAttackRange`, creative reach included). The `ENTITY_INTERACTION_RANGE` modifier stays for interaction, and `SaberStats` builds it for any non-zero bonus (today only above 0).

### 10.4 Crystal tuning

The lightsaber workbench gains two tabs: **Assemble** (the M4 screen, with the length switch) and **Tune**. A crystal (loose, or either crystal of the saber in the workbench) takes one of five tunings. Tunings change numbers only, never colour (the M1 rule: crystals are cosmetic in colour).

| Tuning | Effect | Material (plus 100 credits) |
|---|---|---|
| Keen (`keen`) | +5% damage | 2 `sunstone` |
| Swift (`swift`) | +6% attack speed | 2 `sunstone` |
| Poised (`steady`) | +1 parry tick (the M3 parry cap of 11 stays) | 2 `stormglass_shard` |
| Warding (`warding`) | +15 guard stamina | 2 `stormglass_shard` |
| Attuned (`resonant`) | Force powers cost 5% less while the saber is lit (a `ForceModifier`) | 1 `hymnstone_shard` |

- The tuning lives on the crystal: `CrystalAttunement` gains `tuning` (a string id, `optionalFieldOf`, default none; the three-argument constructor stays), so it travels with the crystal through disassembly and reassembly.
- Retuning replaces the old tuning; nothing is refunded.
- A staff's two crystals tune separately and both count. An off-hand saber's tuning counts for its own strikes only (and Attuned counts once, whichever saber carries it).
- The tab plays the reserved `workbench.tune` sound (an M4 placeholder until now).

### 10.5 Hum profiles (cut order 4)

Each emitter gets its own ignition, hum and retraction: **Guardian** deep and steady; **Duelist** bright and thin, with a quick ignition; **Reliquary** old and wavering, with a crackle; **Cantor** (11.2) a low hum with a choir-like overtone. Twelve sound events `saber.<emitter>.ignite|hum|retract` (15.4); an emitter without its own profile keeps the M1 sounds. Two lit sabers play two hums: `SaberAudio` keys hums by entity and hand (W7).

---

## 11. Legendary components

W7 owns legendary crafting: the tier, the ten components, their schematics and the bench's handling of them. The lead adds `Quality.LEGENDARY` and widens the quality component in Phase 0.

### 11.1 The fourth tier

- `Quality` appends `LEGENDARY`: weapon ×1.18, armour ×1.40, durability ×1.75 (configurable through `Quality.configure`, the M4 rule). `M4Components.QUALITY` accepts 0–3.
- Legendary is never rolled. A legendary schematic (id suffix `_legendary`, the M4 `_masterwork` convention) always makes a Legendary item: no quality roll, no pity, not commissionable, and salvage refuses it ("Too rare to break down").
- The first legendary item a player crafts shows the `legendary` banner (`lesson_complete` variant 14, sent by W7).
- Rarity `EPIC` (as Masterwork; `Crafting.applyQuality` gives it to both), a gold item name set at craft time (W7) and a "Legendary" tooltip line; a gold frame in the bench's slots.
- Each legendary schematic needs its discipline at level 5. The two ship parts' schematics use the `common` discipline (the M4 ship-part rule): anyone who has learned one makes the part at an engineering bench, and the Saltgate shipwright fits it for 500 credits (`flight.legendaryFitCredits`, W1's `ship:fit`), using up the item.

### 11.2 The ten components

| Component (item id) | Kind | Effect | Discipline | Where the schematic comes from | Materials |
|---|---|---|---|---|---|
| *The Cantor's Emitter* (`emitter_cantor`) | saber emitter (`Emitter.CANTOR`) | +8% damage; the Cantor hum (10.5) | Artifice 5 | ending Ulvaine (6.7), or the Long Hymn (12.3) | 2 hymnstone shards, 2 stormglass shards, 1 masterwork fragment |
| *Hask Pommel* (`pommel_hask`) | saber pommel (`Pommel.HASK`) | +15 guard stamina, +1 parry tick | Artifice 5 | the Hask armourer at Honoured (7.1) | 4 clan iron, 1 hymnstone shard |
| *Sunforged Grip* (`grip_sunforged`) | saber grip (`Grip.SUNFORGED`) | +8% attack speed, +5 guard stamina | Artifice 5 | the Long Hymn | 4 sunstone, 2 alloy plates |
| *Stormglass Lens* (`lens_stormglass`) | saber lens (`SaberLens.Lens.STORMGLASS`) | finishers `shock` the target for 20 ticks; +10% damage against droids | Artifice 5 | the Long Hymn | 4 stormglass shards, 1 hymnstone shard |
| *Clan-Iron Plating* (`mod_clan_iron_plating`) | armour mod (`ARMOUR`) | +3 armour, 10% less blaster damage | Armourcraft 5 | the Hask armourer at Honoured | 4 clan iron, 2 alloy plates |
| *Hymnweave Lining* (`mod_hymnweave_lining`) | armour mod (`ARMOUR`) | +6 maximum Force Energy (`ForceModifier.maxEnergyBonus`), +10% regeneration (`regenMultiplier`) | Armourcraft 5 | the Long Hymn | 2 hymnstone shards, 2 stormglass shards |
| *Storm Cell* (`mod_storm_cell`) | blaster mod (`CELL`) | each hit arcs to one more enemy within 4 blocks for 30% of the damage | Armstech 5 | the cartel fence at Honoured (7.1) | 4 stormglass shards, 1 power core |
| *Sunstone Edge* (`mod_sunstone_edge`) | vibro mod (`EDGE`) | +12% damage; sets the target alight for 40 ticks | Armstech 5 | a crashed freighter's hold on Ghessar (3% a search), or the Long Hymn | 4 sunstone, 1 masterwork fragment |
| *Spindle Capacitor* (`spindle_capacitor`) | ship part (`PART_SPINDLE_CAPACITOR`, `1 << 19`) | shield +20, recharge ×1.5 (needs `SHIELD`) | none (`common`) | the Silent Spindle's vault (5.7) | 4 alloy plates, 2 power cores, 1 hymnstone shard |
| *Hymn-Tuned Drive* (`hymn_tuned_drive`) | ship part (`PART_HYMN_DRIVE`, `1 << 20`) | jumps cost no hull wear; the Hyperspace phase is half as long | none (`common`) | space bounties (2% each), or the Long Hymn | 2 hymnstone shards, 1 hyperdrive motivator, 4 stormglass shards |

The four saber parts are new `HiltParts` values and lens (W7; appended by the lead, 17.1); the mods are new mod items with W7's effects routed through the M4 `Mods` rules (the mod effects are W7's in M5); the ship parts are fitted by the shipwright like the M5 upgrades (4.8; W1's action, W7's items). Their effects are coded by the owners of what they change: W2 the capacitor's shield (5.4), W1 the drive's jumps (4.7).

### 11.3 Materials

| Material | Where |
|---|---|
| `hymnstone_shard` (new) | the Long Hymn (3 per weekly clear), Ulvaine's echo (25%), the fence at Trusted (one a week, 400 credits; a dialogue service, 7.1), space bounties (5%) |
| `sunstone` (new) | Ghessar's canyons (3.2); the fence (60 for 4); the Ulvaine helper's roll (6.9) |
| `stormglass_shard` (new) | Tyrvann's ridges (3.3); charged stormbacks (1–2, 12.1); the fence (80 for 4); the Ulvaine helper's roll (6.9) |
| `clan_iron` (new) | the Hask armourer (2 for 240); Clan Hask warriors (10% a kill); the Ulvaine helper's roll (6.9) |
| `alloy_plate`, `masterwork_fragment`, `power_core`, `hyperdrive_motivator` | M4 |

### 11.4 Guaranteed progress

Bad luck never stops a player for long:

- **The Long Hymn:** each weekly clear without a legendary schematic adds 1 to a pity count (`RaidData.pity`, 14.1); at 2, the next clear always gives one the player does not know.
- **The Spindle's vault:** the capacitor schematic is guaranteed on the 8th vault opened without it.
- **Space bounties:** the drive schematic is guaranteed on the 25th bounty without it.
- **Freighter holds:** the edge schematic is guaranteed on the 30th search without it.

The counters live with the player: the raid's in `RaidData.pity`, the others in `CraftingData` (`legendary_misses`, a map from schematic id to misses, `optionalFieldOf`; lead, Phase 0). W7 owns the rule: `LegendaryDrops.roll(ServerPlayer p, Identifier schematic, float chance, int pity)` returns true when the player learns the schematic now, and never rolls one the player knows. W2 calls it for the vault, W4 for the bounties (5.8) and W3 when a freighter hold is searched (a loot table cannot count misses per player). Phase 0 writes `LegendaryDrops` as a stub that returns false (17.1), so W2 and W3, committed before W7, compile against it.
- **Honoured vendors** sell their schematics outright.

A player who already knows a schematic never rolls it again.

---

## 12. Enemies, bosses and the raid

The enemies worker (W8) owns the new ground enemies and creatures, the mentor duellist used by the Fall and the Return, Darth Ulvaine and her echo, the Long Hymn raid, the shared raid session and the generic boss music service. The starfighters are W2's (5.5). Every new foe follows the M3 and M4 enemy rules: a danger tell before every heavy hit, family Force reactions (`ForceReactions.Handler`), an `Allegiance`, barks with subtitles, and no stat scaling by level (composition and numbers only, the M4 rule).

`Allegiance` appends `KAJIDIC`, `CLAN` (7) and `HYMN` (Ulvaine's cult). New entity tags: `#oldrepublic:fearless`, `#oldrepublic:strong_willed`, `#oldrepublic:mind_trickable`, `#oldrepublic:force_resistant` (9), `#oldrepublic:ship_targets` (5.3; W2 fills it).

### 12.1 Enemies and creatures

| Enemy | Entity (variants) | Where | Health / armour | Fights with | Notes |
|---|---|---|---|---|---|
| **Cartel enforcer** | `cartel_enforcer` (gunman, bruiser, captain) | Saltgate's walls and the counting-house; kajidic posts; contracts; pirate boarding never (space is W2's) | 26 / 4, 34 / 6, 48 / 8 | gunman: blaster rifle from cover (the M1 `PoacherCoverGoal`), 5 per bolt; bruiser: vibrosword, a two-hit string with a shove; captain: blaster pistol and frag grenades, calls the others (`KAJIDIC` squad barks) | Attitude follows kajidic standing (7); hub guards never despawn; mind-trickable; drop credits (the M4 credit drops: W4 adds `cartel_enforcer` and `hask_warrior` to `CreditDrops` at W8's request; hub guards never drop, the M4 hub rule) |
| **Clan Hask warrior** | `hask_warrior` (rifle, blade, champion) | the holdfast's posts, hunts, contracts, the duel of honour (6.3), the counting-house with the clan's way | 40 / 10 (clan iron), 40 / 10, 80 / 12 | the M4 hunter's kit: W8 writes its own `HaskCombatGoal` on the pattern of `HunterCombatGoal` (package-private and bound to `MandalorianHunter`, so not reused), with the hunter's jet hops, the public `HunterRocket`, and the `hunter_tell` and `hunter_attack` events by id. Rifle: 6 per bolt and a wrist rocket with a tell. Blade: vibrosword with a jet-slam (new for this foe, with the M4 `jet_slam` damage type, `HunterDamage.JET_SLAM`, until now only Kaast's). Champion: vibrosword and a clan-iron shield that blocks frontal bolts; every 160 ticks a shield bash (a 15-tick tell: the shield drawn back; then 6 damage, knockback 1.0 and `stagger` for 20 ticks) | Attitude follows clan standing; fearless and strong-willed; drops `clan_iron` (10%) and credits |
| **Hymnsworn** | `hymnsworn` (chanter, blade, storm-caller) | verse stones, Hymn shrines, the Hymnal's processional, Ulvaine's pylons | 24, 28, 36 (no armour) | chanter: a chant that heals allies 1 per second within 8 blocks (a verse stone hums while any of its guards lives, 3.3.2); weak staff blows. Blade: fast vibroblade strikes. Storm-caller: **calls lightning** onto a marked spot (a 40-tick tell: static crawls over the ground in an uneven patch and grit lifts from it, then 8 damage and `shock` 20), and short bursts of Force lightning (a 20-tick burst every 200 ticks, 1.5 damage per 5 ticks, range 12) | Force-resistant, not mind-trickable; `HYMN`; codex `hymnsworn` |
| **Sand scour** | `sand_scour` (scour, matriarch) | Ghessar's dunes and canyons (natural spawn, weight 10; ×2 in sandstorms), broods for contracts and `c5_matriarch` | 30, matriarch 120 | **burrowed** it is hidden in sand (no collision, a faint ripple and hiss within 6 blocks; Force Sense shows it); a player within 4 blocks makes it **burst** out (a 15-tick tell: the sand boils) for a bite of 6 and Slowness II for 40 ticks; it fights for 120 ticks, then burrows again for 30 (takes damage only above ground); flees burrowed below 30%. Matriarch: a tail sweep every 200 ticks (a 20-tick tell: the tail rises and coils), 8 damage in a 4-block arc behind and beside her; 2 brood every 400 ticks | codex `sand_scours`; drops leather 1–2, bone 0–1 |
| **Stormback** | `stormback` | Tyrvann's basalt and stormwood (natural spawn, weight 8), the Hymnal's pit, `tyrvann_hunt` | 80 / 6 | a neutral grazer until provoked (hit, or a player within 6 blocks for 60 ticks); the M4 charge (`HollowhornChargeGoal` pattern) for 10 and knockback. **Charged** when lightning strikes it (ridge lightning seeks it, 3.3): 400 ticks of crackling plates, +50% damage, its charge `shock`s for 20 ticks. Once per fight, below 50%, it roars and draws a bolt onto itself (a 30-tick tell): the bolt charges it at once, and players within 3 blocks take 6 and `shock` 20 | codex `stormbacks`; drops leather 2–4 and, if charged, 1–2 `stormglass_shard` |

**Spawner groups** (the lead opens `SpawnerGroups` to registration in Phase 0; W8 registers): `hymnsworn`, `cartel_enforcers`, `hask_warriors`, `scour_brood`, `stormbacks`. **Danger tells** (the frozen `DangerTellTable` gets its M5 rows in Phase 0, the M4 rule): the tell variants of 15.3, that is `scour` 1 and 4, `hymnsworn` 1, `stormback` 1 and 3, `hask_warrior` 0 and 3, `ulvaine` 1, 4, 6 and 7, and `first_cantor` 1, 3, 6, 9 and 11. The rifle and the wrist rocket reuse the M4 hunter's tell rows; cartel enforcers reuse `npc_windup` and the grenade rows.

**The mentor duellist** (for the Fall and the Return, 8.3): W8 adds the `DuelistProfiles` profile `mentor_true` (damage 9, guard stamina 120, parry 0.35, strings of 3–5) and `boss.mentor.MentorDuel.start(ServerPlayer p, Entity mentor, MentorDuel.Kind kind, Consumer<MentorDuel.Result> onEnd)`: the mentor fights with real damage and their own powers (Saelen: Push and Barrier; Sorn: Lightning and Choke, which holds the player with the M3 `Holds`), under a WHITE boss bar with 10 notches, leashed to 24 blocks around where it began, and yields at 10% (`Result.YIELDED`), or the player dies or leaves for 200 ticks (`Result.RESET`). W5 calls it and handles the choice. The mentor has 300 health for the duel (`mentor_duel.health`); at W8's request, W4 adds `MentorEntity.duel(@Nullable String profileId, float maxHealth)`: while it is set, the duelling player's damage is real and the profile and health apply; clearing it restores both.

### 12.2 Darth Ulvaine

| | |
|---|---|
| Entity | `darth_ulvaine` (humanoid, 0.6 × 1.95), `#c:bosses`. A tall Sith in black and violet with a high collar and a veil of fine chain; a violet-red blade on a curved, Makashi-style hilt |
| Bar | PURPLE, 10 notches, "Darth Ulvaine" |
| Health | 600 × (1 + 0.6 × (n - 1)) for n ≤ 4 players in the Heart (the M3 scaling), with the M4 health-cap trick (`KaastRules.HEALTH_CAP`, `damageScale`) |
| Phases | at 65% and 30%; she yields at 10% (60-tick transitions, the M3 rule) |
| Session | the M4 owner session (`UlvaineFight`, per owner, in memory) with the M3 participant rules. Participants are the owner and up to 3 helpers who are in the Heart at the start or enter during phase 1; damage from anyone else is refused. A dead participant stays on the bar and may come back in. A participant who leaves the Heart alive, changes dimension or disconnects leaves the bar after 100 ticks (`ulvaine.participantLeaveTicks`). The fight resets when no participant has been alive inside for 200 ticks (`ulvaine.resetTicks`); a death alone never resets it. If the owner is dead or away at the yield, she stays kneeling until the owner returns (the M3 rule) |

**Phase 1, The Duel.** A duellist: the M2 `DuelistCombatGoal` with profile `ulvaine` (damage 10, guard 140, parry 0.40, strings of 3–6). Force Lightning (a 40-tick channel, 3 per 10 ticks; Push, a stagger or breaking her line of sight ends it), Force Push, and the **Hymn step** every 300 ticks: a violet shimmer (15-tick tell) and she reappears 6 blocks behind her target.

**Phase 2, The Storm.** She raises her arms and the storm comes into the hall. The four rods (`ROD_1`–`ROD_4`) charge in turn every 160 ticks; a charged rod strikes the ground near a player (a 30-tick tell: the rod hums and static crawls over the target patch; 8 damage and `shock` for 20 ticks within 2.5 blocks). Two Hymnsworn chanters appear at `PYLON_1` and `PYLON_2`; while either lives she takes 20% damage. She keeps duelling between strikes.

**Phase 3, The Hymn.** She sings, and the duel slows (her attacks 20% slower). Every 160 ticks a **Hymn pulse**: a wave of bent air and lifted grit rolls out from her to 16 blocks over 40 ticks, 1.5 blocks tall, with a rising note. The wave is an irregular wall of bent air, grit and dust of varied heights, with gaps (the M3 Surge look), never a continuous ring; the damage does not follow the gaps. Jump it, parry it as it passes (a guard timed within the parry window), or cast Push into it (the caster ignores that pulse); otherwise 6 damage and Slowness I for 40 ticks. At 20% her **Crescendo** begins: four resonators (`hymn_resonator` entities at `RESONATOR_1`–`RESONATOR_4`: crystal spires of 20 health each) light up; if any stands after 400 ticks the whole hall takes 12 damage (Barrier stops it) and all four relight for another 400 ticks; once all four are broken it does not return.

**The yield.** At 10% she kneels, invulnerable. W8 calls `QuestHooks.onBossYielded(owner, "ulvaine")`, and W4 opens `c5_ulvaine_yield`. W4's choice action calls `UlvaineFight.resolve(owner, outcome)` with `SPARED`, `BOUND` or `EXECUTED`. W8 plays the scene and calls `QuestHooks.onBossDefeated(p, "ulvaine", outcome, owner, firstClear)` once per participant (the M3 rule). W4 pays the rewards (6.7) and the helpers' `ulvaine.helperXp`.

**Ulvaine's echo** (daily, after the owner's Chapter Five is `COMPLETE`): using the `LECTERN` in the Heart calls her echo, a translucent replay of the same three phases with no yield (she fades at 0). Once a day per player: 800 XP, a 25% chance of a hymnstone shard; it counts for the rank 4 deed (three echo defeats). W8 pays the echo's rewards, and keeps its once-a-day lockout and count in `RaidData` (`echo_day`, `echoes`, `optionalFieldOf`); `ChapterFive.deeds` reads the count. The echo uses the fight's session rules (above). Peaceful: no echo.

**Counterplay notes.** She is in `#oldrepublic:push_resistant` (displacement ×0.4 and the M2 diminishing returns). Stasis lasts at most 20 ticks on her (9.2), and Fear and Mind Trick fail. Her handler's `chokeTicks` returns at most 20 (`ulvaine.chokeMaxTicks`), and `liftHeight` returns 0. Drain works.

### 12.3 The Long Hymn (the raid)

The song-engine under the Heart holds **the First Cantor**, the Sith who wrote the Hymn, bound into its music. Ulvaine meant to wake him; after her fight anyone strong enough can.

| | |
|---|---|
| Opening | the undercroft door (`UNDERCROFT_DOOR`) opens for a player whose Chapter Five is `COMPLETE`; anyone may come with them |
| Start | a participant uses the engine's keystone (anchor `KEYSTONE`); after a 200-tick countdown (`raid.countdownTicks`) the Hymn begins and the First Cantor rises at `CANTOR`. Everyone inside the undercroft box then (anchors `UNDERCROFT_MIN` and `UNDERCROFT_MAX`), and anyone who enters in the first 600 ticks (`raid.joinTicks`), is a participant, up to `raid.maxPlayers` (8). After that the undercroft door opens only for listed participants (the M3 seal rule), and anyone else's damage is refused |
| Entity | `first_cantor`: a towering spectral Sith (2.6 blocks), floating, robes of light and shadow; `#c:bosses`; RED bar with 12 notches |
| Health | 2,000 × (1 + 0.5 × (n - 1)) for n participants, 1 to 8, with the health-cap trick. Each participant who joins in the first 600 ticks raises the maximum, and the current health keeps its fraction (the M3 rule); after that it is fixed |
| Phases | at 70% and 30% |
| Session | the shared raid session (12.4) |

**Phase 1, The Choir.** Four choir echoes (`cantor_choir`, spectral singers, 60 health each, scaled like the Cantor) sing at `CHOIR_1`–`CHOIR_4`; while any sings, the Cantor is shielded. He sweeps a **verse beam** across the hall (a rotating beam of pale light; for 40 ticks before it moves, dust lifts along its coming path; 8 damage). If he is not below 70% within 900 ticks of the last choir echo falling, the choir returns.

**Phase 2, The Verses.** He fights directly: a slam (10 and knockback, a 20-tick tell), chain lightning (three targets, 5 each). Every 600 ticks he sings one of the three verses the player silenced on Tyrvann:

- **Stormwood:** after a 30-tick tell (`rootsTell`), roots grip everyone in a 6-block circle centred on a random participant for 60 ticks (Slowness IV); an ally's hit on the roots frees a player.
- **Mire:** 3 pools of black fog, 4 blocks across, spread on the floor for 200 ticks (`fogPools`, `fogWidth`, `fogTicks`): 2 damage a second and Slowness I inside.
- **Basalt:** five marked spots, plus one for each participant beyond four (`raid.basaltMarks` 5; static crawling and grit lifting), take lightning after 40 ticks (10 damage).

Hymnsworn shades (Hymnsworn blades, 12.1, drawn translucent) join every 1,200 ticks: 2, plus 1 for every two participants beyond two (`raid.shades` 2; 5 at eight players).

**Phase 3, The Long Hymn.** The engine wakes. The three floor rings (`RING_1`–`RING_3`) sing in turn, each for 100 ticks after a 30-tick tell (`ringTell`, `ringTicks`; 4 damage a second on the singing ring; one ring is always safe), and three silencing points (`SILENCE_1`–`SILENCE_3`) appear: each takes a 60-tick channel, interrupted by damage (the verse stone rule, 3.3.2). Each silenced point opens a 400-tick window in which he takes double damage. He does not yield: at 0 he unravels into song and the engine falls silent until next week.

**Counterplay notes.** The First Cantor and the choir echoes are in `#oldrepublic:push_resistant`. Stasis, Fear and Mind Trick follow 9.2. His handler refuses Choke and Lift (`chokeTicks` and `liftHeight` return 0: he floats and has no body to grip). Drain works on him but not on the echoes. Push staggers an echo; it does not move him.

**Wipe.** If no participant has been alive inside the undercroft for 200 ticks (`raid.wipeResetTicks`), the Hymn stops and resets. A dead participant stays on the bar and may come back in. A participant who leaves alive, changes dimension or disconnects leaves the bar after 100 ticks (`raid.participantLeaveTicks`) and is no longer a participant. Every participant listed at the clear, alive or dead, gets the rewards.

**Rewards** (per participant, once per `raid.weekDays` in-game days: `RaidData.lastClearWeek` holds `Clocks.day(server) / raid.weekDays` at the last rewarded clear): 2,000 XP, 3 hymnstone shards, 400 credits, and a legendary schematic roll (25%, with the 11.4 guarantee). First clear: Resonance `long_hymn` +500, banner `long_hymn` (`lesson_complete` variant 15, sent by W8), the *Cantor's Mask* (`cantors_mask`, a cosmetic head piece with no armour), entry `the_first_cantor_falls`, codex `the_first_cantor` updated, and the rank 4 deed. A participant already cleared this week helps for 300 XP and nothing else. Peaceful: the undercroft stays shut.

### 12.4 The raid session and scaling past four

`boss.raid.RaidSession` (W8): a shared session with many owners. It keeps the participant set, scales health from 1 to 8 players, re-syncs the boss bar to participants once a second (the combat digest: `ServerBossEvent` has no range logic), tracks each participant's reward lockout in `RaidData` (attachment `oldrepublic:raid`: `lastClearWeek`, `pity`, `clears`; W8's, persistent, copied on death), and ends on a clear or a wipe. Nothing else about a running raid is saved: a restart resets it (the M3 rule).

### 12.5 The generic boss music service

`client/boss/music/BossMusicService` (W8): one `MusicManager` mixin and a registry, `register(String bossId, EntityType<?> boss, List<SoundEvent> tracks)`, driven by an S2C payload `boss_music(bossId, track)` sent to a boss's participants. A `track` from 1 to n crossfades to that loop over 40 ticks, and 0 fades out. It keeps the M3 and M4 behaviour: a loop per phase where a boss has them (the Collector's three, Kaast's three), `ClientConfig.bossMusic`, vanilla music held off while a loop plays, and a watchdog that fades the music when no entity of the registered type has been within 64 blocks for 200 ticks. At W8's request the lead switches the senders in `boss/ArenaSession` (the `boss_phase` music variants) and `boss/kaast/KaastSession` (`KaastEffects.MUSIC`) to `boss_music`; in the same change W8 removes `BossMusic`, `MusicManagerMixin`, `KaastMusic` and `MusicManagerKaastMixin`, with the lead's review. Tracks: `music.boss.ulvaine` and `music.boss.cantor`, one each (placeholders until composed).

### 12.6 Boss reporting

`QuestService`'s boss dispatch (today: Kaast to Chapter Four, everything else to Chapter Three, in `onBossPhase`, `onBossYielded` and `onBossDefeated`) becomes a lookup by boss id (lead, Phase 0): `kaast` → Chapter Four, `ulvaine` → Chapter Five (W4), anything else → Chapter Three. The raid and the mentor duels do not use the boss hooks: `RaidSession` pays its own rewards and calls `QuestHooks.onRaidCleared` once per participant, and `MentorDuel` reports only through `onEnd`.

---

## 13. Controls and HUD

### 13.1 New keys (all rebindable, in the mod's key category)

| Key | Default | Owner | What it does |
|---|---|---|---|
| Helm | `Y` | W1 | In the pilot seat on the ground: lift off and take the helm. In the air: land (4.6). In an autopilot descent: take the helm (4.5) |
| Focus | `Left Alt` (hold) | W6 | With R, Z, V or B: the second bar's slots 5–8 (9.1) |

While piloting, the vanilla keys change meaning (4.3): `W`/`S` thrust and brake, `A`/`D` strafe, Jump and Sneak climb and descend, Sprint boosts, attack fires the cannons, use locks and fires missiles (5.3), inventory opens the ship screen. A gunner's attack fires the turret. Off the ship nothing changes, except that a player with two lit sabers strikes with whichever hand is ready (10.2).

The key-clash check (`SkillClientSteps.checkKeyDefaults`, run by `IdentityBuildsClientTest`) compares only the mod's defaults with each other today. M5 extends it to vanilla's defaults (every other `KeyMapping` in `Options.keyMappings`), with Focus and Helm included (lead). That test, not an assumption, decides whether Left Alt and Y are free in 26.2.

### 13.2 HUD and screens

| Element | Owner | Where |
|---|---|---|
| Flight HUD (speed, boost, hull, height or orbit, landing hint, orbit cue, edge warning) | W1 | 4.11 |
| Combat HUD (shield, brackets, lead pip, heat, missiles and lock, missile warning, scan meter, comm lines) | W2 | 5.10 |
| Atmosphere and orbit overlays | W1 | 4.7 |
| Space sky, the planet below, extra suns | W2 (W3 for Ghessar's surface) | 5.1, 3.2 |
| Second power bar | W6 | 9.1 |
| Status effect icons (Stasis, Fear, Pacified, Valor, Rage, Drained) | W6 | 9.2 |
| Verse stone channel ring | W3 | 3.3.2 |
| Verse vision (screen tint and three lines) | W4 | 6.6, 15.3 |
| Boss bars (Ulvaine, the First Cantor, the mentors) | W8 | 12 |
| Ship screen: Helm button, "Return to last landing", the M5 upgrades on Status, jumps on Nav, the star chart with six destinations | W1 | 4 |
| Shipwright services (refit, upgrades, legendary ship parts) | W1, through W4's dialogue | 4.8, 4.9 |
| Workbench: length switch, Tune tab | W7 | 10.3, 10.4 |
| Engineering bench: legendary schematics with a gold frame | W7 | 11.1 |
| Standing panel: the two new factions, the rank 4 checklist (from W5's payload, drawn in the M3 rank 3 style), the "Turned" line | W4 draws, W5 supplies | 7.3, 8.4, 8.5 |
| Journal: Chapter Five, the Turning and rank 4 entries | W4 (W5's entries by request) | 6.10 |

---

## 14. Persistence, multiplayer, dedicated server and config

### 14.1 Saved state

The M2 attachment rules hold (persistent, copied on death, owner-synced, sealed; codecs only add `optionalFieldOf` fields; enums by ordinal, append only; records split before 16 fields).

| Attachment / storage | Owner | Change |
|---|---|---|
| `oldrepublic:story` | lead records, W4 and W5 fill them | `chapter5` (nested: `stage`, `wait_since`, `ways` mask, `way` chosen, `dask`, `blockade_failures`, `verses` mask, `ulvaine` outcome, `boss` state) and `turning` (nested: `kind`, `stage`, `band_since`, `refusals`, `reoffer_day`, `task` progress, `turned_day`); new `c5.*` and `turn.*` flags and counters (the `rank4.*` names are identity choices, below) |
| `oldrepublic:identity` | W4 | two new faction ids in the reputation map; the choices `turn.fall.done`, `turn.return.done`, `rank4.done`, `rank4.paid` and `faction_met:<faction id>`; the Darth name as the choice `rank4.darth.<n>` (n = 1–6, lang `rank4.oldrepublic.darth_name.<n>`) |
| `oldrepublic:skills` | lead (`SLOT_COUNT`, `open_slots_2`), W6 | eight slots (old saves pad to eight); the second bar's open count |
| `oldrepublic:travel` | lead, W1 | `helm` (boolean) |
| `oldrepublic:raid` (new, `RaidData`) | W8 | `last_clear_week`, `pity`, `clears`, `echo_day`, `echoes` |
| `oldrepublic:crafting` | lead | `legendary_misses` (11.4) |
| Global `oldrepublic:ships` | W1 | the M5 upgrade bits and the interceptor bit in the existing mask (no codec change); a field landing is the record's position |
| Global `oldrepublic:planet_weather` (new) | W3 | Ghessar's sandstorm state |
| Global `oldrepublic:planet_sites` | W3 | the new sites (shared `HYMNAL`; owned verse stones, debtor camps and detention posts) |
| Global `oldrepublic:world_sites` | W2, W3 | the derelict station; the new worldgen site kinds |
| Item components | lead | `saber_config.length`; `crystal_attunement.tuning`; `quality` and `saber_lens_quality` up to 3 |
| Block entities | W3 | `verse_stone` (owner, index, awake, channel); `holdfast_gate` (state) |
| Entities | | starfighters, the gunship, encounter fighters, Ulvaine, the First Cantor and the choir: not saved (sessions rebuild them); hub NPCs and wall guards: persistent with hub tags (the M4 rule); the ship body: persistent, as M4 |

### 14.2 Multiplayer

- Each player flies their own ship; crew ride along, and the gunner seat lets a friend fight (5.2).
- Encounters belong to an owner; armed friends join and earn kill XP (5.6). The blockade moves only the owner's chapter.
- Verse stones, the debtor camp and the detention post are per player; Saltgate, the holdfast, the Hymnal and the derelict are shared.
- Ulvaine: the owner and up to 3 helpers (6.9, 12.2). The Long Hymn: up to 8 players, each with their own weekly reward (12.3).
- The Fall and the Return are per player; a mentor's confrontation happens near its player, and a friend can watch but not fight (the M2 duel rule: helpers are pushed out of the duel ring).

### 14.3 Dedicated server

- Client-only classes stay in the client source set (the M1 rule); the M5 mixins on `Minecraft`, `LocalPlayer`, `SkyRenderer`, `ItemInHandRenderer` and `HumanoidModel` are client mixins.
- Free flight is tested on a dedicated server (4.1), with `allow-flight=false`.
- All seven new dimensions load on a dedicated server; the game-test server has none of them (19).

### 14.4 Config

**Server (`balance.json`) new sections:** `flight`, `space`, `chapter_five`, `factions_m5`, `turning`, `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick`, `cartel_enforcer`, `hask_warrior`, `hymnsworn`, `sand_scour`, `stormback`, `ulvaine`, `raid`, `mentor_duel`. **Extended:** `planets` (W3: Ghessar, Tyrvann, weather, verse stones), `skills` (W6: `secondBarLevel`), `saber` (W7: forms, dual-wielding, length, tuning), `crafting` (W7: legendary). `vendors` and `missions` gain no keys (16.4).

| Key | Default | Effect |
|---|---|---|
| `flight.enabled` | true | When false, nobody can take the helm; the M4 autopilot still works. The blockade is then the Peaceful scripted pass (6.5); the majordomo offers only the debt job, and the audience follows it; the boards offer no `space_run`, `space_clear` or bounty |
| `space.patrols` | true | Random patrols in orbit (5.6) |
| `space.pvpShipDamage` | false | Fixed false in M5 (5.4); the key is reserved |
| `planets.ridgeLightning` | true | Real strikes on Tyrvann's ridges (3.3) |
| `planets.sandstorms` | true | Ghessar's sandstorms (3.2) |
| `turning.enabled` | true | When false, the Fall and the Return are never offered |
| `raid.maxPlayers` | 8 | |
| `raid.weekDays` | 7 | In-game days between rewarded clears |

**Client (`ClientConfig`, lead):** `spaceSky` (on: the planet below, extra suns and the nebula; off: black sky and stars only), `invertShipPitch` (off), `stormFlashes` (on; off dims Tyrvann's lightning flashes for players sensitive to flashing, together with the existing `flashingEffects`). Toggled with `/oldrepublicfx space_sky|invert_ship_pitch|storm_flashes on|off`.

**Data** (owner in brackets; section 18 lists the ids): planet and orbit dimensions, types, noise settings, biomes, features and structures (W3 planets, W2 orbits); `data/oldrepublic/vendor/*.json` and `mission/*.json` additions (W4); schematics (W7); skill trees (W6); site anchor sidecars (W2, W3); loot tables (each owner); tags (the lead creates, owners fill); damage types (each owner).

---

## 15. Effects and sounds

Each worker writes the phase-by-phase treatment of its effects (anticipation, activation, travel, impact, aftermath) into its design doc; the lead merges them into `VFX_SFX_SPEC.md` as a "Milestone 5" section. The art direction holds (`VFX_SFX_SPEC.md`): bent air, dust, debris, pressure and light; **no ground circles, sigils, halos, glyphs or glow outlines**; anything that spreads along the floor is irregular dust and debris with gaps; tells are never hidden or thinned by presets (`Vfx.tellCount`); flashing off replaces flicker with steady glows; every loop stops on cancel, death, disconnect, dimension change, leaving the area, and item or slot change.

Two M5 additions to the rules:

- **Space has no dust.** In an orbit the vocabulary is sparks, hull fragments, engine light, smoke that thins fast, and bent space (a shield ripple is the air-ripple language applied to a hull). Nothing in orbit kicks up ground dust. Space is not silent: the mod plays weapon, hit and explosion sounds in orbit for readability (the films' convention).
- **Storm flashes.** Tyrvann's lightning, Ulvaine's storm phase and the Hymnsworn storm-callers flash the screen only through vanilla's own lightning flash; `ClientConfig.stormFlashes` off (or `flashingEffects` off) dims it to a steady brightening (14.4).

### 15.1 Particle budgets per active instance at Standard

| Effect | Budget |
|---|---|
| Free flight engine trail | the M4 `engine_exhaust`, 2 per tick at a hover to 8 per tick at full thrust; boost 12 per tick for its first 10 ticks |
| Thruster puffs (strafe, climb, descend) | 2 per tick while held |
| Atmosphere transition | screen-space streaks, not particles (the M4 hyperspace overlay rule: 40 at Standard, 15 at Reduced), plus 30 `atmosphere_streak` around the hull over 30 ticks |
| Ship impact on terrain | 10 `spark_shower` and 10 block-dust particles of the struck block |
| Ship bolts (cannons, turret, fighters) | the M1 bolt renderer, no trail particles; 2 muzzle sparks per shot |
| Bolt on a shield | 1 `air_ripple` spreading over the hit side and 3 sparks |
| Bolt on a hull | 8 `spark_shower` and 2 `hull_debris` |
| Missile | 1 `grenade_smoke` every 2 ticks of flight |
| A fighter destroyed | 24 `hull_debris`, 12 `spark_shower` and one light flash (steady with flashing off) |
| The gunship destroyed | three fighter-sized bursts along its spine over 40 ticks, then 60 `hull_debris` |
| A disabled player ship | 1 `spark_shower` per 4 ticks and 1 `grenade_smoke` per 2 ticks for its 40-tick tumble |
| Sandstorm (ambient, client) | 60 visible `sand_gust` around the camera at Standard |
| Verse stone awake | 2 `verse_mote` per second; the slowing hum: one `air_ripple` outward; silenced: 30 motes falling as grit over 20 ticks |
| Stormglass forming | 6 `lightning_spark` on the struck basalt |
| Force Stasis | 16 `stasis_rime` held around the target; on break the same 16 fall |
| Force Valor | 20 `valor_dust` per player at the cast; 1 per 10 ticks per affected player while it lasts |
| Force Drain | 3 `drain_mote` per tick along the stream |
| Force Fear | one `air_ripple` sweep through the cone; 6 `shadow_wisp` per victim at the cast, then 1 per 10 ticks while afraid |
| Force Rage | 1 `flare_ember` per tick along the lit blade; heat haze as 1 `air_ripple` per 5 ticks |
| Mind Trick | 1 `air_ripple` at the target's head |
| Storm-call tell (Hymnsworn, Ulvaine's rods, the Cantor's basalt verse) | 20 `lightning_spark` crawling over an uneven patch and 10 `gather_dust` lifting from it over the tell (a tell: never thinned) |
| Sand scour burst tell | 15 `sand_burst` boiling up over 15 ticks (a tell); the burst itself 30 |
| Stormback charged | 1 `lightning_spark` per 4 ticks over its plates |
| Ulvaine's Hymn pulse | 48 `gather_dust` along the wave front every 4 ticks (a tell) |
| Ulvaine's Hymn step tell | 12 `gather_dust` and one `air_ripple` over 15 ticks (a tell) |
| Ulvaine's Crescendo | 6 `light_mote` rising from each lit resonator per second (a tell while any stands) |
| The Cantor's slam tell | 16 `gather_dust` lifting under him over 20 ticks (a tell) |
| The roots tell | 20 `tomb_dust` along the spreading cracks over 30 ticks (a tell) |
| A ring's tell, and the ring singing | 12 `vent_grit` trembling over its stones over 30 ticks (a tell); while it sings, 2 `gather_dust` and one `air_ripple` per 5 ticks in uneven patches |
| The Hask jet-slam and shield-bash tells | 8 `engine_exhaust` from the pack over the slam tell; 4 `spark_shower` off the shield's rim over the bash tell (tells) |
| The stormback's charge and roar tells | 10 `gather_dust` pawed up over the charge tell; 12 `lightning_spark` over its plates over the roar tell (tells) |
| The matriarch's sweep tell | 12 `sand_burst` rising along the tail's arc over 20 ticks (a tell) |
| Ship refit or upgrade fitted | 20 `spark_shower` over 40 ticks; the steam puff is 10 `engine_exhaust` |
| Ship overheat | 6 `grenade_smoke` over 20 ticks |
| Bolt on a block; shield down; shield recharge | 3 `spark_shower`; one `air_ripple` and 8 `spark_shower`; one `air_ripple` |
| Gunship generator destroyed | as a fighter destroyed |
| Doors (`site_door`) | 16 block-dust particles of the door's block over 20 ticks |
| Verse stone channel | 2 `verse_mote` per tick along the stream |
| Turning rite; rank 4 ceremony | 30 `gather_dust` over 40 ticks; Master 20 `crystal_glint`, Darth 12 `flare_ember` and 16 `tomb_dust`, Wayfarer 16 `gather_dust` |
| Legendary craft; crystal tuning | 1 `spark_shower` per 2 ticks for 40 ticks; 12 `crystal_glint` |
| Other foe and boss moments (burrow and ripple, chant pulse, storm-call strike, impacts, shield block, Hymn-step arrival, rod strike, Crescendo blast, Cantor slam, roots, mire fog) | at most 30 per event, and 4 per tick for a lasting one, of the owner's 15.2 types |
| The verse beam's coming path | 24 `tomb_dust` lifting along it over 40 ticks (a tell) |
| The First Cantor and his choir | 2 `spectral_wisp` per tick each; the Cantor's unravelling 120 over 60 ticks |
| Ulvaine's hologram, the echo | the model drawn translucent; no particles |

### 15.2 New particle types (each owner registers its own)

As in M4 there is no shared particle class: each owner registers its types in its feature's `<Feature>Particles` class and owns the particle JSON and texture. Any worker may use an existing type by id (the M4 rule, `MILESTONE_4.md` 13.2); every type reused below already exists.

| Owner | New types | Reused by id |
|---|---|---|
| W1 | `atmosphere_streak` | `engine_exhaust`, `spark_shower`, `grenade_smoke` |
| W2 | `hull_debris` | `spark_shower`, `air_ripple`, `grenade_smoke`, `engine_exhaust` |
| W3 | `sand_gust`, `verse_mote` | `lightning_spark` |
| W5 | none | `gather_dust`, `crystal_glint`, `flare_ember`, `tomb_dust` |
| W6 | `stasis_rime`, `drain_mote`, `valor_dust` | `air_ripple`, `shadow_wisp`, `flare_ember` |
| W7 | none | `crystal_glint` (tuning), `spark_shower` (legendary crafts) |
| W8 | `sand_burst`, `spectral_wisp` | `lightning_spark`, `gather_dust`, `tomb_dust`, `shadow_wisp`, `air_ripple`, `light_mote`, `vent_grit`, `engine_exhaust`, `spark_shower` |

### 15.3 Effect events (look and sound)

Delivery uses the M1 `EffectEvents` (`around`, `toTracking`, `toPlayer`). "Tell" marks the variants the lead adds to the frozen `DangerTellTable` in Phase 0 (Danger Sense reacts to them, and `Vfx.tellCount` keeps them whole).

| Id | Owner | Delivery (variant, intensity) | Look | Sound |
|---|---|---|---|---|
| `ship_flight` | W1 | toTracking; entity = the ship; variant 0 take the helm, 1 boost, 2 thruster puff, 3 terrain impact (intensity = speed lost), 4 hand to the autopilot, 5 turned back at the orbit's edge, 6 landing refused | the helm: struts fold, the ramp shuts, the engines flare; boost: a longer, brighter plume and a pressure thump; puffs: short white jets from the hull; impact: sparks and the struck block's dust | `ship.flight.helm`, `ship.flight.boost`, `ship.flight.thruster`, `ship.flight.impact`, `ship.flight.edge`, `ship.flight.land_warn`. The engine loops `ship.flight.engine` and `ship.interceptor.engine` are client sound instances whose pitch follows speed, not events |
| `atmosphere` | W1 | toPlayer (the pilot and every rider); variant 0 up into orbit, 1 down into a sky | 4.7: streaks and heat shimmer, then black (up) or cloud (down); `atmosphere_streak` around the hull | `ship.flight.atmosphere` |
| `ship_refit` | W1 | around; variant 0 to the interceptor, 1 back to the courier, 2 upgrade fitted | the shipwright's crane, welding sparks and a puff of steam that hides the body swap; an upgrade: sparks at the fitting point | `ship.refit`, `ship.upgrade_fit` |
| `ship_weapon` | W2 | toTracking; entity = the firing ship or fighter; variant 0 cannons, 1 turret, 2 missile launch, 3 overheat, 4 fighter lasers, 5 fighter missile, 6 gunship turret | a short muzzle glow at the gun port; overheat: the barrels vent a thin smoke | `space.cannon.fire`, `space.turret.fire`, `space.missile.launch`, `space.cannon.overheat`, `space.fighter.fire` (4, 6), `space.missile.launch` (5) |
| `ship_hit` | W2 | around; pos = the impact; variant 0 shield, 1 hull, 2 block; intensity = damage | shield: a ripple of bent space spreading over the struck side, 3 sparks; hull: sparks and fragments; block: sparks only | `space.bolt.hit_shield`, `space.bolt.hit_hull`, `blaster.impact` (existing) |
| `ship_shield` | W2 | toTracking; entity = the ship; variant 0 down, 1 recharge starts, 2 full | down: a last ripple that tears into sparks; recharge: one faint shimmer crawling over the hull | `space.shield.down`, `space.shield.recharge` |
| `missile_lock` | W2 | toPlayer; variant 0 locking (intensity = progress), 1 locked, 2 lost, 3 incoming (start), 4 incoming (end) | the combat HUD's lock ring and edge pulse (5.10) | `space.missile.lock_tone` (loop), `space.missile.locked`, `space.missile.warning` (loop) |
| `ship_destroyed` | W2 | around; entity; variant 0 fighter, 1 the gunship's generator, 2 a gunship blast, 3 the gunship's end | a flash (steady with flashing off), fragments tumbling outward and burning out; the gunship: blasts along its spine, then it breaks in two | `space.fighter.explode`, `space.gunship.generator_down`, `space.gunship.explode` |
| `ship_disabled` | W1 | toTracking; entity = the player's ship; variant 0 disabled, 1 the rescue arrives | sparks, smoke, running lights that flicker (steady with flashing off) | `ship.flight.alarm` (loop, the pilot only), `ship.flight.rescue` |
| `gunship` | W2 | toTracking; entity; variant 0 bay opens, 1 launch | the bay doors part and two fighters drop out of the hull | `space.gunship.launch`; `space.gunship.hum` is an entity-bound loop |
| `space_comm` | W2 | toPlayer; variant = speaker (0 Hymn pilot, 1 pirate, 2 clan pilot, 3 Varra, 4 an ace); intensity = line index | a subtitle line in the M4 bark style | `space.comm` (a radio crackle) |
| `space_scan` | W2 | toPlayer; variant 0 scanning (intensity = meter), 1 broken, 2 complete | the HUD scan meter (5.9) | `space.scan` (loop) |
| `nav_buoy` | W2 | around; entity; variant 0 blink, 1 reached | a blinking marker light (steady with flashing off) | `space.buoy` |
| `sandstorm` | W3 | toPlayer (every player in Ghessar); variant 0 starts, 1 ends; intensity = strength | the fog closes in over 200 ticks; blowing sand around the camera (15.1) | `sandstorm.wind` (loop) |
| `verse_stone` | W3 | around; pos = the stone; variant 0 slowing hum, 1 channel starts, 2 channel progress (intensity = 0–1), 3 channel broken, 4 the Force answers (the 40-tick cut), 5 silenced | hum: the air around the monolith bends outward once and motes drift off it; channel: motes stream from the stone to the player's hand; silenced: its faint glow drains and the motes fall as grit | `verse_stone.pulse`, `verse_stone.channel` (loop), `verse_stone.silence`; `verse_stone.hum` is a positional loop while it is awake |
| `stormglass` | W3 | around; pos; variant 0 a cluster forms | static crawls over the struck basalt and a cluster pushes up | `stormglass.form` |
| `site_door` | W3 | around; pos; variant 0 the holdfast gate shuts, 1 it opens, 2 the Heart's door, 3 the undercroft door | heavy plates slide; stone grinds apart with falling grit | `holdfast.gate`, `hymnal.door` |
| `verse_vision` | W4 | toPlayer; variant 0–2 the verse; intensity = duration | a 6-second wash of pale grey-gold over the screen, the world's sound dipping, three subtitled lines (6.6) | `story.verse_vision` |
| `hologram` | W8 (W4 sends it) | around; pos = Dask's desk; variant 0 start, 1 end | Ulvaine's model drawn translucent blue with slow horizontal scan bands, flickering at the edges (steady with flashing off) | `story.hologram` (loop while it plays) |
| `turning` | W5 | toPlayer; variant 0 an offer arrives, 1 the rite (Fall), 2 the rite (Return) | the offer: none beyond the messenger; the Fall rite: a cold wind through the camp lifts grit and dims the fire; the Return rite: dust settles and the fire brightens | `story.turning.offer`; the rite's sound plays with its banner |
| `rank_four` | W5 | around; entity = the player; variant 0 Master, 1 Darth, 2 Wayfarer | Master: Saelen lays her lit blade flat across the player's open hands and pale motes fall slowly around them; Darth: Sorn's writ burns from its edges as he reads the name, and ash lifts; Wayfarer: Tessaly's figure projects from the Compass (the M3 Pathfinder look) and steps aside to show the road | `story.rank_four` |
| `lesson_complete` variants 11–15 | W4 draws the banners; the senders are W4 (11), W5 (12, 13), W7 (14), W8 (15) | toPlayer | banners `chapter5` (11), `turning` (12), `rank_four` (13), `legendary` (14), `long_hymn` (15) | `story.chapter_five`, `story.turning.rite`, `story.rank_four`, `bench.legendary`, `story.lesson_complete` (existing) |
| `force_stasis` | W6 | toTracking; entity = the target; variant 0 cast, 1 held, 2 break | 9.3 | `force.stasis.cast`, `force.stasis.loop`, `force.stasis.break` |
| `force_valor` | W6 | toTracking; entity = the caster; variant 0 cast, 1 an ally touched (entity = the ally), 2 end | 9.4 | `force.valor.cast`, `force.valor.end` |
| `force_drain` | W6 | toTracking; entity = the caster; variant 0 start, 1 pulse (dir = to the target), 2 end | 9.5 | `force.drain.loop`, `force.drain.end` |
| `force_fear` | W6 | toTracking; entity = the caster; variant 0 cast (dir), 1 a victim (entity = the victim) | 9.6 | `force.fear.cast` |
| `force_rage` | W6 | toTracking; entity = the caster; variant 0 start, 1 end | 9.7 | `force.rage.cast`, `force.rage.loop` (the caster only), `force.rage.end` |
| `force_mind_trick` | W6 | toTracking; entity = the target; variant 0 success, 1 refused | 9.8 | `force.mind_trick.cast`, `force.mind_trick.fail` |
| `offhand_strike` | W7 | toTracking; entity = the player | the off hand's swing (vanilla) and the blade's arc | `saber.offhand_swing` |
| `crystal_tune` | W7 | toPlayer; variant = the tuning's index | a `crystal_glint` burst in the Tune slot | `workbench.tune` (existing, reserved in M4) |
| `bench_craft` variant 3 | W7 (M4's event, a new variant) | around | Legendary: a slow rise of bright sparks and a warm glow from the finished item (steady) | `bench.legendary` |
| `scour` | W8 | toTracking; entity; variant 0 burrowed ripple, 1 burst tell, 2 burst, 3 burrow, 4 matriarch tail-sweep tell, 5 matriarch calls her brood; **tells 1, 4** | a faint ripple in the sand; the sand boils; the scour erupts in a spray of sand; it sinks, and the sand slumps in over it; the matriarch's tail rises and coils | `scour.hiss` (loop while burrowed nearby), `scour.burst_tell`, `scour.burst`, `scour.burrow`, `scour.matriarch.sweep`, `scour.matriarch.call` |
| `hymnsworn` | W8 | variant 0 chant pulse (toTracking; entity), 1 storm-call tell (around; pos = the patch), 2 storm-call strike, 3 a burst of Force lightning; **tell 1** | the chanter's breath fogs and nearby grit trembles; static crawls over an uneven patch and grit lifts; a cosmetic bolt and a scorch | `hymnsworn.chant`, `hymnsworn.call_tell`, `hymnsworn.call_strike`, `force.lightning.release` (existing) |
| `stormback` | W8 | toTracking; entity; variant 0 charged, 1 charge tell, 2 charge impact, 3 roar tell (it draws a bolt), 4 charge spent; **tells 1, 3** | crackling plates; it lowers its head and paws grit; a heavy impact; it rears and roars to the sky; the crackle fades | `stormback.charged` (loop), `stormback.charge_tell`, `stormback.impact`, `stormback.roar` |
| `hask_warrior` | W8 | toTracking; entity; variant 0 jet-slam tell, 1 jet-slam impact, 2 shield block, 3 shield bash tell, 4 shield bash; **tells 0, 3**. Rifles, rockets and jet hops send the M4 `hunter_tell` and `hunter_attack` by id | the pack whines and the warrior crouches; flame and a dust burst; sparks off the shield; the shield drawn back | `hask.slam_tell`, `hask.slam`, `hask.shield_block`, `hask.bash_tell`, `hask.bash` |
| `ulvaine` | W8 | around; variant = moment: 0 phase, 1 Hymn-step tell, 2 Hymn-step arrival, 3 lightning channel, 4 rod charge tell (pos = the patch), 5 rod strike, 6 Hymn pulse (intensity = radius), 7 Crescendo begins, 8 a resonator lights or breaks, 9 Crescendo blast, 10 yield, 11 the echo fades; **tells 1, 4, 6, 7** | 12.2: a violet shimmer and a displacement of air; static on the floor and the rod's hum; a wave of bent air and lifted grit; the spires' inner light (steady); the hall shaking; she kneels | `boss.ulvaine.*` (15.4) |
| `first_cantor` | W8 | around; variant: 0 phase, 1 beam tell (dir), 2 beam sweep, 3 slam tell, 4 slam, 5 chain lightning, 6 roots tell, 7 roots grip, 8 mire fog spreads, 9 basalt mark tell, 10 basalt strike, 11 ring tell, 12 ring sings, 13 a silencing point opens, 14 a point silenced, 15 the choir shields him, 16 he unravels; **tells 1, 3, 6, 9, 11** | 12.3: dust lifting along the beam's coming path; cracks spreading as roots push up; black fog creeping over the floor; static and grit at the marks; the ring's stones trembling before it sings; grit dancing and the air shimmering over the singing ring's stones in uneven patches; pale motes drifting from the choir and settling on his robes; his body coming apart into drifting light | `boss.cantor.*` (15.4) |

Cartel enforcers add no event: the bruiser's shove uses the M2 `npc_windup` tell and the captain's grenades the M3 `grenade_tell` and `grenade_fuse`. The mentor duels use the existing duellist and power events.

### 15.4 Sounds and music

Every new id is in `tools/assets/sounds/events_m5.json` (written in Phase 0, in the M4 format) with its feature, subtitle, loop and stream flags and volume; `gen_sounds.py --m5` writes placeholders, `sounds.json` entries and the Java constants (the M4 process). Recipes per owner: `sounds/flight.py` (W1), `space.py` (W2), `world_m5.py` (W3), `story_m5.py` (W4), `turning.py` (W5), `force_m5.py` (W6), `saber_m5.py` (W7), `enemy_m5.py` (W8). Reused ids keep their audio and are not listed again: `workbench.tune`, `blaster.impact`, `story.lesson_complete`, `story.duel_begin`, `story.duel_end`, `saber.form_change`, `force.lightning.release`.

| Feature (owner) | New ids |
|---|---|
| `flight` (W1) | `ship.flight.engine` (loop), `ship.flight.boost`, `ship.flight.thruster`, `ship.flight.atmosphere`, `ship.flight.impact`, `ship.flight.land_warn`, `ship.flight.helm`, `ship.flight.edge`, `ship.interceptor.engine` (loop), `ship.refit`, `ship.upgrade_fit`, `ship.flight.alarm` (loop), `ship.flight.rescue` |
| `space` (W2) | `space.cannon.fire`, `space.cannon.overheat`, `space.turret.fire`, `space.fighter.fire`, `space.missile.lock_tone` (loop), `space.missile.locked`, `space.missile.launch`, `space.missile.warning` (loop), `space.bolt.hit_shield`, `space.bolt.hit_hull`, `space.shield.down`, `space.shield.recharge`, `space.fighter.engine` (loop), `space.hymn_interceptor.engine` (loop), `space.fighter.explode`, `space.gunship.hum` (loop), `space.gunship.generator_down`, `space.gunship.explode`, `space.gunship.launch`, `space.comm`, `space.scan` (loop), `space.buoy`, `ambient.space` (loop, stream), `music.space` (stream) |
| `world_m5` (W3) | `verse_stone.hum` (loop), `verse_stone.pulse`, `verse_stone.channel` (loop), `verse_stone.silence`, `sandstorm.wind` (loop), `stormglass.form`, `holdfast.gate`, `hymnal.door`, `music.ghessar`, `music.tyrvann`, `ambient.ghessar.dunes`, `ambient.ghessar.saltflat`, `ambient.ghessar.canyons`, `ambient.tyrvann.stormwood`, `ambient.tyrvann.mire`, `ambient.tyrvann.basalt`, `ambient.saltgate`, `ambient.holdfast`, `ambient.hymnal` (music and ambience streamed loops) |
| `story_m5` (W4) | `story.chapter_five`, `story.verse_vision` |
| `turning` (W5) | `story.turning.offer`, `story.turning.rite`, `story.rank_four` |
| `force_m5` (W6) | `force.stasis.cast`, `force.stasis.loop`, `force.stasis.break`, `force.valor.cast`, `force.valor.end`, `force.drain.loop`, `force.drain.end`, `force.fear.cast`, `force.rage.cast`, `force.rage.loop`, `force.rage.end`, `force.mind_trick.cast`, `force.mind_trick.fail`, `force.focus` |
| `saber_m5` (W7) | `saber.guardian.{ignite,hum,retract}`, `saber.duelist.{ignite,hum,retract}`, `saber.reliquary.{ignite,hum,retract}`, `saber.cantor.{ignite,hum,retract}` (the hums loop), `saber.offhand_swing`, `bench.legendary` |
| `enemy_m5` (W8) | `cartel.{bark,hurt,death,shove}`; `hask.{bark,hurt,death,slam_tell,slam,shield_block,bash_tell,bash}`; `hymnsworn.{chant,hurt,death,call_tell,call_strike}`; `scour.{hiss,burst_tell,burst,burrow,hurt,death}`, `scour.matriarch.{sweep,call}`; `stormback.{idle,hurt,death,charge_tell,impact,roar,charged}`; `boss.ulvaine.{hurt,phase,step_tell,step,lightning,rod_charge,rod_strike,pulse,crescendo,resonator,blast,yield,echo_fade}`; `boss.cantor.{hurt,phase,beam_tell,beam,slam_tell,slam,chain,roots,fog,mark,strike,ring_tell,ring,silence,unravel,choir,engine}`; `music.boss.ulvaine`, `music.boss.cantor` (stream); `story.hologram` (loop; W8's client plays and stops it with the `hologram` effect) |

Loops: `hymnsworn.chant`, `scour.hiss`, `stormback.charged`, `boss.ulvaine.lightning`, `boss.cantor.beam`, `boss.cantor.ring`, `boss.cantor.choir` and `boss.cantor.engine` loop as well.

**Music and ambience:**

| Id | Use |
|---|---|
| `music.ghessar`, `music.tyrvann` | the planets' `background_music` attribute (the vanilla music slider) |
| `music.space` | the orbit dimension type's `background_music` |
| `ambient.ghessar.{dunes,saltflat,canyons}`, `ambient.tyrvann.{stormwood,mire,basalt}` | biome ambient loops and additions (3.6) |
| `ambient.space` | the orbit field biome's ambient loop (5.1) |
| `ambient.saltgate`, `ambient.holdfast`, `ambient.hymnal` | hub and site beds through `SiteAmbience` |
| `music.boss.ulvaine`, `music.boss.cantor` | `BossMusicService` (12.5), one track each |

**Loops and their stop rules** (every loop also stops on death, logout, dimension change and `/kill`):

| Loop | Stops when |
|---|---|
| `ship.flight.engine`, `ship.interceptor.engine` | the helm is released (landing, the autopilot, a rescue) or the listener's ship leaves render range; the M4 `ship.idle` takes over on the ground |
| `space.missile.lock_tone`, `space.missile.warning` | the lock is lost or fired; the incoming missile hits, misses or expires |
| `ship.flight.alarm` | the rescue completes |
| `space.scan` | the scan breaks or completes, or the run ends |
| `space.fighter.engine`, `space.hymn_interceptor.engine`, `space.gunship.hum` | the entity is removed (entity-bound instances) |
| `verse_stone.hum` | the stone is silenced, or the listener moves 24 blocks away |
| `verse_stone.channel` | the channel completes or breaks |
| `sandstorm.wind` | the storm ends, or the player leaves Ghessar |
| `story.hologram` | the message ends, or the player moves 16 blocks away |
| `force.stasis.loop` | the target's `stasis` effect ends for any reason (break, expiry, milk, `/effect clear`, death, unload) |
| `force.drain.loop`, `force.rage.loop` | the channel ends on any cancel path; for Rage, its effect ends for any reason |
| `saber.<emitter>.hum` | the M1 hum rules (retract, unequip, slot change) |
| `hymnsworn.chant`, `scour.hiss`, `stormback.charged` | the entity dies, is removed, or stops chanting, burrowing or being charged |
| `boss.ulvaine.lightning`, `boss.cantor.{beam,ring,choir,engine}` | the attack or phase ends, the fight is won, reset or wiped, or the listener leaves the Heart or the undercroft |
| `music.boss.*` | victory, yield, reset, wipe, or leaving the fight (`boss_music` track 0, 12.5) |
| hub and site beds | leaving the site (`site_presence` changes) |

**Placeholders.** Every M5 sound and track is a generated placeholder until it is recorded or composed; `AUDIO_BRIEF.md` gets a Milestone 5 section with a brief for each id, and `ASSET_MANIFEST.md` lists every placeholder sound, texture and model the milestone adds (17.3, step 8).

---

## 16. Balance (defaults)

Ticks: 20 per second. "A day" is an in-game day (24,000 ticks, 20 minutes), as everywhere in the mod. Cumulative XP to reach each level (`Progression.xpToNext`, unchanged curve):

| Level | 15 | 16 | 17 | 18 | 19 | 20 | 21 |
|---|---|---|---|---|---|---|---|
| Cumulative XP | 46,180 | 54,612 | 63,914 | 74,115 | 85,244 | 97,329 | 110,396 |

### 16.1 Expected budget (solo, normal pace)

M4 players finish at about level 15 (`MILESTONE_4.md` 14.1). The table follows a Jedi or Sith who takes the kajidic's way; the clan's way pays about the same (the matriarch, and the duel of honour where Kaast died), and the Force's way less, but it is quicker.

| Source | XP | Total | Lv |
|---|---|---|---|
| Chapter Five begins | – | ≈ 46,200 | 15 |
| The call (300) and the helm lesson (200 + 300 + 300) | 1,100 | ≈ 47,300 | 15 |
| Saltgate: discovery (400 + 200) and one way (the kajidic's two jobs, 2 × 1,200) | 3,000 | ≈ 50,300 | 15 |
| The counting-house (1,500) and the factor (300) | 1,800 | ≈ 52,100 | 15 |
| Cannons (200), the blockade (2,000) and its kills (7 interceptors and the gunship, ≈ 1,000 with fatigue) | ≈ 3,200 | ≈ 55,300 | **16** |
| Tyrvann: discovery (400 + 200) | 600 | ≈ 55,900 | 16 |
| The three verse stones (3 × 1,500) and their 12 guards (≈ 550) | ≈ 5,050 | ≈ 60,950 | 16 |
| 3 faction contracts at level 16 (890 each) | ≈ 2,700 | ≈ 63,650 | 16 |
| Kills on the new worlds (≈ 40 at 36–80 with fatigue) and site discovery (4 worldgen sites at 150) | ≈ 2,600 | ≈ 66,250 | **17**: Ulvaine's gate |
| One space patrol (2 raiders and the 300 bonus) | ≈ 460 | ≈ 66,700 | 17 |
| The Hymnal's processional (≈ 500) and Ulvaine (5,000) | 5,500 | ≈ 72,200 | 17 |
| Report | 1,000 | ≈ 73,200 | 17 |
| A second way's missions, left open as faction work (about half of players; ≈ 1,500), or 2 more contracts | ≈ 1,500 | ≈ 74,700 | **18** |

A solo player at a normal pace finishes Chapter Five at about level 18 (section 2 says "about 18"). Rank 4 asks for level 20 (97,329): about 22,600 more XP from the endgame loop, which pays about 4,700 a day at full use (Ulvaine's echo 800, two space bounties 1,200, three contracts 2,700) plus the raid's 2,000 a week. That is five or six days of the loop, two to three hours of play, during which the player also gathers the deeds, the mastery and the trust that rank 4 asks for.

If playtests show players reaching level 17 well after the third verse stone, lower `chapter_five.duelLevel` to 16 before raising any reward.

**Skill points in M5:**

| Source | SP |
|---|---|
| Levels 15 to 20 | 5 |
| The third verse stone | 1 |
| Ulvaine | 2 |
| **Total by level 20** | about 8 |
| Rank 4 (the ceremony) | 2 more |

Purchasable M5 nodes (9.9, 10.1): Jedi 7 and Sith 10 (Mind Trick is open to both), plus 6 for the three forms; Independents 3 (Mind Trick) plus the forms. Every build has to choose.

**Credits:**

| Source | Credits |
|---|---|
| Saltgate: the kajidic's two jobs (150 each) or the matriarch (150) | 150–300 |
| The counting-house, the blockade, the verse stones (3 × 100) and Ulvaine | 150 + 200 + 300 + 400 |
| Contracts (25 + 5 × level: 105 at level 16) | ≈ 300 for the 3 in the XP table |
| Fighter kills (10–15 each) and enemy drops | ≈ 250 |
| **Typical by the end of Chapter Five** | ≈ 2,000 earned |
| The loop | two bounties 800 a day, contracts ≈ 375 a day, the raid 400 a week |
| Typical spending | shield 1,000, thrusters 900, missile rack 1,200 and missiles (60 for 4), the interceptor refit 2,500 and 6 alloy plates, tunings 100 each, legendary ship parts 500 at the shipwright, the cipher 50, hymnstone 400 a week from the fence |

The free cannons come from the story; everything else in space is a goal for the loop.

### 16.2 Space combat reference

| | Cannons (pilot) | Turret (gunner) | Missile |
|---|---|---|---|
| Burst DPS | 24 (6 × 4 per second) | 15 | 30 per lock (one per 20 ticks) |
| Sustained (overheat cycle) | ≈ 10: about 9 shots from 40 heat to 100, then the 60-tick lock | ≈ 7 | limited by the hold |
| Shots to destroy | Hymn interceptor 5 (30), Hask fighter 8 (45), kajidic raider 10 (60), generator 14 (80), gunship 67 (400) | | an interceptor in one, a raider in two |

Targets: a courier with cannons and no shield clears the blockade with about half its hull when flown reasonably, and an unarmed courier is never attacked (5.6). Enemy fire comes in runs: fighters fire only while their nose is within 10° of the target (`space.fighterAimCone`) and their bolts spread 2° (`space.fighterSpread`), so a ship that turns and boosts takes far less than the fighters' burst damage. The gunship's turrets are the main threat; destroying the generator first is the lesson.

### 16.3 Enemy reference

Kill XP follows the M1 rule with anti-grind fatigue. Health and armour are in 12.1 and 5.5.

| Enemy | XP | Credits (drop) | Main hit |
|---|---|---|---|
| Cartel gunman / bruiser / captain | 45 / 50 / 90 | the M4 drop (50% for 1–6) | rifle 5; vibrosword 7 then shove 4; pistol 5 and frags 7 |
| Clan Hask rifle / blade / champion | 70 / 70 / 150 | the M4 drop; `clan_iron` 10% | rifle 6 and wrist rocket 7; vibrosword 8 and jet-slam 9; vibrosword 9 |
| Hymnsworn chanter / blade / storm-caller | 50 / 50 / 100 | none | staff 3; vibroblade 5; storm call 8 and `shock` 20 |
| Sand scour / matriarch | 60 / 400 | none | bite 6 and Slowness II for 40 ticks; tail sweep 8 |
| Stormback | 120 (charged 160) | none | charge 10 (15 charged) and knockback |
| Hymn interceptor / kajidic raider / Hask fighter / gunship | 60 / 80 / 70 / 600 | 10 / 15 / 10 / 10 (straight to the purse) | 4 / 5 (missile 15) / 5 / 3 per bolt |
| Darth Ulvaine | 5,000 (story, once) | 400 | saber 10, lightning 3 per 10 ticks, rods 8, pulse 6, Crescendo 12 |
| The First Cantor | 2,000 a week | 400 | beam 8, slam 10, chain 5 per target, basalt 10, rings 4 a second |

Enemy health for reference: troopers 20–30, hunters 40, cartel enforcers 26–48, Hask warriors 40–80, Hymnsworn 24–36, sand scours 30 (matriarch 120), stormbacks 80, Kaast 500, Ulvaine 600, the First Cantor 2,000 (all before group scaling).

### 16.4 Sections

Each section is a `BalanceConfig.section` with public fields. Every tuning number in sections 3–12 is a key here, except numbers that are data (schematic inputs, vendor stock and prices, loot tables, contract templates, templates and their sizes) and values fixed at registration (entity sizes, armour base values, stack sizes, the peat fuel time). Owners in brackets.

### `flight` (`travel.FlightBalance`, W1)

| Key | Default |
|---|---|
| `enabled` / `serverSteering` / `serverSteeringSpeedPct` | true / false (true switches every ship to fallback B, 4.1) / 67 |
| `maxSpeedCap` / `pitchLimit` / `reverseSpeed` / `strafePct` | 2.0 / 60 / 0.2 / 40 |
| `courierSky` / `courierOrbit` / `courierThrust` / `courierBrake` / `courierClimb` / `courierTurn` | 1.0 / 1.4 / 0.04 / 0.08 / 0.4 / 6 |
| `interceptorSky` / `interceptorOrbit` / `interceptorThrust` / `interceptorBrake` / `interceptorClimb` / `interceptorTurn` | 1.2 / 1.45 / 0.07 / 0.12 / 0.5 / 9 |
| `decaySky` / `decayOrbit` | 0.97 / 0.99 |
| `boostMultiplier` / `boostTicks` / `boostRechargeTicks` | 1.25 / 60 / 160 |
| `thrustersSpeedPct` / `thrustersBoostPct` | 10 / 25 |
| `impactFloor` / `impactStep` / `impactHull` | 0.6 / 0.25 / 2 |
| `helmSpoolTicks` / `helmRiseTicks` / `helmRiseBlocks` | 40 / 20 / 4 |
| `landHeight` / `landMaxSpeed` / `landLevelTolerance` / `hubBayRange` | 6 / 0.25 / 1 / 32 (Milestone 6: 88) |
| `orbitCeiling` / `atmosphereLine` / `orbitScale` / `orbitArrivalY` / `skyArrivalY` / `jumpArrivalY` / `transitionCooldown` | 300 / 64 / 8 / 100 / 290 / 160 / 60 |
| `orbitRadius` / `edgeWarning` | 2048 / 128 |
| `cannonsPrice` / `shieldPrice` / `thrustersPrice` / `missilesPrice` | 800 / 1000 / 900 / 1200 |
| `refitCredits` / `refitAlloy` / `refitBackCredits` / `interceptorHold` / `interceptorHull` | 2500 / 6 / 500 / 9 / 80 |
| `legendaryFitCredits` / `hymnDriveHyperspaceMult` (the Hymn-Tuned Drive, 11.2) | 500 / 0.5 |
| `rescueHull` / `tumbleTicks` | 20 / 40 |

### `space` (`space.SpaceBalance`, W2)

| Key | Default |
|---|---|
| `patrols` / `patrolCheckTicks` / `patrolChance` / `patrolGapTicks` / `patrolSize` / `hostilePatrolSize` / `patrolDistance` / `patrolLeaveRange` / `patrolBonusXp` | true / 2400 / 0.25 / 1200 / [2, 4] / [3, 4] / 120 / 200 / 300 |
| `pvpShipDamage` | false (fixed in M5, 5.4) |
| `cannonInterval` / `cannonDamage` / `cannonHeat` / `boltSpeed` / `boltRange` | 5 / 6 / 7 / 4 / 96 |
| `turretInterval` / `turretDamage` / `turretHeat` | 7 / 5 / 7 |
| `heatCoolPerSecond` / `heatCoolDelay` / `overheatLockTicks` / `overheatCoolTo` | 20 / 10 / 60 / 40 |
| `missileInterval` / `missileDamage` / `missileSplash` / `missileSplashRadius` / `missileSpeed` / `missileTurn` / `missileLife` | 20 / 30 / 10 / 3 / 1.6 / 6 / 100 |
| `lockCone` / `lockRange` / `lockTicks` | 20 / 80 / 30 |
| `courierShield` / `interceptorShield` / `shieldRecharge` / `shieldDelay` | 40 / 60 / 4 / 60 |
| `underFireTicks` | 200 |
| `fighterAimCone` / `fighterSpread` / `fighterShieldRecharge` / `fighterShieldDelay` | 10 / 2 / 5 / 60 |
| `hymnInterceptor` (speed, shield, hull, damage, rate) | 1.6, 10, 20, 4, 3 a second in bursts |
| `raider` | 1.2, 20, 40, 5; missile every 200 ticks for 15 |
| `haskFighter` | 1.4, 15, 30, 5 |
| `gunship` (speed, hull, generator hull, turret damage, turret rate, turret range, launch interval, launch count, max alive) | 0.3, 400, 80, 3, 2 a second, 64, 600, 2, 6 |
| `killXp` / `killCredits` | Hymn interceptor 60, raider 80, Hask fighter 70, gunship 600 / raider 15, others 10 |
| `blockadeDistance` / `blockadeWaves` / `clanFighters` / `peacefulPassTicks` | 150 / [3, 4] / 3 / 200 |
| `scanRange` / `scanTicks` / `buoyDistance` | 48 / 60 / [600, 1000] |
| `aceMultiplier` / `aceWingmen` | 2 / 2 |
| `capacitorShield` / `capacitorRechargeMult` (the Spindle Capacitor, 11.2) | 20 / 1.5 |
| `spindlePos` / `spindlePlaceRange` / `vaultSchematicChance` / `vaultSchematicPity` | [420, 150, -380] / 400 / 0.1 / 8 |
| `asteroidDensity` | home 3, Selvaren 4, Varkhast 2, Ghessar 3, Tyrvann 3 (chunks per cluster) |
| `orbitCatchY` / `coopRange` | -56 / 128 |

### `planets` additions (`planet.PlanetBalance`, W3)

| Key | Default |
|---|---|
| `sandstorms` / `sandstormIntervalDays` / `sandstormTicks` / `sandstormFogEnd` / `scourSandstormMultiplier` | true / [3, 5] / [3000, 6000] / 24 / 2 |
| `ridgeLightning` / `ridgeIntervalTicks` / `ridgeMinY` / `ridgeDistance` / `ridgeStormbackRange` / `ridgeHubExclusion` | true / [600, 1200] / 110 / [4, 10] / 24 / 48 (Milestone 6: 72) |
| `freighterEdgeChance` / `freighterEdgePity` (the Sunstone Edge schematic in a freighter hold, 11.4) / `verseGuards` (6.6) | 0.03 / 30 / [3, 4, 5] |
| `hymnalArchInterval` / `stormglassChance` | [400, 800] / 0.5 (per real strike on exposed basalt) |
| `verseRings` / `silenceTicks` / `silenceForceCut` / `humSlowRange` / `humSlowInterval` | [250, 350, 450] (ring radii, the M4 form, with the ring point on the middle one; the M4 `siteEnsureRange` 160 applies) / 100 / 40 / 8 / 60 |
| `hymnalRings` / `hymnalDepth` | [600, 750, 900] / 18 |
| `debtorCampRings` / `detentionRings` | [200, 300, 400] / [120, 160, 200] |

The M4 arrival keys (`chapter_four.planetXp` 400, `hubXp` 200) pay the new planets and hubs; `planets.siteDiscoveryXp` (150) pays the new worldgen sites.

### `chapter_five` (`quest.story.ChapterFiveBalance`, W4)

| Key | Default |
|---|---|
| `waitTicks` / `minSleepTicks` | 24000 / 100 |
| `callXp` / `helmXp` / `orbitXp` / `jumpXp` | 300 / 200 / 300 / 300 |
| `jobXp` / `jobCredits` / `jobRep` / `audienceRep` / `debtCredits` / `teelBribe` | 1200 / 150 / 60 / 80 / 200 / 150 |
| `matriarchXp` / `matriarchCredits` / `matriarchRep` / `honourXp` / `honourRep` | 1500 / 150 / 80 / 800 / 100 |
| `forceWayXp` / `forceWayRep` / `mindTrickWayRep` | 600 / -50 / -25 |
| `countingHouseXp` / `countingHouseCredits` / `countingHouseGuards` | 1500 / 150 / kajidic 0, clan 6, Mind Trick 4, Fear or force 8 |
| `daskXp` / `releaseRep` / `releaseLight` / `clanRep` / `clanKajidicRep` / `endKajidicRep` / `endClanRep` / `endDark` | 300 / 40 / 3 / 80 / -60 / -100 / -20 / -5 |
| `cannonsXp` / `blockadeXp` / `blockadeCredits` / `clanHelpFailures` / `clanHelpCredits` | 200 / 2000 / 200 / 2 / 1000 |
| `verseXp` / `verseCredits` / `versesSp` | 1500 / 100 / 1 |
| `duelLevel` | 17 |
| `ulvaineXp` / `ulvaineSp` / `ulvaineCredits` / `ulvaineResonance` | 5000 / 2 / 400 / 600 |
| `spareLight` / `bindJediLight` / `bindJediRep` / `bindSithRep` / `bindClanRep` / `bindClanCredits` / `endDarkUlvaine` / `endJediTrust` / `endSithTrust` | 8 / 5 / 100 / 150 / 150 / 500 / -10 / -10 / 10 |
| `reportXp` / `reportTrust` / `reportResonance` | 1000 / 10 / 400 |

### `factions_m5` (`identity.FactionM5Balance`, W4)

| Key | Default |
|---|---|
| `clanStartSpared` / `clanStartStripped` / `clanStartEnded` / `helmetReturnRep` | 100 / 0 / -150 / 100 |
| `contractRep` / `bountyXp` / `bountyCredits` / `bountyRep` / `driveSchematicChance` / `driveSchematicPity` | 25 / 600 / 400 / 15 / 0.02 / 25 |

### `turning` (`quest.turning.TurningBalance`, W5)

| Key | Default |
|---|---|
| `enabled` / `fallAlignment` / `returnAlignment` / `bandDays` / `reofferDays` / `refusals` | true / -50 / 50 / 1 / 3 / 3 |
| `taskXp` / `taskRepGain` / `taskRepLoss` / `fallDark` / `returnLight` | 1500 / 100 / -150 / -5 / 5 |
| `confrontationXp` / `crystalDark` / `bladeLight` / `confrontationResetTicks` | 1500 / -8 / 3 / 1200 |
| `riteXp` / `riteResonance` / `newTrust` / `refundPct` / `rankCap` / `oldFactionCap` / `newFactionBonus` | 1000 / 300 / 40 / 50 / 2 / -500 / 150 |
| `rank4Level` / `rank4Trust` / `rank4Rep` / `rank4Alignment` / `rank4MasteryRank` / `rank4MasteryPowers` / `rank4Deeds` | 20 / 90 / 800 / 50 / 3 / 2 / 2 |
| `deedEchoes` / `deedBounties` / `deedContracts` / `wayfarerFactions` / `wayfarerTier` | 3 / 5 / 5 / 3 / 100 |
| `ceremonyXp` / `ceremonySp` / `ceremonyResonance` | 1500 / 2 / 900 |

### `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick` and `skills` additions (W6)

Every number of 9.2–9.8, one key each, with the M3 naming (`cost`, `cooldownTicks`, `durationTicks`, `range`, `coneDegrees`, `pulseTicks`, `pulseDamage`, `pulseHeal`, `pulseEnergy`, `maxPulses`, …; each branch's numbers beside its power), plus `pvpEffects` false for each. `skills.secondBarLevel` 15; `skills.wayfarerEnergyPct` / `skills.wayfarerSpeedCostPct` 10 / 20 (8.5). The control kinds use the M2 diminishing-returns rule and its one key, `force_push.staggerDrWindowTicks`.

### `saber` additions (`saber.SaberBalance`, W7)

| Key | Default |
|---|---|
| `makashiParryTicks` / `makashiRiposteWindow` / `makashiPassiveWindow` / `makashiRipostePct` / `makashiDuelistPct` / `makashiBoltGuardPct` | 2 / 20 / 30 / 40 / 15 / 20 |
| `djemSoHeavyPct` / `djemSoWindupCut` / `djemSoStaggerTicks` / `djemSoReturnChance` / `djemSoSpeedPct` | 20 / 2 / 10 / 0.6 / -5 |
| `jarKaiSpeedPct` / `jarKaiGuard` / `jarKaiParryTicks` | 10 / 15 / -1 |
| `offhandDamage` / `offhandSpeed` / `dualParryTicks` | 0.7 / 0.2 / 1 |
| `shortReach` / `shortSpeedPct` / `shortDamagePct` / `longReach` / `longSpeedPct` / `longDamagePct` | -0.5 / 10 / -10 / 0.5 / -10 / 5 |
| `tuneCredits` / `keenPct` / `swiftPct` / `steadyTicks` / `wardingGuard` / `resonantPct` | 100 / 5 / 6 / 1 / 15 / 5 |
| `tuneSunstone` / `tuneStormglass` / `tuneHymnstone` | 2 / 2 / 1 |

### `crafting` additions (`crafting.CraftingBalance`, W7)

| Key | Default |
|---|---|
| `qualityWeaponDamage` / `qualityArmour` / `qualityDurability` (the M4 keys, now four values; a stored three-value array keeps Legendary's default) | [1.0, 1.06, 1.12, 1.18] / [1.0, 1.15, 1.30, 1.40] / [1.0, 1.25, 1.5, 1.75] |
| `legendaryLevel` | 5 |
| `cantorDamagePct` / `haskGuard` / `haskParryTicks` / `sunforgedSpeedPct` / `sunforgedGuard` / `stormglassShockTicks` / `stormglassDroidPct` | 8 / 15 / 1 / 8 / 5 / 20 / 10 |
| `clanIronArmour` / `clanIronBlasterPct` / `hymnweaveEnergy` / `hymnweaveRegenPct` / `stormCellRange` / `stormCellPct` / `sunstoneEdgePct` / `sunstoneEdgeFireTicks` | 3 / 10 / 6 / 10 / 4 / 30 / 12 / 40 |

### `cartel_enforcer`, `hask_warrior`, `hymnsworn`, `sand_scour`, `stormback` (W8)

Every number of 12.1 and 16.3, one key each, plus the natural spawn weights (`sand_scour.weight` 10, `stormback.weight` 8) and `clanIronChance` 0.1.

### `ulvaine` (`boss.ulvaine.UlvaineBalance`, W8; W4 reads `helperXp`)

| Key | Default |
|---|---|
| `maxHealth` / `perParticipant` / `scalingCap` | 600 / 0.6 / 4 |
| `phase2At` / `phase3At` / `yieldAt` / `transitionTicks` | 0.65 / 0.30 / 0.10 / 60 |
| P1 `damage` / `guard` / `parry` / `strings` / `lightningChannel` / `lightningDamage` / `lightningInterval` / `stepInterval` / `stepTell` / `stepDistance` | 10 / 140 / 0.40 / [3, 6] / 40 / 3 / 10 / 300 / 15 / 6 |
| P2 `rodInterval` / `rodTell` / `rodDamage` / `rodRadius` / `shockTicks` / `pylonDamagePct` | 160 / 30 / 8 / 2.5 / 20 / 20 |
| P3 `slowPct` / `pulseInterval` / `pulseRadius` / `pulseTicks` / `pulseHeight` / `pulseDamage` / `pulseSlowTicks` | 20 / 160 / 16 / 40 / 1.5 / 6 / 40 |
| `crescendoAt` / `resonatorHealth` / `crescendoTicks` / `crescendoDamage` / `stasisMaxTicks` / `chokeMaxTicks` | 0.20 / 20 / 400 / 12 / 20 / 20 |
| `resetTicks` / `participantLeaveTicks` / `helperXp` | 200 / 100 / 1200 |
| `echoXp` / `echoShardChance` | 800 / 0.25 |

### `raid` (`boss.raid.RaidBalance`, W8)

| Key | Default |
|---|---|
| `maxPlayers` / `weekDays` / `countdownTicks` / `joinTicks` | 8 / 7 / 200 / 600 |
| `maxHealth` / `perParticipant` / `phase2At` / `phase3At` | 2000 / 0.5 / 0.70 / 0.30 |
| P1 `choirHealth` / `choirReturnTicks` / `beamTell` / `beamDamage` | 60 / 900 / 40 / 8 |
| P2 `slamTell` / `slamDamage` / `chainTargets` / `chainDamage` / `verseInterval` / `rootsTell` / `rootsRadius` / `rootsTicks` | 20 / 10 / 3 / 5 / 600 / 30 / 6 / 60 |
| P2 `fogPools` / `fogWidth` / `fogTicks` / `fogDamage` / `basaltMarks` / `basaltTell` / `basaltDamage` / `shadeInterval` / `shades` | 3 / 4 / 200 / 2 / 5 / 40 / 10 / 1200 / 2 |
| P3 `ringTell` / `ringTicks` / `ringDamage` / `silenceTicks` / `windowTicks` / `windowMultiplier` | 30 / 100 / 4 / 60 / 400 / 2 |
| `wipeResetTicks` / `participantLeaveTicks` | 200 / 100 |
| `clearXp` / `clearShards` / `clearCredits` / `schematicChance` / `schematicPity` / `helpXp` / `firstClearResonance` | 2000 / 3 / 400 / 0.25 / 2 / 300 / 500 |

### `mentor_duel` (`boss.mentor.MentorDuelBalance`, W8)

| Key | Default |
|---|---|
| `damage` / `guard` / `parry` / `strings` / `health` | 9 / 120 / 0.35 / [3, 5] / 300 |
| `leash` / `yieldAt` / `leaveTicks` | 24 / 0.10 / 200 |

### `vendors` and `missions` (W4: no new keys)

The new stocks and contract templates are data (7.1, 7.2). `missions.contractXpCap` stays 900, so contracts pay 900 from level 17.

---

## 17. Architecture and work split

### 17.1 Phase 0 (lead, before workers start)

**Probes.** The three M5 probes ran (`tools/api-probe/m5_{flight,space,combat}.txt`, run 39167972476) and are digested in the scratchpad (`api/m5_{flight,space,combat}_digest.md`). The findings this spec relies on are cited where they are used: the client-authoritative vehicle recipe and its server checks (4.1), the orbit dimension type, the void generator, the planet impostor, the far plane and the weather packets (3.5, 5.1), swept projectiles, Phantom-style flight and the boss bar's missing range logic (5.3, 5.5, 12.4), `isImmobile`, flee goals and registering mob effects (9.2), and the off-hand and attack-range rules (10.2, 10.3).

**Follow-up probe.** One more probe, `m5_followup.txt`, ran before Phase 0 and is digested in `api/m5_followup_digest.md`. It changed four things here: the ship keeps its velocity in its own field, because the client applies motion packets even for an entity it controls (4.1); the orbit type's attribute ids, angles and timeline form (5.1); the server checks a melee hit's range for the main hand only, so the off-hand strike keeps its own payload (10.2); and `LivingEntity#isImmobile` is only `isDeadOrDying()`, so Stasis's head-cancel is safe (9.2). Still open, each with `// API-CHECK` and its fallback (the M4 rule): timeline sampling and modifier maths, the horizon height, `Entity#lerpMotion`, `absSnapRotationTo`, `Player#getWeaponItem`, `LivingEntity#getActiveItem`, `Player#isImmobile`, the off hand's swing transform, and `Ghast$GhastMoveControl`. It covered what the digests marked as recalled rather than seen: the orbit type's visual attribute ids and `timeline` form (`star_brightness`, `sun_angle`, `moon_angle`, `star_angle`, `sunrise_sunset_color`, `timelines`, and `data/minecraft/timeline/day.json`); `FogRenderer#setupFog` and `SkyRenderer#shouldRenderDarkDisc`; how the client treats position and motion packets for an entity it controls (`ClientPacketListener#handleMoveEntity`, `#handleEntityPositionSync`, `#handleSetEntityMotion`, `ServerEntity#sendChanges`); `LocalPlayer#applyInput`, `#canStartSprinting` and `#raycastHitResult`; `ClientLevel#tickNonPassenger`; `ItemInHandRenderer#submitHandsWithItems`; `HumanoidModel#setupAnim`; `AttackRange` and `LivingEntity#getAttackRangeWith`; `LivingEntity#isImmobile` and `#aiStep`; `Mob#isEffectiveAi`; `Phantom$PhantomMoveControl#tick` and `FlyingMoveControl#tick`; `MobEffectCategory` and `FabricMobEffect`; `ServerLevel#getWeatherData`; `ClientPacketListener#handleGameEvent`; `StructurePlaceSettings`.

**Seed survey.** The lead logs, for the playthrough seed, where Saltgate and the holdfast land, the test player's verse stone ring points, the Hymnal's ridge, the debtor camp and the detention post, and checks that the Silent Spindle's point in `orbit_home` is clear (19.3).

**Contracts.** The lead writes the following with real signatures and minimal bodies so every worker compiles against them from day one (the M4 rules: "real code" means the lead writes the whole body; signatures are frozen, so methods can be added, not changed; every enum is stored or synced by ordinal and is append only; "existing" names only what is added to an M1–M4 type).

```java
// existing com.oldrepublic.planet.Planets  [lead: real code]
Planet appends GHESSAR, TYRVANN                                                  // oldrepublic:ghessar, oldrepublic:tyrvann
public static final ResourceKey<Level> GHESSAR, TYRVANN;
public static ResourceKey<Level> orbit(Planet planet);                           // oldrepublic:orbit_<planet id>
public static @Nullable Planet orbitOf(ResourceKey<Level> key);                   // the system whose orbit this is, else null
public static boolean isOrbit(Level level);
public static @Nullable ServerLevel orbitLevel(MinecraftServer s, Planet planet);  // null if the dimension is missing
// isPlanet becomes "any Planet except HOME"; isStoryLevel stays "home world or planet" (an orbit is neither)
// The ~25 "two planets" sites of the survey get a default branch or a per-planet table (3.1); PlanetHubs.template,
// PlanetContracts.planetOf, HubNpcs and the MentorClient accents read tables instead of picking Selvaren for "not Varkhast".

// Placeholder data (Phase 0; owners replace it): ghessar and tyrvann with their own dimension types, the overworld noise
// settings and one vanilla biome each (desert, jungle), for W3; the five orbit_* dimensions with the oldrepublic:orbit
// type, the orbit_void noise settings and the orbit_field biome as in 5.1, for W2.

// com.oldrepublic.planet  [W3 bodies]
public final class PlanetWeather {
	public enum Kind { CLEAR, SANDSTORM }
	public record State(Kind kind, long untilTick) {}                             // global attachment oldrepublic:planet_weather
	public static Kind kind(Level level);                                         // both sides; CLEAR except during a Ghessar storm
}
// existing PlanetSites.Site appends VERSE_STONE_1, VERSE_STONE_2, VERSE_STONE_3, HYMNAL (shared), DEBTOR_CAMP, DETENTION_POST

// existing com.oldrepublic.world.site  [lead]
SiteKind appends CRASHED_FREIGHTER, KAJIDIC_POST, HYMN_SHRINE, MIRE_GRAVE, DEBTOR_CAMP, VERSE_STONE, HYMNAL, DETENTION_POST, DERELICT_STATION
SiteKind.ofTemplate maps hub/saltgate and hub/holdfast to PLANET_HUB; planet/crashed_freighter, planet/kajidic_post, planet/hymn_shrine and
	planet/mire_grave to CRASHED_FREIGHTER, KAJIDIC_POST, HYMN_SHRINE and MIRE_GRAVE; site/debtor_camp, site/verse_stone and site/detention_post
	to their kinds; site/hymnal and site/hymnal_arch to HYMNAL; station/spindle to DERELICT_STATION (RuntimeSites throws for an unmapped template)
// the lead creates the empty structure tags #oldrepublic:crashed_freighters, kajidic_posts, hymn_shrines and mire_graves; W3 fills them
// and adds their cases to StructureLocator.structureTag (a worldgen site is found only through its tag)
SiteAnchors.Anchor appends the new anchor names of 3.2, 3.2.1, 3.3, 3.4, 5.7 and 12.3 (names that exist already, such as HEART, LECTERN,
	DAIS, THRESHOLD, PYLON_1, PYLON_2, HOST, MEDBAY, CONTRACT_TERMINAL and the pad bays, are reused, not added)
SpawnerGroups gains public interface Group { int size(int count, BlockPos pos); List<LivingEntity> spawn(ServerLevel level, BlockPos at,
	int size, boolean bound, @Nullable UUID owner); } and public static void register(String id, Group group) (a duplicate id throws);
	// known, size and spawn fall back to the registered groups; W8 registers its five groups (12.1)

// existing com.oldrepublic.travel  [lead: ShipState, Destination, ShipRecord, TravelData, TravelAttachments, TravelLocks, moveAcross]
ShipState appends FLYING                                                         // flying() includes it
Destination appends SALTGATE(GHESSAR), HOLDFAST(TYRVANN)
ShipRecord.UPGRADE_CANNONS = 1 << 4, UPGRADE_SHIELD = 1 << 5, UPGRADE_THRUSTERS = 1 << 6, UPGRADE_MISSILES = 1 << 7,
	CLASS_INTERCEPTOR = 1 << 18, PART_SPINDLE_CAPACITOR = 1 << 19, PART_HYMN_DRIVE = 1 << 20  // bits 8–17 stay sockets, plates, paint
TravelData gains helm (boolean, optionalFieldOf default false) and withHelm(boolean)
TravelService.Refusal appends NO_HELM, BLOCKADED, NO_ROOM, UNDER_FIRE
public abstract class ShipBody extends Entity {                                  // W1's file; Phase 0 makes CourierShuttle extend it with no behaviour moved
	public abstract @Nullable UUID ownerId();
	public abstract boolean piloted();                                            // the synced DATA_PILOTED flag
	public abstract boolean interceptor();
}
// existing Ships [W1 bodies]
public static boolean hasWeapons(ShipRecord r);                                  // UPGRADE_CANNONS
public static boolean interceptor(ShipRecord r);
public static @Nullable ShipBody ship(MinecraftServer s, UUID owner);             // added; shuttle(...) stays for M4 code and tests and returns the body only when it is a courier
public static void disable(ServerPlayer owner);                                  // 5.4: lock, tumble, then the free rescue to the last landing spot
// client: com.oldrepublic.client.travel.Piloting [W1 body]: isPiloting(), isGunner(), @Nullable ShipBody ship(); the stub returns false and null

// com.oldrepublic.space  [W2 bodies; lead stubs]
public final class ShipCombat {
	public static void hitShip(ServerPlayer owner, float damage, @Nullable Entity source); // shield first, then hull through Ships.update; at 0 calls Ships.disable
	public static boolean underFire(ServerPlayer owner);                          // hit in the last space.underFireTicks
	public static int shield(ServerPlayer owner);
}
public final class SpaceEncounters {
	public static void startBlockade(ServerPlayer owner);                         // W4 calls it from onOrbitEntered during BLOCKADE (6.5)
	public static boolean blockadeRunning(ServerPlayer owner);
}
public final class SpaceMissions {                                               // the runtime of space_run and space_clear (5.9)
	public static boolean start(ServerPlayer p, String missionId, MissionDefinition.Objective kind, Planets.Planet system, boolean ace); // false = refused now
	public static void cancel(ServerPlayer p, String missionId);
}

// existing com.oldrepublic.quest.QuestHooks  [lead: real forwarding; the named owner fires each]
public static void onHelmTaken(ServerPlayer owner);                              // W1
public static void onOrbitEntered(ServerPlayer owner, Planets.Planet system, boolean byJump); // W1
public static void onBlockadeBroken(ServerPlayer owner);                         // W2
public static void onSpaceObjectiveDone(ServerPlayer p, String missionId, boolean success); // W2; W4's mission service completes or fails it
public static void onShipKill(ServerPlayer shooter, Entity fighter, @Nullable UUID encounterOwner); // W2 (faction losses for clan fighters, 5.9)
public static void onVerseSilenced(ServerPlayer owner, int index);               // W3
public static void onRaidCleared(ServerPlayer p, boolean firstClear);            // W8
public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first); // existing; W1 fires it for every rider at every
	// touchdown on a planet (an autopilot pad, a bay glide, a field landing); the lead forwards it to ChapterFourService,
	// ChapterFiveService and TurningService
public static void onFoeKilled(ServerPlayer killer, LivingEntity victim, String kind, @Nullable Faction faction, boolean wasHostile); // W8, for
	// each of its five foes (kind = entity id; faction only for enforcers and warriors); W4 applies KillReputation (the M3 rule, 7),
	// opens the codex pages and counts contract hunts
// QuestHooks forwards to the services through Phase 0 stubs in the owners' files: quest.story.ChapterFiveService (W4) and
// quest.turning.TurningService (W5), one static method per hook with an empty body
// onBossPhase / onBossYielded / onBossDefeated are reused with "ulvaine" only; QuestService's dispatch becomes a lookup by boss id
// (12.6). The raid reports through onRaidCleared and the mentor duels through MentorDuel's onEnd.

// com.oldrepublic.quest.story  [lead: ChapterFive, Turnings, the enums and the StoryData records; W4 and W5 own the services]
public enum ChapterFiveStage { NOT_STARTED, WAITING, CALLED, SALTGATE, THE_FACTOR, ARMED, BLOCKADE, TYRVANN, VERSES, THE_HYMNAL, AFTERMATH, COMPLETE }
public final class ChapterFive {                                                 // real code; reads work on both sides
	public enum Way { KAJIDIC, CLAN, FORCE }
	public static ChapterFiveStage stage(Player p);
	public static boolean atLeast(Player p, ChapterFiveStage stage);
	public static @Nullable Way way(Player p);
	public static boolean clanStands(Player p);                                   // the clan's way, Trusted clan standing, or c5.clan_hired
	public static int verses(Player p);                                           // the silenced mask
	public static @Nullable BossOutcome ulvaineOutcome(Player p);
	public record Deeds(boolean hymnCleared, int echoes, int bounties, int contracts, boolean writ) {}
	public static Deeds deeds(Player p);                                          // 8.5's deeds, from counters W4 and W8 keep
}
public enum TurningKind { NONE, FALL, RETURN }
public enum TurningStage { NONE, OFFERED, TASK, CONFRONTATION, RITE, COMPLETE }
public final class Turnings {                                                    // real code
	public static TurningStage stage(Player p);
	public static TurningKind kind(Player p);
	public static boolean done(Player p, TurningKind kind);
}
// StoryData gains chapter5 (StoryData.Chapter5) and turning (StoryData.Turning), both optionalFieldOf, nested under 16 fields (14.1)
// JournalEntries appends the 6.10 ids and chapter_header.5; CHAPTER_FIRSTS gains THE_FACTOR_ASKS
// QuestEffects: LESSON_CHAPTER_FIVE = 11, LESSON_TURNING = 12, LESSON_RANK_FOUR = 13, LESSON_LEGENDARY = 14, LESSON_LONG_HYMN = 15
// existing client.mentor.MentorClient  [lead, Phase 0]: MILESTONES gains chapter5, turning, rank_four, legendary, long_hymn at 11–15, and
// milestoneSound maps them to the 15.3 sounds (story.chapter_five, story.turning.rite, story.rank_four, bench.legendary, story.lesson_complete)
// existing com.oldrepublic.quest.story.StoryMentors  [lead: real code]
public static void comeTo(ServerPlayer p, MentorRole role, boolean newCamp, Consumer<MentorEntity> onArrive);
	// after story.mentorSummonTicks, at the next safe moment (6.1) in the overworld: spawns the mentor if it no longer exists (the
	// Crossing removed the other path's), places it near the player and calls onArrive; newCamp moves the player's camp there (the
	// rites), false leaves it (the confrontations); no "summoned" message
// onUsed: a mentor whose path is not the story path says turning.oldrepublic.cold.<jedi|sith> instead of not_now once
// Turnings.done(p, FALL) or Turnings.done(p, RETURN)
// existing quest.story.RankThree  [lead]: promote pays rank3Sp and rank3Xp only without the flag c3.rank3_paid (8.4)
// com.oldrepublic.dialogue  [lead: real code; Dialogues.open asks it first]
public final class DialogueRedirects { public static void register(String dialogueId, BiFunction<ServerPlayer, String, String> redirect); }
	// W5's Ansa and Kaddor (8.3, 6.11)

// existing com.oldrepublic.identity and combat  [lead]
Faction appends VESSBA_KAJIDIC, CLAN_HASK; every switch over Faction outside the lead's files gets a default branch, and the Standing
	panel, vendor refusals, attitude rule and mission issuers read Faction.values()
Allegiance appends KAJIDIC, CLAN, HYMN
Allegiances.friendly(a, b) is false while either entity is confused: Allegiances.setConfusedTest(Predicate<Entity>) (W6 supplies it, 9.8)
DangerTellTable gains the 15.3 rows (scour 1, 4; hymnsworn 1; stormback 1, 3; hask_warrior 0, 3; ulvaine 1, 4, 6, 7; first_cantor 1, 3, 6, 9, 11)

// existing com.oldrepublic.force, skill and the client  [lead]
ControlEffects.FEAR = "fear", STASIS = "stasis", PACIFY = "pacify"              // W6 owns the runtime (9.2)
SkillData.SLOT_COUNT = 8                                                         // the codec pads and truncates to it
SkillData gains openSlots2 (int, `open_slots_2`, optionalFieldOf default 0)      // the second bar's open slots: 0, 2 or 4 (9.1)
AbilityInputPayload and AbilityStatePayload carry slots 0–7; Force Sense keeps its slot 8
ClientConfig gains spaceSky (true), invertShipPitch (false), stormFlashes (true) and their /oldrepublicfx toggles (14.4)
// client.quest.JournalScreen.buildLeftPage gains a first branch for Chapter Five at CALLED or later (6.10)

// existing com.oldrepublic.saber, crafting and mission  [lead]
SaberForm appends MAKASHI("form_makashi"), DJEM_SO("form_djem_so"), JAR_KAI("form_jar_kai"); Phase 0 gives them placeholder cases in
	// SaberForm.modifiers (Unformed's modifiers); W7 replaces them
HiltParts: Emitter appends CANTOR, Grip appends SUNFORGED, Pommel appends HASK; SaberLens.Lens appends STORMGLASS, with placeholder
	// cases in SaberLens.Lens.value (an empty array) and SaberItems.lensItem; W7 replaces them
SaberConfig gains length (int 0–2, optionalFieldOf default 0; the five- and seven-argument constructors stay, and the stream codec
	writes it after lens)
CrystalAttunement gains tuning (String, optionalFieldOf default ""; the three-argument constructor stays)
Quality appends LEGENDARY (1.18, 1.40, 1.75; its built-in arrays gain the fourth value; configure accepts three or four values,
	// and three set the first three tiers and keep Legendary's default); M4Components.QUALITY and SaberComponents.LENS_QUALITY accept 0–3
CraftingData gains legendaryMisses (Map<String, Integer>, `legendary_misses`, optionalFieldOf, empty)   // 11.4: misses per legendary schematic id
MissionDefinition.Objective appends SPACE_RUN, SPACE_CLEAR

// stubs in worker packages (each worker owns the file and replaces the body)
public final class MentorDuel { public enum Kind { SAELEN, SORN } public enum Result { YIELDED, RESET }   // boss.mentor, W8
	public static void start(ServerPlayer p, Entity mentor, Kind kind, Consumer<Result> onEnd); }
public final class UlvaineFight { public static void resolve(ServerPlayer owner, BossOutcome outcome); }  // boss.ulvaine, W8
public final class RaidData { public long lastClearWeek(); public int pity(); public int clears(); public long echoDay(); public int echoes(); }
	// boss.raid, W8; attachment oldrepublic:raid
public final class LegendaryDrops { public static boolean roll(ServerPlayer p, Identifier schematic, float chance, int pity); } // crafting, W7;
	// the lead's stub returns false
public final class BossMusicService { public static void register(String bossId, EntityType<?> boss, List<SoundEvent> tracks); } // client.boss.music, W8

// com.oldrepublic.registry  [lead: real code]
public final class M5Items { /* hymnstone_shard, sunstone, stormglass_shard, clan_iron, concussion_missile, sealed_cargo */ }
public final class M5Contracts { public static void init(); }                     // called from OldRepublicMod after M4Contracts

// Entry points, one per worker (each worker owns its own; Phase 0 writes them empty and wires them):
//   W1 travel.FlightFeature, client.travel.FlightClient (mixins: oldrepublic-travel)
//   W2 space.SpaceFeature, client.space.SpaceClient, oldrepublic-space.mixins.json (mixin.space, new)
//   W3 planet.PlanetM5Feature, client.planet.PlanetM5Client (mixins: oldrepublic-planet, oldrepublic-world)
//   W4 quest.story.ChapterFiveFeature, client.quest.ChapterFiveClient (mixins: oldrepublic-quest, oldrepublic-identity)
//   W5 quest.turning.TurningFeature, client.quest.turning.TurningClient (no mixins; the lead adds one on request)
//   W6 force.ForceM5Feature, client.force.ForceM5Client (mixins: oldrepublic-force, oldrepublic-skill)
//   W7 saber.SaberM5Feature, client.saber.SaberM5Client (mixins: oldrepublic-saber, oldrepublic-crafting, oldrepublic-equipment)
//   W8 enemy.FoesM5Feature, client.enemy.FoesM5Client (mixins: oldrepublic-foes, oldrepublic-boss)
// M5ClientTest runs the m5/{Flight,Space,Planet,Story,Turning,Force,Saber,Foes}ClientSteps stubs (19.2).
```

**Registries.** `registry/M5Items` (the lead's) holds only the plain items more than one worker names: `hymnstone_shard`, `sunstone`, `stormglass_shard`, `clan_iron`, `concussion_missile` and `sealed_cargo`. Every item that needs its own class, every block and every entity type is registered by its owner (18.1). Data loaders keep the M4 rule: an entry whose item id is unknown is skipped with a warning, so each worker's part loads before the others are committed.

**Process files** (written in Phase 0): `docs/WORKER_M5.md` (the M4 worker rules, plus: a piloted ship is moved only by its pilot's client or by `TravelService.moveAcross` and the server moves of 4.1; nothing in an orbit uses ground dust (15); every new `switch` over `Planet` or `Faction` has a default branch; the client never trusts a payload's numbers for damage, heat or cooldowns), `tools/ownership_m5.json` (17.2), `tools/check_m5_ids.py` (section 18's ids against registrations, lang, sounds and `events_m5.json`; its id lists change only together with section 18), `tools/assets/sounds/events_m5.json` (15.4), the `ContractsM5GameTests` class (19.1) and the `M5ClientTest` step stubs. CI's `checks` job runs the M5 checks beside the M3 and M4 ones.

**Phase 0 code changes to existing systems** (3.1, 7, 9.1, 12.6): the planet sweep and the faction sweep, the `SkillData` slot count and the two ability payloads, the boss dispatch by id, `DialogueRedirects`, the `ClientConfig` switches and the `JournalScreen` Chapter Five branch, the `CourierShuttle extends ShipBody` change, the codec fields of 14.1, and the `DangerTellTable` rows. M1–M4 game tests and the playthrough must stay green after Phase 0.

### 17.2 Workers and ownership

Eight workers. **A file has exactly one owner.** Shared files (lead) change only through requests in a worker's report.

| Worker | Owns (code) | Owns (data, assets, docs) | Delivers |
|---|---|---|---|
| **W1 flight and ships** | `com.oldrepublic.travel.**` except the lead's contract files (`ShipState`, `Destination`, `ShipRecord`, `TravelData`, `TravelAttachments`, `TravelLocks`; `TravelService` is W1's file, but its `moveAcross` is the lead's frozen code); `client/travel/**`; `mixin/travel/**` and `oldrepublic-travel.mixins.json` | the interceptor model and texture (`gen_textures_ship.py` additions), the gunner turret part; `sounds/flight.py`; lang `travel.json` additions; particle `atmosphere_streak`; `docs/design/flight.md` | The spike first (4.1); `ShipBody` and the interceptor (4.9); the helm, controls, flight model, landing, sky and orbit transitions, jumps from orbit and the edge (4.2–4.7); the M5 upgrades and the refit as dialogue actions (4.8, 4.9); `Ships.disable` and the rescue (5.4); the Hymn-Tuned Drive's effect (11.2); leaving mid-flight (4.10); the flight HUD, camera and engine sounds (4.11); `Piloting`; the ship screen changes; conditions `ship:weapons`, actions `ship:fit` and `ship:refit` |
| **W2 space** | `com.oldrepublic.space.**` (new); `client/space/**`; `mixin/space/**` and `oldrepublic-space.mixins.json` | the orbit dimension type, the five orbit dimensions, `orbit_void`, `orbit_field`, the `asteroid` feature; `tools/assets/gen_structures_space.py`, the `station/spindle` template and sidecar; `gen_textures_space.py` (fighters, gunship, bolts, missile, buoy, the planet impostors, the nebula); `sounds/space.py`; lang `space.json` (new); loot `gameplay/space_bounty`, `chests/spindle_vault`; the assets of `concussion_missile` and `sealed_cargo`; damage type `ship_weapon`; particle `hull_debris`; tag `#oldrepublic:ship_targets`; `docs/design/space.md` | Orbits and how they look (5.1), the gunner seat's weapon (5.2), weapons, heat, missiles and the two projectiles (5.3), shields and hull damage (5.4), the four fighters and the generator (5.5), patrols, the blockade and co-op (5.6), the Silent Spindle (5.7), the Spindle Capacitor's effect (11.2), the bounty fight and space objectives' runtime (5.8, 5.9; W4 pays the bounty), the combat HUD (5.10), the client rain rule in orbits (3.5) |
| **W3 worlds** | `com.oldrepublic.planet.**` except the lead's `Planets` and `Clocks`; `com.oldrepublic.world.**` except the lead's `SiteKind`, `SiteAnchors` and `SpawnerGroups`; `client/{planet,world}/**`; `mixin/{planet,world}/**` and their configs | Ghessar and Tyrvann: dimensions, types, noise settings, biomes, features, structures and sets; `tools/assets/gen_structures_m5.py` and its sidecars (hubs, sites, worldgen pieces, the Hymnal, the detention post); `gen_textures_planet.py` additions; blocks `salt_crust`, `sunstone_ore`, `stormglass_cluster`, `peat`, `verse_stone`, `holdfast_gate` and their loot; the assets of `sunstone` and `stormglass_shard`; `sounds/world_m5.py`; lang `planet.json` additions; loot `chests/{crashed_freighter,kajidic_post,hymn_shrine,mire_grave}`; particles `sand_gust`, `verse_mote`; tags `is_ghessar`, `is_tyrvann` and the `is_planet` additions; `docs/design/planets_m5.md` | Section 3: both planets, the hubs' templates and placement, the sites, the verse stone block, the Hymnal, the detention post, per-dimension weather and sandstorms, ridge lightning, the twin sun, ambience; the planet tables of the Phase 0 sweep that W3's code reads |
| **W4 story and factions** | `com.oldrepublic.quest.**` except the lead's `QuestHooks`, `Guidance`, `quest/story/{ChapterFour,ChapterFive,Turnings}`, the M5 stage enums and the `StoryData` records, and except `quest/turning/**` (W5); `com.oldrepublic.mission.**`, `dialogue.**`, `mentor.**`; `identity.**` except the lead's `Faction`; `vendor.**` except `VendorEntry`; `client/{quest,mission,identity,dialogue,mentor,vendor}/**` except `client/quest/turning/**` and `client/quest/JournalTabs`; `mixin/{quest,identity}/**` | lang `quest.json`, `mission.json`, `identity.json`, `vendor.json` additions; every M5 dialogue except W5's `turn_*` and `rank4_*`; `data/oldrepublic/mission/*.json` and `vendor/*.json` additions; the new hub NPCs (Vessba as her own entity `vessba`, the rest as `hub_host` and `vendor` variants) with skins and portraits (`gen_textures_hub.py` and `gen_textures_quest.py` additions); `sounds/story_m5.py`; loot `gameplay/**` additions except `space_bounty` (W2) and `long_hymn` (W8); the assets of `ulvaines_writ`; the banners of `lesson_complete` 11–15; `docs/design/chapter_five.md` | Chapter Five (6), `ChapterFiveService`, both factions' standing, vendors and contracts (7), the Standing panel's new rows and the rank 4 checklist view (W5 supplies the data), the journal, the 6.11 conditions and actions it owns, the `verse_vision` effect, the counters behind `ChapterFive.deeds` |
| **W5 paths and ranks** | `com.oldrepublic.quest.turning.**` (new); `client/quest/turning/**` | dialogues `turn_*` and `rank4_*`; lang `turning.json` (new); the `watch_holocron` item and its assets (`gen_textures_turning.py`, new); `sounds/turning.py`; the `turning` and `rank_four` effects; `docs/design/turning.md` | Section 8: the offers, both questlines and their costs, the cold mentors' lines and Ansa's and Kaddor's redirects (`DialogueRedirects`, 17.1), the rank 4 rules, the checklist payload and the three ceremonies (granting the robes W7 makes and the powers W6 makes); the 6.11 conditions and actions it owns; the escort and delivery runtime of its tasks |
| **W6 powers** | `com.oldrepublic.force.**` M5 additions (`force/{stasis,valor,drain,fear,rage,mindtrick}/**`, `force/ForceMobEffects`, `force/ForceM5Feature`), the fear migration in `force/choke/**`, and the runtime behind the lead's `ControlEffects` constants; `com.oldrepublic.skill.**` except the lead's `SkillData`; `com.oldrepublic.ability.**`; `client/force/**` (including `PowerAnimations` and `ForceKeys`), `client/skill/**`, `client/hud/ForceHud` and `client/ability/**`; `mixin/{force,skill}/**` and their configs | `skill_tree/**` (all of it in M5); lang `force.json`, `skill.json` additions; mob effect icons and node icons (`gen_textures_force.py` and `gen_textures_skill.py` additions); `sounds/force_m5.py`; particles `stasis_rime`, `drain_mote`, `valor_dust`; damage type `force_drain`; tags `#oldrepublic:{fearless,strong_willed,mind_trickable,force_resistant}`; `docs/design/powers_m5.md` | Section 9: the second bar and Focus, the control kinds and status effects, the six powers with branches, mastery, poses, effects and sounds, the `wayfarers_stride` passive, the conditions and actions `ability:knows`, `mind_trick:use` and `fear:use`; the form nodes in `saber.json` on W7's request |
| **W7 sabers, crafting and gear** | `com.oldrepublic.saber.**` except `saber/duel/**` (W8) and the lead's `SaberComponents`; `com.oldrepublic.crafting.**` except the lead's contract files (`Discipline`, `Quality`, `Schematic`, `Schematics`, `CraftingData`, `CraftingAttachments`); `com.oldrepublic.equipment.**` except the lead's `ModSlot` and `FittedMods`; `client/{saber,crafting,equipment}/**`; `mixin/{saber,crafting,equipment}/**` and their configs | `data/oldrepublic/schematic/*.json` additions; lang `saber.json`, `crafting.json`, `equipment.json` additions; `gen_textures_saber.py`, `gen_textures_crafting.py`, `gen_textures_equipment.py` additions (the legendary parts and mods, the robes, the mask, `hymnstone_shard`, `clan_iron`); `sounds/saber_m5.py`; `docs/design/sabers_m5.md` | Section 10 (forms, dual-wielding, blade length, tuning, hum profiles) and section 11 (the Legendary tier, the ten components, materials' recipes and `LegendaryDrops`, 11.4); the robes `jedi_master_robe`, `sith_darth_robe`, `wayfarer_mantle` and the `cantors_mask` (8.5, 12.3) |
| **W8 foes and bosses** | `com.oldrepublic.enemy.{cartel,hask,hymn,scour,stormback}.**` and `enemy/FoesM5Feature`; `com.oldrepublic.boss.{ulvaine,raid,mentor,cantor}.**`; `com.oldrepublic.saber.duel.**`; `client/enemy/{cartel,hask,hymn,scour,stormback}/**`, `client/enemy/FoesM5Client`, `client/boss/{ulvaine,cantor,music}/**`; the M3 and M4 boss music files it replaces (12.5); `mixin/{foes,boss}/**` and their configs | lang `enemy.json`, `boss.json` additions; `gen_textures_foes.py` and `gen_textures_boss.py` additions; `sounds/enemy_m5.py`; loot `entities/<W8 entities>`; damage types `storm_call`, `hymn`; particles `sand_burst`, `spectral_wisp`; the `hologram` effect; loot `gameplay/long_hymn` (the raid's rewards); spawn eggs; `docs/design/foes_m5.md` | Section 12: the five foes and their natural spawns and spawner groups, the mentor duellist, Ulvaine and her echo, the Long Hymn raid and `RaidSession`, the boss music service |

**Shared and inherited files:**

| Files | Owner | Notes |
|---|---|---|
| `registry/{M5Items,M5Contracts}`, the M4 shared files (`registry/{M4Items,M4Components,M4Contracts}`, `combat/**`, `economy/**`, `planet/{Planets,Clocks}`, the travel contract files and `moveAcross`, `quest/{QuestHooks,Guidance}`, `quest/story/{ChapterFour,ChapterFive,Turnings}` and the stage enums, the crafting contract files, `equipment/mod/{ModSlot,FittedMods}`, `vendor/VendorEntry`, `client/quest/JournalTabs`), `world/site/{SiteKind,SiteAnchors,SpawnerGroups}`, `identity/Faction`, `skill/SkillData`, `saber/SaberComponents`, `registry/ModComponents` | lead | the contract files inside worker packages are named in each worker's `except` list |
| M1–M4 files of packages no M5 worker continues: `enemy/{BlasterBolt,trooper,construct,mandalorian,droid,creature}/**`, `boss/` (the Collector) and `boss/kaast/**`, the M1–M3 holocron, network and config code | lead | changed on a worker's request |
| `data/minecraft/tags/**`, `data/oldrepublic/tags/**` new tag files | lead creates; owners fill their own tags by request | |
| `src/gametest/resources/**` | lead | workers request fixtures |
| `src/gametest/java/.../m5/<Feature>ClientSteps.java` | the feature's worker | stubs by the lead |
| Test classes | `ContractsM5GameTests` and the client drivers lead; `FlightGameTests` W1, `SpaceGameTests` W2, `PlanetM5GameTests` W3, `StoryM5GameTests` W4, `TurningGameTests` W5, `ForceM5GameTests` W6, `SaberM5GameTests` W7, `FoesM5GameTests` W8 | M1–M4 classes stay with their M4 owners' successors: the worker who owns the package |
| `tools/assets/gen_structures.py`, `gen_sounds.py`, `tools/check_*.py`, `tools/assets/sounds/events_m5.json` | lead | |
| Balance sections | the section's owner (16.4) | |

Notes on boundaries:

- **The ship in combat:** W1 owns the ship, its flight, its hull number, `Ships.disable` and the rescue; W2 owns everything that shoots, the shield number and when a hit lands (`ShipCombat.hitShip`). W2 reads the pilot through `Piloting` on the client and `Ships` on the server.
- **The turret:** W1 draws it on the ship's model and seats the gunner; W2 aims and fires it.
- **The shipwright:** W4 writes the dialogue and the vendor; W1 owns the `ship:*` actions and conditions; W7 makes the legendary ship parts the shipwright fits (11.2).
- **Hubs:** W3 places the hubs and their anchors; W4 spawns and owns everything that talks or sells there; W1 owns the pads as landing spots.
- **Verse stones:** W3 owns the block, its channel and its sites; W8 owns the guards (`hymnsworn` spawner group); W4 owns the story around them.
- **The Hymnal:** W3 builds the template and anchors; W8 runs the fights inside it; W4 owns the scenes, the choice and every Chapter Five reward; W8 owns the raid's and the echo's rewards.
- **Story-granted powers:** W5 calls `Skills.grant` for Valor, Rage and `wayfarers_stride` at the ceremonies and for the new path's signature powers at a rite; W6 implements them.
- **Hologram:** W4's dialogue action sends the `hologram` event; W8 draws it with Ulvaine's model.
- **Stubs while parallel:** W4 needs `SpaceEncounters`, `SpaceMissions`, `UlvaineFight`, `PlanetSites` (the new sites) and `Ships`; W5 needs `MentorDuel`, `PlanetSites.DETENTION_POST`, `DialogueRedirects`, `StoryMentors.comeTo` and `Skills.grant`; W2 needs `Piloting`, `ShipBody` and `Ships`; W8 needs the Hymnal's anchors (stub: `null`, so W8 tests its fights on fixtures placed with `RuntimeSites.placeAt`, as M4's Kaast did). The Phase 0 stubs return safe values (`null` for a site or body that is not there yet, false, an empty list, or a refusal). Every worker tests with commands until integration.

### 17.3 Integration order (lead)

1. Phase 0 committed and compiling, `ContractsM5GameTests` and all M1–M4 tests green, the follow-up probe read, the seed survey logged, `WORKER_M5.md` written.
2. Workers deliver in parallel in **one working tree**, with no per-worker branches. Workers never commit; the lead reviews each worker's files and commits them, and CI builds the lead's branch. `check_ownership.py` with `ownership_m5.json` reports any file a worker changed outside its globs.
3. **W1 delivers the spike first** (4.1), before the rest of its section; the lead runs `FlightClientTest` on CI and records pass or fallback B in `DECISIONS.md`.
4. Review and commit order **W3 → W1 → W2 → W7 → W6 → W8 → W5 → W4**: the worlds first, then the ship that reaches them and the space around them, the gear, the powers and the foes that test them, the paths, and the story last. Each part loads without the later ones (the M4 rule).
5. Fix compile errors on CI; send logic issues back.
6. Run every server and client test (`M5ClientTest` runs the eight `m5/*ClientSteps`); re-run the M1–M4 playthrough and confirm it stays green.
7. PlaythroughTest extension (19.3).
8. Update the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `VFX_SFX_SPEC`, `AUDIO_BRIEF`, `DECISIONS`), including any cuts from section 1.

---

## 18. Shared identifiers

All ids are in the `oldrepublic` namespace. "Existing, reused" ids come from M1–M4: they are not registered again, get no new lang key and are not in `tools/assets/sounds/events_m5.json` (`tools/check_m5_ids.py` enforces this, and its id lists change only together with this section). In 18.1 the owner is the worker who writes the id's lang, models and textures; the plain items several workers name are registered in the lead's `registry/M5Items` (17.1), and every other id by its owner.

### 18.1 Items, blocks, entities, data

| Kind | Ids | Owner |
|---|---|---|
| Planet blocks | `salt_crust`, `sunstone_ore`, `stormglass_cluster`, `peat`, `verse_stone` (with its block entity), `holdfast_gate` | W3 |
| Planet items | `sunstone`, `stormglass_shard` (both in `M5Items`); the block items of `salt_crust`, `sunstone_ore`, `stormglass_cluster` and `peat`; `verse_stone` and `holdfast_gate` are registered with `ModRegistry.blockOnly` | W3 |
| Materials | `hymnstone_shard`, `clan_iron` (both in `M5Items`) | W7 |
| Space items | `concussion_missile`, `sealed_cargo` (both in `M5Items`) | W2 |
| Ship | `interceptor` (entity) | W1 |
| Space entities | `hymn_interceptor`, `kajidic_raider`, `hask_fighter`, `blockade_gunship`, `gunship_generator`, `ship_bolt`, `ship_missile`, `nav_buoy` | W2 |
| Space spawn eggs | `hymn_interceptor_spawn_egg`, `kajidic_raider_spawn_egg`, `hask_fighter_spawn_egg` | W2 |
| Legendary saber parts | `emitter_cantor`, `grip_sunforged`, `pommel_hask` (the `<part>_<id>` convention of `HiltParts`), `lens_stormglass` | W7 |
| Legendary mods and ship parts | `mod_clan_iron_plating`, `mod_hymnweave_lining`, `mod_storm_cell`, `mod_sunstone_edge` (the M4 `mod_<name>` convention); `spindle_capacitor`, `hymn_tuned_drive` | W7 |
| Robes and the mask | `jedi_master_robe`, `sith_darth_robe`, `wayfarer_mantle`, `cantors_mask` | W7 |
| Keepsakes | `watch_holocron` (W5), `ulvaines_writ` (W4) | W5, W4 |
| Foes | `cartel_enforcer`, `hask_warrior`, `hymnsworn`, `sand_scour`, `stormback` (variants by data); their spawn eggs `<id>_spawn_egg` | W8 |
| Bosses | `darth_ulvaine`, `first_cantor`, `cantor_choir`, `hymn_resonator` (Ulvaine's Crescendo spires) | W8 |
| Hub NPCs | `vessba` (her own entity); `hub_host` variants `harbourmaster`, `teel`, `dask`, `varra`, `dorrun`, `doorman`, `bartender`, `debtor`, `warden` (W5's dialogue); `vendor` variants by stock id | W4 |
| Data (planets) | dimensions `ghessar`, `tyrvann`, with dimension types and noise settings of the same names; biomes `ghessar_dunes`, `ghessar_saltflat`, `ghessar_canyons`, `tyrvann_stormwood`, `tyrvann_mire`, `tyrvann_basalt`; structures `crashed_freighter`, `kajidic_post`, `hymn_shrine`, `mire_grave`, with structure sets of the plural names | W3 |
| Data (space) | dimensions `orbit_home`, `orbit_selvaren`, `orbit_varkhast`, `orbit_ghessar`, `orbit_tyrvann`; dimension type `orbit`; noise settings `orbit_void`; biome `orbit_field`; feature `asteroid` | W2 |
| Templates | `hub/saltgate`, `hub/holdfast`, `site/verse_stone`, `site/hymnal_arch`, `site/hymnal`, `site/debtor_camp`, `site/detention_post`, `planet/crashed_freighter`, `planet/kajidic_post`, `planet/hymn_shrine`, `planet/mire_grave` (W3); `station/spindle` (W2) | W3, W2 |
| Schematics (`data/oldrepublic/schematic/`) | `concussion_missile` (Armstech 3); the ten `<component>_legendary` schematics of 11.2 (the two ship parts' with the `common` discipline) | W7 |
| Vendor stock | `cartel_fence`, `saltgate_shipwright`, `hask_armourer` | W4 |
| Missions | `c5_debt`, `c5_cargo`, `c5_matriarch`, `c5_honour`, `c5_counting_house`; contracts `ghessar_{collect,recover,cull,escort,run,bounty}`, `tyrvann_{hunt,spar,cleanse,recover,patrol,bounty}` | W4 |
| Loot tables | `gameplay/space_bounty`, `chests/spindle_vault` (W2); `chests/{crashed_freighter,kajidic_post,hymn_shrine,mire_grave}` and the W3 block tables (W3); `gameplay/long_hymn` and `entities/<W8 entities>` (W8); `gameplay/<W4 rewards>` (W4) | as listed |
| Damage types | `ship_weapon` (W2), `force_drain` (W6), `storm_call`, `hymn` (W8) | as listed |
| Status effects | `stasis`, `fear`, `pacified`, `valor`, `rage`, `drained` | W6 |
| Tags | entity: `#oldrepublic:ship_targets` (W2); `#oldrepublic:{fearless,strong_willed,mind_trickable,force_resistant}` (W6); `#c:bosses` and `#oldrepublic:push_resistant` additions (W8, by request); biome: `is_ghessar`, `is_tyrvann`, the `is_planet` additions (W3) | the lead creates each new tag file in Phase 0; the named owner fills it, and other workers add their entities by request |
| Particles | 15.2 | as listed |
| Skill nodes | `force_stasis`, `stasis_field`, `stasis_lock`, `force_valor`, `valor_shared`, `valor_steadfast`, `force_drain`, `drain_life`, `drain_essence`, `force_fear`, `fear_terror`, `fear_dread`, `force_rage`, `rage_unbound`, `rage_cold`, `force_mind_trick`, `mind_trick_confuse`, `mind_trick_mass`, `wayfarers_stride`; forms `form_makashi`, `form_djem_so`, `form_jar_kai` | W6 (the forms' effects W7) |
| Keys | `key.oldrepublic.helm` (W1), `key.oldrepublic.focus` (W6) | as listed |

### 18.2 Payloads

| Id | Direction | Owner |
|---|---|---|
| `ship_action` gains buttons `TAKE_HELM`, `LAND`, `RETURN_TO_LANDING`, `HAND_TO_AUTOPILOT`, `JUMP_ORBIT`, `OPEN_SCREEN`; `TAKE_HELM`, `LAND` and `OPEN_SCREEN` are accepted without an open ship screen when the sender is the owner riding their own body | C2S | W1 |
| `flight_boost` (pressed) | C2S | W1 (the server tracks the meter) |
| `flight_status` (boost meter, landing check, transition cooldown, edge distance) | S2C | W1 |
| `flight_motion` (velocity x, y, z; yaw; whether to turn the pilot's look) | S2C | W1 |
| `ship_fire` (weapon, pressed) | C2S | W2 |
| `ship_combat_status` (shield, heat per weapon, missiles, lock, scan meter) | S2C | W2 |
| `space_encounter` (kind, objective point, comm line keys) | S2C | W2 |
| `planet_weather` (kind, until tick) | S2C | W3 |
| `rank_four_checklist` (each requirement's state) | S2C | W5 |
| `offhand_strike` (target id) | C2S | W7 |
| `boss_music` (boss id, track; 0 stops) | S2C | W8 |
| `ability_input` and `ability_state` carry slots 0–7 | both | lead (Phase 0), W6 |

### 18.3 Effect events

The full look-and-sound table is 15.3. By owner: W1 `ship_flight`, `atmosphere`, `ship_refit`, `ship_disabled`; W2 `ship_weapon`, `ship_hit`, `ship_shield`, `missile_lock`, `ship_destroyed`, `gunship`, `space_comm`, `space_scan`, `nav_buoy`; W3 `sandstorm`, `verse_stone`, `stormglass`, `site_door`; W4 `verse_vision`, `lesson_complete` 11–15 (banners); W5 `turning`, `rank_four`; W6 `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick`; W7 `offhand_strike`, `crystal_tune`, `bench_craft` 3; W8 `hologram`, `scour`, `hymnsworn`, `stormback`, `hask_warrior`, `ulvaine`, `first_cantor`.

### 18.4 Sounds

Every sound named in 15.3 and 15.4. Placeholders are generated for all of them; `AUDIO_BRIEF.md` gets an M5 section with a brief per sound. Reused, not new: `workbench.tune`, `blaster.impact`, `story.lesson_complete`, `story.duel_begin`, `story.duel_end`, `saber.form_change`, `force.lightning.release`.

### 18.5 Lang parts

| Part | Prefixes |
|---|---|
| `travel.json` (W1) | `entity.oldrepublic.interceptor`, `ship.oldrepublic.*` (M5 lines), `screen.oldrepublic.ship.*` (M5 lines), `destination.oldrepublic.{saltgate,holdfast}`, `hud.oldrepublic.flight.*`, `key.oldrepublic.helm` |
| `space.json` (W2, new) | `entity.oldrepublic.<W2 entities>`, `item.oldrepublic.{concussion_missile,sealed_cargo,<W2 eggs>}`, `space.oldrepublic.*`, `hud.oldrepublic.combat.*`, `bounty.oldrepublic.ace.*`, `biome.oldrepublic.orbit_field`, `dimension.oldrepublic.orbit_*`, `death.attack.oldrepublic.ship_weapon*` |
| `planet.json` (W3) | `block.oldrepublic.<W3 blocks>`, `item.oldrepublic.{sunstone,stormglass_shard}`, `biome.oldrepublic.<the six>`, `dimension.oldrepublic.{ghessar,tyrvann}`, `site.oldrepublic.<M5 sites>`, `weather.oldrepublic.*` |
| `quest.json`, `mission.json`, `identity.json`, `vendor.json` (W4) | `journal.oldrepublic.entry.<6.10>`, `journal.oldrepublic.codex.<6.10>`, `quest.oldrepublic.c5.*`, `dialogue.oldrepublic.{c5_*,vendor_*,spindle_log}`, `npc.oldrepublic.<M5 hub NPCs>`, `entity.oldrepublic.vessba`, `mission.oldrepublic.{c5_*,ghessar_*,tyrvann_*}`, `faction.oldrepublic.{vessba_kajidic,clan_hask}`, `item.oldrepublic.ulvaines_writ`, `quest.oldrepublic.c2.banner.{chapter5,turning,rank_four,legendary,long_hymn}`, each with `.jedi`, `.sith`, `.independent` and `.none`, `quest.oldrepublic.c2.banner.planet.{ghessar,tyrvann}` and their `.sub`, `journal.oldrepublic.chapter_header.5` |
| `turning.json` (W5, new) | `turning.oldrepublic.*`, `dialogue.oldrepublic.{turn_*,rank4_*}`, `title.oldrepublic.wayfarer`, `rank4.oldrepublic.darth_name.*`, `item.oldrepublic.watch_holocron` (the rank names `rank.oldrepublic.{jedi,sith}.4`, "Master" and "Darth", exist in `identity.json`) |
| `force.json`, `skill.json` (W6) | `ability.oldrepublic.<the six powers>.*`, `skill.oldrepublic.node.<18.1>.*`, `effect.oldrepublic.<the six effects>`, `key.oldrepublic.focus`, `death.attack.oldrepublic.force_drain*` |
| `saber.json`, `crafting.json`, `equipment.json` (W7) | `saber.oldrepublic.{form,length,tuning,hum}.*`, `item.oldrepublic.<W7 items>`, `quality.oldrepublic.legendary`, `screen.oldrepublic.workbench.{tune,length}.*` |
| `enemy.json`, `boss.json` (W8) | `entity.oldrepublic.<W8 entities>`, `item.oldrepublic.<W8 eggs>`, `boss.oldrepublic.{ulvaine,cantor,mentor}.*`, `death.attack.oldrepublic.{storm_call,hymn}*` |
| `sounds.json` (lead) | `subtitles.oldrepublic.*` (generated) |

---

## 19. Test plan

Distinguish compile, automated checks and manual play, as in M1–M4. Nothing counts as "played" until a person plays it. Every game test follows the M4 test rules (`WORKER_M5.md`): an explicit `maxTicks` (≤ 400 for server tests), small fixtures (≤ 7 high), listed mock players, explicit days, no global state, and whole templates, worldgen and real dimension travel only in client tests. The game-test server has no planet or orbit dimensions, so server tests fly in the overworld's sky (the home world is a planet for flight, 4.4) and test every cross-level rule on detached data.

### 19.1 Server game tests (`./gradlew build`; each worker writes their own class, the lead registers it)

| Class | Owner | Tests |
|---|---|---|
| `ContractsM5GameTests` (green before workers start) | lead | `planetAppendsKeepOrdinals`; `orbitKeysRoundTrip`; `isPlanetCoversNewWorldsAndNotOrbits`; `everyPlanetHasTableRows` (hub template, music, ambience, contract planet); `destinationAppendsMapToPlanets`; `flyingCountsAsFlying`; `upgradeBitsAvoidSocketsPlatesAndPaint`; `travelDataWithoutHelmDecodes`; `storyDataWithoutChapter5OrTurningDecodes`; `journalAppendsAfterChapterFour`; `factionAppendsAndStandingRowsFollowValues`; `allegianceAppends`; `controlKindsDistinct`; `skillDataPadsOldSavesToEightSlots`; `skillDataWithoutSecondBarDecodes`; `abilityPayloadsCarrySlotsZeroToSeven`; `saberConfigWithoutLengthDecodes`; `crystalAttunementWithoutTuningDecodes`; `qualityAcceptsLegendary`; `qualityKeepsThreeValueArrays`; `lensQualityAcceptsLegendary`; `missionObjectivesAppend`; `bossDispatchById`; `dialogueRedirectApplies`; `spawnerGroupsRegister`; `dangerTellTableMatches15_3`; `everyM5TemplateMapsToASiteKind` (every template path in 18.1 gives a non-null `SiteKind.ofTemplate`) |
| `FlightGameTests` | W1 | `helmRefusals` (not owner, `NO_HELM`, combat lock, state); `takeHelmSetsFlyingAndPiloted`; `controllingPassengerOnlyWhilePiloted`; `noServerMoveOfPilotedShip`; `speedNeverAboveCap`; `impactHullFromSpeedLost`; `boostMeterServerSide`; `landingChecksFootprintLevelSpeedHeight`; `landingInHubGlidesToBay`; `fieldLandingKeepsPositionAcrossRestart` (registry rules); `riderLeavingIsPutDownAtLastLanding`; `pilotLeavingReturnsShip`; `restartWhileFlyingReturnsToLastLanding`; `transitionCooldownBothWays`; `orbitMappingTimesEight` (pure math); `orbitEdgeTurnsBack` (rules); `jumpFromOrbitCostsFuelAndRefuses` (fuel, hull, `UNDER_FIRE`, `BLOCKADED`); `refitNeedsSaltgateLandedEmptyAndHoldOfNine`; `rescueKeepsRidersAcrossLevels` (detached data: the body and its riders reach the landing spot's level together); `jumpFromOrbitKeepsBodyAndRiders` (the body's level differs from the record's); `orbitArrivalClampedInsideTheEdge`; `refitKeepsCodeFuelUpgradesAndOneCrew`; `upgradesFitOnceAtTheirPrice`; `disableTumblesThenFreeRescueAt20Hull`; `hymnDriveNoWearHalfHyperspace`; `handToAutopilotAndTakeHelmInDescent`; `returnToLastLandingCostsACellOrDailyRecall`; `shipwrightRemovesUpgradeWithoutRefund`; `interceptorSeatsHoldAndHull`; `allowFlightFalseNeverKicksPilot` (rules; the real check is client-side, 19.2) |
| `SpaceGameTests` | W2 | `boltSweepHitsFastTarget`; `boltNeverHitsItsShipOrRiders`; `shipWeaponsHurtOnlyShipTargets`; `heatLocksAt100AndCoolsTo40`; `missileLockConeRangeAndTicks`; `missilesComeFromTheHold`; `shieldFirstThenHull`; `shieldRechargeAfterDelay`; `hullZeroCallsDisable`; `playersShipsNeverDamaged`; `patrolsOnlyForArmedShips`; `patrolGapRespected`; `encounterEndsOnLeaveJumpOrDisable`; `blockadeWavesAndGeneratorShield`; `blockadeClanFightersSideWithPlayer`; `blockadeResetsOnDisable`; `peacefulBlockadeIsAPass`; `scanMeterFillsAndDrains`; `spaceRunCompletesAtTheBuoy`; `spaceClearCompletesWhenGroupDies`; `bountyAceHasDoubleShieldHullAndTwoWingmen`; `killXpAndCreditsToShooter`; `capacitorShieldAndRecharge`; `spindlePlacedOnceAndVaultOncePerDay`; `fallBelowOrbitFloorReturnsToShip`; `spindleTemplateHasSidecarAnchorsInsideSize`; `coopKillsCountForOwner`; `fighterFlightAndRuns` (a fixture in the overworld sky) |
| `PlanetM5GameTests` | W3 | `ghessarHoldsRainAtZero` (rules); `tyrvannStormAlwaysOn` (rules); `sandstormCycleSeeded`; `sandstormStateCodecRoundTrip`; `ridgeLightningDistanceSeekingAndHubExclusion` (rules); `verseStoneChannelAndInterrupt` (a fixture block in the overworld); `verseStoneForceCutOnce`; `verseStoneOwnerAndPartyOnly`; `verseHumSlowsWhileGuarded`; `silencedStoneCallsHook`; `hymnalPlacedOncePerServer` (detached data); `everyM5TemplateHasSidecarAnchorsInsideSize`; `stormglassFormsWhereLightningStrikesBasalt`; `peatBurnsAsFuel`; `holdfastGateOpensPerPlayerFromNeutral`; `newStructuresHaveStructureTags` |
| `StoryM5GameTests` | W4 | `stagesOnlyMoveForward`; `m4SaveAtC4CompleteEntersWaiting`; `callOnSafeMomentOnly`; `helmUnlockedAtCalledAndForOldSaves`; `helmLessonPaysOnce`; `waysProgressInParallelFirstWins`; `forceWayOffersOnlyKnownPowers`; `countingHouseGuardsByWay`; `daskChoicesAndShifts`; `armedSkippedWhenShipHasCannons`; `tyrvannRefusedBeforeBlockade`; `blockadeStartsOnArrivalInOrbit`; `twoFailuresOfferTheClan`; `versesNeedThreeAndLevel17`; `ulvaineChoiceRewardsOncePerFlag`; `reportShowsRankFourChecklist`; `clanStartFromKaastOutcome`; `helmetReturnedOnce`; `tiersGateVendorsAndAttitude`; `standingRowsOnlyWhenMet`; `contractsValidateOnNewPlanets`; `spaceObjectivesGoThroughSpaceMissions` (stub); `bountyOncePerBoardPerDayOutsideSoftCap`; `deedsCounters`; `newFactionKillLossesCapped`; `debtChoicesPayBribeOrForce`; `ulvaineHelperPaidOncePerDay`; `peacefulRules` |
| `TurningGameTests` | W5 | `offerAfterOneDayInTheBand`; `reofferAfterThreeDaysAndStopAfterThird`; `fallOnceReturnOnce`; `deliveryRestartsWhenStruckDown`; `confrontationResetsAfterDeath`; `riteChangesPathWithoutRestartingLessons`; `rankCappedAtTwo`; `oldSignaturesRemovedNewGranted`; `boughtNodesHalfRefunded`; `reputationAndTrustAfterTurning`; `coldMentorLine`; `rankFourChecklistRules`; `ceremonyGrantsPowerSlotsRobeAndPoints`; `wayfarerTitleAndPassive`; `darthNameRecorded`; `robesReissuedOnceADay`; `returnEscortLandsPrisonersAtGreenwatch`; `peacefulConfrontationIsATalk` |
| `ForceM5GameTests` | W6 | `secondBarSlotsOpenAtLevelAndRank`; `stasisHoldsAndBreaks`; `stasisOnBossesQuarter`; `diminishingReturnsOnResistantTargets`; `fearFleesAndChokeDreadMigrated`; `pacifyStopsTargetingUntilHit`; `valorTouchesAllies`; `drainPulsesHealAndRefund`; `drainRefusedOnDroidsAndFighters`; `rageModifiersAndNoRegeneration`; `mindTrickTagsAndDialogueAction`; `valorAndRageModifiersFollowTheEffect`; `branchesExclusive`; `masteryThresholds`; `newNodesVisibleAndPurchasable`; `wayfarersStrideBonuses` |
| `SaberM5GameTests` | W7 | `formModifiers`; `formChangeRules`; `offhandStrikeValidates` (reach, cooldown, lit, alive); `offhandPenaltyUnlessJarKai`; `bladeLengthStats`; `tuningSurvivesDisassembly`; `tuningEffects`; `resonantCountsOnce`; `legendaryNeverRolledNorSalvaged`; `legendaryMultipliers`; `pityCounters`; `knownSchematicNeverRolled`; `legendaryComponentEffects` (the eight W7 parts); `robesCountAsSetPieces`; `concussionMissileSchematic` |
| `FoesM5GameTests` | W8 | `tellsPrecedeHeavyHits` (each new foe); `enforcerAttitudeByStanding`; `haskFearlessAndStrongWilled`; `chantHealsAllies`; `stormCallTellThenStrike`; `scourBurrowBurstCycle`; `scourHurtOnlyAboveGround`; `stormbackNeutralUntilProvoked`; `stormbackChargedByLightning`; `mentorDuelYieldsAndResets`; `ulvainePhasesAndYield`; `ulvaineHealthScalingAndCap`; `resolveCallsHook`; `echoOncePerDay`; `raidParticipantsAndScalingOneToEight`; `raidWeeklyLockoutAndPity`; `raidWipeResets`; `newFoesReportKills`; `ulvaineBreaksStasisAfter20Ticks`; `cantorChoirShieldVersesRingsAndWindow`. Fights run on fixtures placed with `RuntimeSites.placeAt` |

### 19.2 Client game tests (screenshots reviewed)

- **`FlightClientTest`** (lead driver, W1 assertions): the spike of 4.1 on a **dedicated server** (`allow-flight=false`): `/oldrepublic ship give @p` and `/oldrepublic ship helm @p`, board, take the helm, 400 ticks of scripted input with the pass criteria of 4.1; then, once W2 and W3 are in, climb into `orbit_home`, dive back, jump to `orbit_ghessar`, dive and land at Saltgate by hand; assert the ship's state and position at each step; screenshots at lift-off, the atmosphere line, orbit and landing.
- **`PlanetsM5ClientTest`** (lead driver, W3 assertions): in each new planet, sample biomes at the seed survey's coordinates, place a verse stone site and the Hymnal with `/oldrepublic site place`, assert the templates and sidecar anchors, take sky screenshots at noon, in a Ghessar sandstorm, under Tyrvann's storm and of the twin sun, and check that after `/weather rain` then `/weather clear` in the overworld a player on Ghessar has a client rain level of 0 within 20 ticks (3.5).
- **`PersistenceM5Test`** (lead): create a world; write a Chapter Five record at `VERSES` with two stones silenced, a Turning record at `TASK`, `helm` on, a field-landed interceptor with every M5 upgrade, a `RaidData` with pity 1, a saber with a long blade and a tuned crystal, and a legendary mod on armour; close; reopen; assert all. Then a ship saved in `FLYING`: on reopening it is at its last landing spot.
- **`M5ClientTest`** (lead, written in Phase 0) runs the eight step classes `m5/{Flight,Space,Planet,Story,Turning,Force,Saber,Foes}ClientSteps`; each worker fills its own. W8's `FoesClientSteps` also checks `BossMusicService`'s registry (every boss id with its tracks; `bossMusicServiceRegistry`).
- The key-clash check covers Helm (`Y`) and Focus (`Left Alt`).
- Screenshots are named `oldrepublic_m5_<nn>_<what>`, by band: the lead's drivers 090–099, W1 100–109, W2 110–124, W3 125–139, W4 140–154, W5 155–164, W6 165–174, W7 175–184, W8 185–199.

| Worker | Screenshots |
|---|---|
| W1 | the helm HUD in a sky; the landing hint; the atmosphere overlay up and down; the interceptor (third person, all three paints); the refit; a field landing |
| W2 | each orbit with its planet below at Standard; `orbit_tyrvann` at Reduced, High and Cinematic; an asteroid field; the combat HUD with brackets, lead pip, heat and a lock; a shield hit; a fighter exploding; the blockade gunship; the Spindle's landing deck and vault |
| W3 | each biome at noon; a Ghessar sandstorm; the twin sun; Tyrvann's storm with a ridge strike; Saltgate and the holdfast from their pads; a verse stone awake and silenced; the Hymnal's arch and the Heart |
| W4 | the Chapter Five journal page; Vessba, Varra and Dask's dialogues; the hologram; the verse vision; the Standing panel with the two new rows and the rank 4 checklist; the chapter banner |
| W5 | the Fall's offer; the Last Lesson; the rite; each ceremony; the three robes worn |
| W6 | the second bar with Focus held; Stasis on a trooper; Valor on two players; Drain's stream; Fear's victims; Rage's embers; Mind Trick |
| W7 | Makashi, Djem So and Jar'Kai stances; dual-wielding in first and third person; short and long blades; the Tune tab; a legendary craft |
| W8 | each new foe's tell; a scour bursting; a charged stormback; Ulvaine in each phase and the yield; the First Cantor in each phase with four players; a wipe |

### 19.3 PlaythroughTest extension (lead)

After Chapter Four, the same world continues on the Jedi path with real inputs where possible:

1. Wait out `WAITING` (logged time skip). Hear Saelen's call.
2. Take the helm with the real Helm key, lift off, climb to orbit with real inputs, open the ship screen and jump to Ghessar's orbit with real clicks, dive and land at Saltgate by hand (the autopilot is a logged fallback).
3. The kajidic's way: "A Debt in the Canyons" (pay the debt: no fight) and "The Quiet Cargo" (fly to the buoy; a scan failure retries once, then a logged shortcut). The audience; the counting-house with no guards; let Dask go.
4. The free cannons. Jump to Tyrvann's orbit: the blockade, fought with real cannon fire; after 2,400 ticks the logged `/oldrepublic space blockade win` shortcut (PASS WITH SHORTCUT, the M3 rule).
5. Dive and land at the holdfast. The three verse stones: logged teleports beyond 128 blocks; the guards with real saber and Push; the channel with the real use key and a Force cast to cut it. An XP top-up to level 17 if needed (logged).
6. The Hymnal: Ulvaine's three phases with real inputs (parry, jump the pulses, break the resonators), each phase with a 2,400-tick timeout and the logged `/oldrepublic ulvaine phase` shortcut. **Spare**; report to Saelen. Assert `COMPLETE` and the rank 4 checklist.
7. A logged shortcut to the rank 4 requirements, then the Master ceremony with real dialogue clicks. Assert rank 4, `force_valor` owned, slots 7 and 8 open, the robe.

A shorter **Sith run** starts from a logged shortcut to `SALTGATE` on the Sith path and takes the clan's and the Force's ways' first steps (the matriarch hunt; Fear on the counting-house door). Three more runs are item 10 of the cut list: the **Fall run** (a logged shortcut to Chapter Four `COMPLETE` at alignment -60 on the Jedi path, then the offer, the delivery, the Last Lesson with real inputs and the rite), the **Return run** (the same on the Sith path, with the detention post), and a **solo raid run** (a logged shortcut to `COMPLETE` and level 20, then the keystone and the three phases, each with a 2,400-tick timeout). Every shortcut is logged.

### 19.4 Manual checks (owner or a tester)

- Free flight: does it feel like flying a ship, not a minecart in the air? Any rubber-banding on a dedicated server? Motion sickness at full speed with the camera behind?
- Orbits: do they read as space at every preset, with the planet below at the right size? Is the transition through the atmosphere convincing?
- The blockade in an unshielded courier: hard enough to matter, never a wall? With a gunner friend?
- Tyrvann's storm with flashing effects off: comfortable for players sensitive to flashing?
- Ulvaine solo at level 17 and 18, and with 2 players. Target: 8 to 12 minutes; every phase readable with sound off.
- The Long Hymn with 1, 4 and 8 players. Target: 12 to 20 minutes for a group; solo possible but long.
- The Fall and the Return: is the cost felt, and is the pace (days of play) right?
- Dual-wielding: readable in third person? Jar'Kai worth its points?
- A dedicated server with two players: pilot and gunner through the blockade; one logging out in orbit.
- Peaceful pass: the blockade as a pass, the stones without guards, Ulvaine as a talk.

---

## 20. Lore notes for `DECISIONS.md` (lead adds these)

- **Planets are original.** Ghessar (the brief's wasteland port; Tatooine-like in feel) and Tyrvann (the brief's storm jungle) are original worlds, like Selvaren and Varkhast, so the mod never contradicts canon geography. Tython, Coruscant and the Dark Council's seat stay off-screen places named in dialogue and the codex (the M1 codex `cold_peace` already puts the Jedi on Tython); no player goes there.
- **Hutts and kajidics.** Hutts, their crime families (kajidics), majordomos and enforcers are canon concepts. Vessba the Hutt, her kajidic, Ommo Teel, Imrel Dask and Saltgate are original. Vessba has her own model (a Hutt is not a humanoid); every other kajidic NPC is human, because the mod has no other species' models.
- **Mandalorians.** Mandalorian clans, their armour and the duel of honour are canon concepts; Clan Hask, Dorrun Hask and Varra Hask are original, and the clan is Torvin Kaast's own (M4). The clan's armour metal is **clan iron**, an original name: the spec avoids canon proper nouns for materials, as M4 did. The Hask fighter is an original design.
- **Sith.** The Dark Council and the title Darth are canon. Darth Ulvaine and the First Cantor are original characters; the Long Hymn, the Hymnal, the verse stones and the Hymnsworn are original. Ancient Sith temples and Sith sorcery are canon concepts the Hymn borrows from, but the song-engine and its choir are original.
- **Force powers.** Stasis, Valor, Drain, Fear, Rage and the mind trick are canon powers, adapted. Each is shown through what it does to the world (rime on a held body, a gust that lifts dust, a drawn mote of breath), never as a glow, a dome, a sigil or a ground circle (the art direction). Fear and Mind Trick work only on suitable NPCs (`#oldrepublic:mind_trickable`, `#oldrepublic:strong_willed`), never on players or bosses, in keeping with the canon rule that the trick fails on strong minds.
- **Saber forms.** Makashi, Djem So and Jar'Kai are canon form names, adapted as stat and timing changes. Crystal tuning is original and changes stats only; a crystal's colour keeps the M1 rule.
- **Starships.** Every ship design is original: the courier (M4), the interceptor, the Hymn interceptor, the kajidic raider, the Hask fighter and the blockade gunship. No canon ship class, model name or shipyard appears. Laser cannons, concussion missiles, deflector shields, hyperdrives and nav computers are canon technology, used generically.
- **Hyperspace from orbit.** Ships jump to hyperspace only from orbit and drop out in the destination's orbit, as canon ships clear a planet's gravity before jumping. The M4 autopilot keeps its pad-to-pad trip and does the same climb off-screen.
- **The Fall and the Return.** A Jedi who falls and a Sith who is redeemed are canon themes; the steps (the Watch holocron, the last lesson, the detention post, the Overseer's due) are original. Neither mentor dies in either questline: each yields and leaves, or loses a crystal or a blade, so both stay in the story.
- **Ranks.** Master and Darth are canon titles; a Darth name is chosen from an original list of six. **Wayfarer** is an original title for Independents, following the M3 Pathfinder.
- **Materials and creatures.** Sunstone, stormglass, hymnstone and peat are original or generic names; the sand scour and the stormback are original creatures.

---

## 21. Open questions

Only the decisions that block development. Each has a recommended default, so work goes ahead without waiting. The lead records the owner's answer in `DECISIONS.md`.

1. **How the ship is flown.** Recipe A (the pilot's client moves the ship and the server checks it) feels like flying; fallback B (the server steers from the pilot's input) is simpler and safer but adds a round trip to every input.
   **Default:** A, behind the feasibility gate (4.1). B only if the spike fails twice; `flight.serverSteering` switches any server to B.
2. **Five orbits or one space?** One shared space dimension would need its own coordinates for each system and its own rules for what is "below"; five orbit dimensions keep every system's sky, planet and encounters apart.
   **Default:** five orbit dimensions sharing one dimension type (5.1).
3. **Turning during Chapter Five.** A player could Fall or Return between the factor and Ulvaine.
   **Default:** allowed. The chapter goes on, mentor lines follow the new path, and the report goes to the new mentor (6.9).
4. **Ship against ship between players.** Space combat could include PvP.
   **Default:** no. Ships cannot damage other players' ships (5.4); players can fight side by side in the same orbit. PvP stays on the owner's improvement list.
5. **Raid size.** The brief asks for raid-size bosses but the mod's groups so far were up to four.
   **Default:** one to eight players, scaling past four (12.4); solo is possible but long.
6. **The weekly reset.** "Once a week" can mean seven in-game days or a real calendar week.
   **Default:** seven in-game days (`raid.weekDays`, 12.3), like every M3 and M4 daily, which counts in-game days; a server can raise it.
7. **Rank 4 at level 20.** The level cap is 100, so 20 is a pacing choice, not the end of the curve.
   **Default:** level 20 (8.5), five to six days of the endgame loop after the chapter (16.1).
8. **Binding Ulvaine as an Independent.** An Independent has no order to hand her to.
   **Default:** Clan Hask collects her for the bounty she put on others (6.7).
9. **A second ship or a refit?** Owning two ships needs a hangar, a choice on every summon and twice the persistence.
   **Default:** one ship per player; the interceptor is a refit of the courier at the Saltgate shipwright, and the refit back is allowed (4.9).
10. **Who learns Mind Trick.** The brief's light-side list "could include" limited mind influence (`DESIGN_BRIEF.md` 6), but the Force's way at Saltgate (6.3) and the Return's warden (8.3) work best when every path can learn it.
    **Default:** the Exploration tree, open to every path (9.8), recorded in `DECISIONS.md` as a difference from the brief; Fear stays Sith only.

---

## 22. Revision notes

This revision fixed four reviews against the code (story, factions and paths; powers, sabers and foes; worlds, flight and space; internal consistency) and folded in the follow-up probe. Each line is a decision that changes behaviour, with the sections it touches. Phase 0 as built adds its own lines below the review's.

**Reviews and the follow-up probe**

1. The story's free cannons are fitted whatever the kajidic standing; only bought cannons need the vendor (6.5, 7.1).
2. `QuestHooks.onPlanetArrived` fires for every rider at every touchdown on a planet (pad, bay glide, field landing) and reaches Chapter Four, Chapter Five and the Turning (4.6, 17.1).
3. With `flight.enabled` false the blockade is the Peaceful scripted pass, and the kajidic's way is the debt job alone (6.5, 6.9, 14.4).
4. A turned player keeps `min(rank, 2)`; the rite swaps `c3.rank3_done` for `c3.rank3_paid` and `rank4.done` for `rank4.paid`, so a ceremony held again pays no SP or XP (8.4, 8.5).
5. `StoryMentors.comeTo` (lead) summons a mentor at the next safe moment; the cold mentor's line is the lead's `onUsed` rule, not a dialogue redirect; Ansa's and Kaddor's lines go through `DialogueRedirects` (8.3, 17.1).
6. The turning rows apply on top of `Identity.setPath`'s own changes, and say so; refunds, granted passives and the second bar's slots are named (8.4).
7. The Amends prisoners do not take seats: they board at the ramp and step off when the ship lands at the Greenwatch (8.3).
8. The new planets' arrival rewards come from one per-planet table that `ChapterFourRepairs` reads; nothing pays twice and no pad unlocks by accident (3.1).
9. Banners 11 to 15 use the `quest.oldrepublic.c2.banner.*` prefix; W5's lang keys avoid the two that existed (6.10, 18.5).
10. The shipwright's fit and refit, the fence's weekly hymnstone shard and the armourer's clan iron are dialogue services, not vendor stock (4.8, 7.1).
11. One kill hook, `QuestHooks.onFoeKilled`, reports the five new foes for reputation (the M3 kill rule), codex pages and contract hunts (7, 12.1, 17.1).
12. The "safe moment" is the M4 seeker rule as built (`SeekerScene.isSafe`), not the M2 wording (6.1).
13. `faction:tier` was dropped; `reputation_min` already reads tiers (6.11).
14. Dask is spawned per owner at his desk, so his fate is per player (6.4).
15. The detention post holds two security droids (the M4 group, `count` 2) and two M3 troopers, and the fight counts as hostile (3.4, 8.3).
16. Choke's fear moves to W6's `force/fear/ForceFear.apply`, which W7's techniques call; control lengths come from `ControlEffects.diminished(target, ticks, kind)` alone, with stasis on bosses at 25% and Kaast at 15% (9.2, 9.5).
17. The second bar has its own open count (`SkillData.openSlots2`, `open_slots_2`) and `Skills.isOpen` (9.1).
18. Valor and Rage are transient attribute modifiers kept in their balance sections; the new effects live in `force/ForceMobEffects` beside the M2 `ForceEffects`, with particles off (9.3, 9.6).
19. Boss music is one service, `BossMusicService.register(bossId, type, tracks)`, with the payload `boss_music(bossId, track)` (0 stops); the lead switches the Collector's and Kaast's senders (12.5).
20. Ulvaine and the raid use the M3 participant rules; the raid scales from 1 to 8 players, and its week is `Clocks.day / raid.weekDays` (12.2 to 12.4).
21. Phase 0 gives the new forms, parts and lens placeholder switch cases that W7 replaces (10, 17.1).
22. `Quality.LEGENDARY` is the fourth tier; `configure` takes three or four values, so stored M4 tuning loads; lens quality runs 0 to 3 (11).
23. `SpawnerGroups` gains a `Group` interface and `register` (12.1, 17.1).
24. The Hask warrior has its own combat goal, with a shield bash that hits (6 damage, knockback, `stagger` 20 ticks) and a sound (12.1, 15.3).
25. Tags hold entity types only; `#oldrepublic:duelists` was dropped for `SaberDuelist` (9, 12.1).
26. Legendary ship parts use the `common` discipline and the ship bits `1 << 19` and `1 << 20`; W1 and W2 build their effects (4.8, 11.2).
27. Confusion works through `Allegiances.setConfusedTest`, which W6 supplies (9.8).
28. Legendary schematics have pity counters: the raid's in `RaidData.pity`, the others in `CraftingData.legendary_misses`, all rolled through W7's `LegendaryDrops.roll` (11.4).
29. Boss dispatch is by id: `kaast` to Chapter Four, `ulvaine` to Chapter Five, anything else to Chapter Three; the raid and the mentor duels report through `onRaidCleared` and `MentorDuel`'s `onEnd` (12.6).
30. Ulvaine's echo keeps its once-a-day lockout and count in `RaidData` (`echo_day`, `echoes`); W8 pays it (12.2).
31. Every saber carries `ATTACK_RANGE` from its length and parts; the off-hand strike keeps its own payload because the server checks melee range for the main hand only (10.2, 10.3).
32. The ship keeps its velocity in its own field, because the client applies motion packets even for an entity it controls (4.1).
33. The orbit type uses the `minecraft:visual/<name>` attribute ids, angles from the zenith, and an empty `#oldrepublic:orbit` timeline tag (5.1).
34. W1 owns the disabled ship whole: the lock, the tumble, the rescue, its keys (`flight.tumbleTicks`, `rescueHull`) and its sounds (`ship.flight.alarm`, `ship.flight.rescue`) (5.4).
35. W4 pays the space bounty in place of the contract formula; W2 rolls its loot table (5.8, 7.2).
36. The paid clan help after two blockade failures sets `c5.clan_hired`, which `ChapterFive.clanStands` reads (6.5).
37. The verse stones have one `SPAWNER` anchor each and 3, 4 and 5 guards (`planets.verseGuards`) (3.3.2).
38. The holdfast gate opens per player, from Neutral clan standing, and shuts behind them (3.3.1, 6.6).
39. The counting-house has eight guard posts, and the clan's warriors are Allied to the mission owner's party whatever the standing (3.2.1, 6.3).
40. The helper's Ulvaine roll is W4's `gameplay/ulvaine_helper`, once per in-game day (`c5.helper_day`) (6.9).
41. On Peaceful the matriarch hunt and the counting-house complete on arrival (the M3 `clear` rule) (6.9).
42. Every M5 template maps to a `SiteKind` (`RuntimeSites` throws for an unmapped one), and the four worldgen structures get structure tags (`#oldrepublic:crashed_freighters`, `kajidic_posts`, `hymn_shrines`, `mire_graves`), the only way a worldgen site is found (3.6, 17.1).
43. The rescue of a disabled ship is one server-driven run that keeps its riders, across levels; the pilot leaving or dying returns the ship; Return to last landing costs one fuel cell or the free daily recall, and the M4 recall and tow still refuse a `FLYING` ship (4.10, 5.4).
44. A jump from orbit starts where the ship is and enters the run at its jump phase with `ensureHub`; a jump to an orbit has no Descent and arrives above (0, 0); `PlanetHubs.ensure` runs on a jump to an orbit and on a first dive (4.7).
45. The Hymnal is two pieces, `site/hymnal_arch` on the surface and `site/hymnal` sunk 18 blocks below it, eligible from `VERSES` and placed by the M4 `ensure` rule within 160 blocks; its fallback is `placeClearing` (3.3.3, 6.7, 6.9, 18.1).
46. Beds work on Ghessar and Tyrvann as on the M4 planets (the `bed_rule`) (3.2, 3.3).
47. `Flights.recover` settles `FLYING` records that have no running autopilot at the record's position; `Ships.busy(record)` (an autopilot run, or `FLYING`) replaces the M4 in-flight checks (4.2, 4.10).
48. `ship_action` gains `OPEN_SCREEN`; `TAKE_HELM`, `LAND` and `OPEN_SCREEN` are accepted without an open ship screen from the owner riding their own body, and the inventory key sends `OPEN_SCREEN` while piloting (4.3, 18.2).
49. A `flight_motion` payload carries every velocity the server imposes (Up, Down and the edge), because the ship's velocity lives in its own field (4.7, 18.2).
50. Orbit arrivals are clamped inside 1,920 blocks of (0, 0), and every point W2 places in an orbit stays inside it; the world-border clamp is gone (4.7, 5.6).
51. The speed numbers are settled: boost ×1.25, Thrusters +10%, `courierOrbit` 1.4, `interceptorSky` 1.2, `interceptorOrbit` 1.45, every speed under the 1.99 cap, and a resync above `maxSpeedCap` × 1.1 (4.4, 16.4).
52. The server measures collisions from the ship's speed before and after each move; flying ships pass through each other; the landing check uses the measured speed (4.4, 4.12).
53. A rider who leaves the server in another level than the ship's last landing spot is moved beside it on their next join (`ShipEvents.joined`, a cross-level teleport under a `TravelTickets` ticket) (4.10).
54. The ship simulates only where `piloted() && isLocalInstanceAuthoritative()` (4.1).
55. The verse stones' ring point is chosen with `findClosestBiome3d`; `planets.verseRings` is [250, 350, 450] (`verseEnsureRange` is gone), `debtorCampRings` [200, 300, 400] and `detentionRings` [120, 160, 200] (3.3.2, 3.4, 16.4).
56. Ghessar resends rain and thunder level 0 every 20 ticks, and the four biomes that lacked one get a precipitation flag (3.2, 3.3, 3.5).
57. Saltgate is about 63 by 63, the M4 hub footprint that the surface search, the raised fallback, the hub ticket and `flight.hubBayRange` assume (3.2.1). (Milestone 6: Saltgate is 89 by 89 and these read the template's size, except `flight.hubBayRange`, now 88.)
58. Both hubs have `HEART` and `PAD_TERMINAL`; the harbourmaster and Dorrun offer the free daily recall (`ship:recall_free`) (3.2.1, 3.3.1).
59. The Silent Spindle is placed with `RuntimeSites.stamp` after its chunks load (no terrain adaptation in the void), and its droids restock daily (5.7).
60. `ShipRules.maxHull` gives each class its hull, and `Ships.spawn` picks the entity type from the record's class bit (4.9).
61. W1 changes every use of `CourierShuttle` as a type outside the courier's own files to `ShipBody`; `Ships.shuttle(...)` stays for M4 code and tests (4.9, 17.1).
62. The gunner seat is the courier's seat 1, the left bench (5.2).
63. A field landing is refused inside another player's owned site (a `WorldSites` or `PlanetSites` record), not in a "protected area" (4.6).
64. Fallback B's speeds are 67% (`flight.serverSteeringSpeedPct`) (4.1).
65. The Hymn interceptor has its own `space.hymnInterceptor` key and pays 60 XP (5.5, 16.4).
66. The twin sun seen from Ghessar's orbit is W2's (5.1).
67. `salt_crust`, `sunstone_ore`, `stormglass_cluster` and `peat` have block items; `verse_stone` and `holdfast_gate` are registered with `ModRegistry.blockOnly` (18.1).

**Phase 0 as built**

68. `SkillData.isOpen(index)` is the slot rule: slots 0 to 3 are open while `index < openSlots`, slots 4 to 7 while `index - 4 < openSlots2`; `Skills.isOpen` reads it. `SkillData.BAR_SLOTS` is 4 and `openSlots` counts the first bar only. `Skills.slots` stays the first bar's count, so the M4 HUD and input code are unchanged until W6 widens them. Phase 0 never writes `openSlots2`. The skill slot command takes 1 to 8 (9.1).
69. The legendary hilt parts count and render as `GUARDIAN`, `WRAPPED` and `WEIGHTED` (their model fallbacks) until W7; `SaberItems.partItem` returns a stand-in for a part with no item and `hasItem` says which have one; `lensItem(STORMGLASS)` returns the honed lens, so the stormglass lens cannot be fitted until W7 registers it; the new forms use Unformed's modifiers (10, 11.2).
70. `quality.oldrepublic.legendary` is in `crafting.json` from Phase 0 (W7 does not add it again). A Legendary item has the EPIC rarity and a gold quality line in its tooltip. `CraftingBalance` keeps its three-value arrays until W7 makes them four (11.1, 16).
71. `CraftingData.misses(id)` and `withMisses(id, n)` read and set a pity counter (0 or less removes it) (11.4).
72. `RaidData`'s `last_clear_week` and `echo_day` default to -1, because week 0 and day 0 are real values (12.2, 12.4).
73. `MentorDuel`'s stub calls `onEnd(YIELDED)` inside `start`; W8's real duel calls it when the duel ends (8.3, 12.1).
74. `CrystalAttunement` equality includes the tuning (10.4).
75. The confused test is "nobody" until W6 installs its own at init (9.8).
76. The three new client switches share one `/oldrepublicfx` status line (`command.oldrepublicfx.status.space`, in `quest.json` beside the other effect keys) (14.4).
77. Until W3 replaces them, Ghessar and Tyrvann are placeholder worlds: vanilla desert and jungle (a `minecraft:fixed` biome source on the overworld noise), with the M4 planet type's rules and no music or cave mood; vanilla structures can generate there meanwhile (3.2, 3.3, 17.1).
78. `SiteAnchors.Anchor` appends the 73 M5 anchors in spec order, starting with `NPC_DEBTOR` and `SPAWNER` (which did not exist before) (3.2 to 3.4, 5.7, 12.3).
79. `SiteKind.ofTemplate` maps the M5 templates by path prefix, after every M4 check, so an owner may split a site into more pieces (`site/hymnal` also covers `site/hymnal_arch`) (17.1, 18.1).
80. A missing template means "not yet": `PlanetSites.ensure` returns null with the M4 retry pacing, `PlanetHubs.ensure` completes with zero as for a missing planet, each logs once per run, and `/oldrepublic planet hub` refuses with the unknown-planet message (3.1).
81. `PlanetHubs.template` is null for the home world and any world without a row; it used to fall back to the Greenwatch (3.1).
82. The new site pieces' buried layers (verse stone 2, Hymnal arch 3, Hymnal halls 0, debtor camp 4, detention post 3) are placeholders that W3 sets with its templates; sinking the Hymnal under its arch is W3's placement code (3.3.3).
83. Hub stilts are cut sandstone on Ghessar and mud bricks on Tyrvann, until W3 says otherwise (3.1).
84. Hub hosts: Ansa on Selvaren (and, as in M4, the home world), Kaddor on Varkhast, the harbourmaster on Ghessar, and none on Tyrvann. Only Ansa and Kaddor greet in chat, and `c4.met.<planet>` is set only where the host greets. The cold path is Sith on Selvaren and Jedi on Varkhast and none elsewhere, because the M5 hubs judge by standing (3.1, 7).
85. The vendor's hood shows on the home world and Selvaren; the half-cape on Varkhast, Ghessar and Tyrvann (3.1).
86. `PlanetContracts.templates(planet)` holds the 7.2 boards and `types(HOME)` is empty; `planetOf` has no Selvaren fallback; a planet without site kinds calls back null at once. Ghessar's cull hunts `sand_scour` and Tyrvann's hunt `stormback` (W8's ids; a missing entity type spawns nothing); W4 adds the other fights, guards and the escort (7.2).
87. Only Selvaren and Varkhast end Chapter Four's first flight (`ChapterFourRules.chapterFourWorld`); the hunt counts a landing on any planet (8; M4 8.3).
88. Ash hounds keep out of their own world's hub (3.1).
89. `SpawnerGroups.register` refuses a built-in or already registered id; `unregister` is a test seam (12.1, 17.1).
90. The arrival banners `quest.oldrepublic.c2.banner.planet.{ghessar,tyrvann}` and their `.sub` came in with Phase 0; W4 does not add them again (3.2, 3.3, 18.5).
91. Bosses reach their chapter by id family: ids starting `kaast` go to Chapter Four, `ulvaine` to Chapter Five, every other to Chapter Three (`QuestHooks.bossChapter`) (12, 17.1).
92. `StoryData`'s Chapter Five record holds `wait_since` (ticks, -1 none), `ways` (a bit mask by `Way` ordinal), `way` (the first way completed, -1 none), `dask` (-1 none, 0 release, 1 clan, 2 end), `verses` (a bit mask by stone index), `ulvaine` (the `BossOutcome` ordinal, -1 none) and `boss` (W4's own number). The Turning record holds `kind`, `stage`, `band_since`, `reoffer_day`, `turned_day` (-1 none; W5 documents whether they count days or ticks) and `task` (W5's own number) (6, 8, 17.1).
93. Chapter Five's deeds are the counters `c5.bounties` and `c5.contracts` and the flags `c5.writ` and `c5.clan_hired` (W4); the hymn's clear and the echoes come from `RaidData`. The clan stands with the player when the clan way is done, the clan was hired, or Clan Hask's standing is Trusted or better (6.6, 6.9).
94. `Turnings.done(kind)` reads the identity choices `turn.fall.done` and `turn.return.done` (8).
95. Rank 3 held again with `c3.rank3_paid` still promotes and gives the resonance, robe, skill, journal entry and ceremony; only the SP and XP are skipped (8.4).
96. `DialogueRedirects` keys are the bare path for the mod's own dialogues or a full id; a missing, identical or invalid target opens the original; there is one hop; the speaker stays; registering one id twice throws (17.1).
97. `StoryMentors.comeTo` keeps one call per player in memory (a newer call replaces an older one). Logout and server stop drop it; respawn and dimension change keep it, and `isComing` and `cancelComing` let W5 issue it again. It waits `mentorSummonTicks`, then for a safe moment, never during a spar; a mentor already there is moved and anchored, else one is spawned; with a new camp the camp moves and Brenn follows Sorn. It sends no message itself; `onArrive` reaches the player (8.3).
98. The safe moment is: on the home world, not aboard a ship, no dialogue open, and `SeekerScene.isSafe` (6.1, 8.3).
99. While the other path's mentor stands within `lostMentorRadius` of the camp, the path's own mentor is not replaced (8.3).
100. Using the mentor of the other path asks W5 first (`setOtherPathTalk`); only when W5 declines does the mentor say the cold line (`turning.oldrepublic.cold.<jedi|sith>`, once either turning is done) or `not_now` (8.3).
101. The Standing panel keeps the M4 order for the first four factions (Jedi Order, Sith Empire, Republic, Frontier) and then lists the factions met later in `Faction` order; plain `Faction` order would swap the Republic and the Frontier (6.11, 14).
102. A faction is met through the identity choice `faction_met:<id>` (`Identity.meetFaction`); the four M1 to M4 factions are known from the start; `knows` decides the Standing rows, rank 3's best reputation and `/oldrepublic identity show`; W4 meets both new factions on the first arrival at Saltgate (6.11).
103. A later faction's issuer key is its id (`board.title.<id>`, `board.notice.standing.<id>`, `contract.<type>.<id>.<n>`), and its vendors refuse only below Neutral (6.11, 7.1).
104. The Chapter Five journal header comes from `CHAPTER_FIRSTS` (its fifth entry is `the_factor_asks`) and the page header is `journal.oldrepublic.chapter_five`; the section 8 and 12 entries follow `report_c5`, so they show under Chapter Five (6.10, 14).
105. The space objectives `SPACE_RUN` and `SPACE_CLEAR` are not combat objectives: no ground trigger moves them, they never resolve on arrival, they spawn nothing, and their target is 1; W2 drives them (5.4, 7).
106. Each `Destination` carries its planet, the milestone that added it, its point and light on the star chart, and its lanes to earlier destinations; the ship screen reads them. `Destination.hubOf(planet)` is the per-planet pad table that `Pads.of` and `Flights.destinationOf` read (3.1, 17.1).
107. A destination added after Milestone 4 stays off the Nav tab until the owner has unlocked it (`hiddenWhileLocked`), so an M4 save's chart and list look as before, and a non-owner sees only the M4 four. From six rows the Nav list tightens to a 22-pixel pitch; W1 owns the Milestone 5 Nav layout (3.1, 13.2).
108. Finishing Chapter Four unlocks the four Milestone 4 destinations only; Saltgate and the holdfast unlock through Chapter Five (6.1).
109. `ShipState.FLYING` counts as flying, so a ship at the helm gets the M4 in-flight rules (no launch, repair, refuel, upgrade, recall or tow; riders locked; no pushing). Nothing sets it in Phase 0; W1 adds the recovery of a FLYING record with no flight (4.2, 4.10).
110. `TravelData.helm` is `optionalFieldOf("helm", false)`, and the M4 six-argument constructor stays (4.2, 14.1).
111. The ship's bits: M4 upgrades 0 to 3, the M5 upgrades 4 to 7 (`UPGRADE_CANNONS`, `UPGRADE_SHIELD`, `UPGRADE_THRUSTERS`, `UPGRADE_MISSILES`), sockets 8 to 10, plates 12 to 15, paint 16 and 17, `CLASS_INTERCEPTOR` 18, and the legendary parts 19 and 20 (4.8, 11.2).
112. `ShipBody` adds no synced data, so `CourierShuttle`'s data ids are unchanged; `Ships.ship(server, owner)` finds any ship body, and `shuttle()` returns it only when it is a courier (4.9).
113. The orbits share one dimension type, `oldrepublic:orbit`: fixed time with no clock (the Nether's setup), the empty timeline tag `#oldrepublic:orbit`, a ceiling (so no weather), black sky and fog, `star_brightness` 1.0, the sun at `sun_angle` 325 and the moon at `moon_angle` 78. The sky turns only east to west, so the sun stands 35 degrees from the zenith in the east, not the south-east; the client test checks the angles' sign (5.1).
114. Every orbit uses the all-air `orbit_void` noise settings and the single `orbit_field` biome (no features, carvers or spawns); W2 adds the asteroid feature (5.1).
115. `NavData` is unchanged: it stores planet ids and the M4 mentor's coordinates, and the new worlds unlock through the story (3.1, 8.2).
116. `StoryMentors` and `DialogueRedirects` pass to W4 with the rest of `quest/story` and `dialogue` after Phase 0; their 17.1 signatures stay frozen (17.1, 17.2).
117. W6 owns all of `client/force/**` and `force/choke/**`, as 17.2's row says; the other M3 and M4 powers' server code and `sounds/force_m4.py` stay the lead's, and the Choke mixins stay in W8's `mixin/foes` (9, 17.2).
118. From Phase 0 the key check (`m2/SkillClientSteps.checkKeyDefaults`) compares every mod key's default with every other key's, vanilla's debug keys aside (13.1).
119. The id check's baseline is the last commit before Phase 0, so what Phase 0 added counts as new (17.1, 18).
120. The Toggle Lightsaber key's default moves from G to U: vanilla 26.2's Quick Actions (`key.quickActions`) defaults to G, which the key check found (118). A saved binding keeps whatever key the player chose (13.1).
