# Feature checklist

Status per feature, with how it has been checked. Legend:
- **Built**: code and data exist and compile on CI.
- **Server test**: covered by a server game test (`./gradlew build`).
- **Client test**: driven by the client game tests, with screenshots reviewed (`./gradlew runClientGameTest`). `ClientSmokeTest` uses commands to reach each feature; `PlaythroughTest` plays the whole chapter on a normal-terrain world with real inputs (it teleports between distant places and logs every shortcut).
- **Played**: hand-tested in game by a person. Nothing is at this level yet.

## Milestone 1 (vertical slice)

| Feature | Built | Server test | Client test | Played | Notes |
|---|---|---|---|---|---|
| Force awakening (sleep or time trigger, dream, burst, journal) | ✓ | ✓ stage rules | ✓ dream and after | | Sleep trigger not driven by a test |
| Persistent progression (level, XP curve, Resonance, energy, skill points, mastery, anti-grind) | ✓ | ✓ curve, save round trip | ✓ HUD values | | Death/relog keep data by design (copy-on-death attachment) |
| Force Energy meter and regen | ✓ | | ✓ HUD | | |
| Force Push, charge and release | ✓ | ✓ cone, thresholds, costs | ✓ charge, release, aftermath | | |
| Force Push branches (Repulse Wave, Focused Blast, Deflecting Pulse) | ✓ | ✓ branch numbers and choice rules | ✓ Force screen | | Branch effects not driven by the client test |
| Meditation teaches Push; practice; the Calling | ✓ | ✓ practice counting, daily meditation | | | Calling depends on a generated cave nearby |
| Wanderer's Journal screen and codex | ✓ | ✓ unique entry ids | ✓ journal | | |
| Crystal cave structure (camp, descent, heart) | ✓ | ✓ templates load | ✓ natural caves found and played (playthrough) | | |
| Unstable crystal cluster hazard | ✓ | | | | |
| Resonant seal | ✓ | ✓ refuses un-awakened player | | | |
| Trial of Reflection and Shadow Echo | ✓ | ✓ requirements, orphan echo cleanup | ✓ forming, echo, crystal | | |
| Attuned kyber crystal (owner-only) | ✓ | | ✓ crystal forms | | Owner-only pickup needs two players |
| Hilt parts and loot (emitters, grips, pommels; bad-luck protection) | ✓ | | | | |
| Lightsaber workbench | ✓ | ✓ construct/disassemble returns the same crystal, slot ownership | ✓ screen | | |
| Customisable lightsaber (colour, parts, stats) | ✓ | ✓ stat maths | ✓ ignited, first and third person | | |
| Blocking, parry and bolt deflection | ✓ | | ✓ guard vs poacher | | |
| Crystal Poacher (ranged enemy, tells, bursts, cover) | ✓ | | ✓ fight | | |
| Intro quest (stages Stirring to Complete) | ✓ | ✓ forward-only stages | ✓ through the trial | | |
| VFX/SFX with quality presets, shake and flashing settings | ✓ | | partial (visuals only; CI has no audio) | | `/oldrepublicfx` client command changes settings |
| Single-player | ✓ | | ✓ | | |
| Dedicated server | ✓ | ✓ (game tests run on a dedicated server) | ✓ client joins and pushes | | |

## Supporting work
| Item | Status |
|---|---|
| Balance config (`config/oldrepublic/balance.json`) | ✓ see `docs/BALANCE.md` |
| Asset manifest | ✓ see `docs/ASSET_MANIFEST.md` |
| Known issues | ✓ see `docs/KNOWN_ISSUES.md` |
| Decisions and lore departures | ✓ see `docs/DECISIONS.md` |
| License | ✓ All Rights Reserved, see `LICENSE` |
| Milestone 3 worker guide | ✓ see `docs/WORKER_M3.md` |

## Milestone 2 (Identity and Builds)

Spec: `docs/MILESTONE_2.md`. Merged in commits `61e27b6` to `22d4be6`, with fixes up to `609a5b5`.

**Status: complete.** All CI checks are green: the build, every server and client game test, and `PlaythroughTest` at 41/41 through Chapter Two on the Jedi path. "Playthrough" below means a step that `PlaythroughTest` drives with real inputs. Nothing has been played by hand.

| Feature | Built | Server test | Client test | Played | Notes |
|---|---|---|---|---|---|
| Alignment (-100 to +100, bands, major one-time shifts, minor and severe daily caps, shift log) | ✓ `identity` | ✓ `IdentityGameTests` (bands, clamp, caps, one-time sources) | | | |
| Contextual alignment (helped, defended, cruelty, harmed, killed innocents) | ✓ `identity.ContextualActs` | ✓ `IdentityGameTests` (cap rules only) | | | Detection itself is not driven by a test; `identity.contextualAlignment` turns it off |
| Faction reputation (Jedi Order, Sith Empire, Frontier; five tiers) | ✓ | ✓ `IdentityGameTests` (tiers) | | | |
| Paths and ranks (None, Jedi, Sith, Independent; Initiate/Acolyte, Padawan/Apprentice) | ✓ | ✓ `IdentityGameTests` (path change rules), `StoryGameTests` (trial matrix) | ✓ Padawan through the Trial (playthrough) | | Jedi ↔ Sith only by operator command |
| Mentor trust (per mentor, 0 to 100) | ✓ | ✓ `IdentityGameTests` (clamping) | | | |
| Dialogue engine and screen (data-driven JSON, conditions with reasons, stances, actions, sessions) | ✓ `dialogue`, `client.dialogue` | ✓ `IdentityGameTests` (conditions, stale session, range) | ✓ screenshot; Chapter Two dialogue choices (playthrough) | | 8 dialogue files in `data/oldrepublic/dialogue/` |
| Yielding (Captain, Brenn; spare, execute with confirm strike, flee) | ✓ `identity.Yielding` | ✓ `IdentityGameTests` (clamp, execute timing), `EnemyGameTests` (Captain yields at 25%) | ✓ Captain yield (screenshot, playthrough) | | |
| Frontier Settler (free, captive, fleeing; captives dialogue; thanks and payment loot) | ✓ | | ✓ captives dialogue (screenshot, playthrough) | | Free settlers exist only through `/oldrepublic identity settler free` (see Known issues) |
| Standing tab and alignment / reputation toasts, rank and band banners | ✓ `client.identity` | | ✓ screenshots `oldrepublic_m2_10..17` | | `/oldrepublicfx alignment_toasts on\|off` |
| Skill trees (Universal, Lightsaber, Jedi, Sith; 34 nodes; groups, dormant nodes, granted nodes) | ✓ `skill`, `data/oldrepublic/skill_tree/` | ✓ `SkillGameTests` (data, cycles, purchase matrix, switch, dormant) | ✓ skill screen (playthrough) | | |
| Full respec ("Unburden" at the mentor, meditation stone for Independents) | ✓ | ✓ `SkillGameTests` (rounding, cooldown, granted kept) | | | |
| Ability runtime and four slots (R, Z, V, B; slot 4 at rank 2 or No Master) | ✓ `ability`, `client.ability` | ✓ `SkillGameTests` (slots, combat lock, slot 4) | ✓ key-clash check | | |
| Force Push on the ability framework, branch choice moved into the tree, save migration | ✓ | ✓ `SkillGameTests` (migration), `ForceGameTests` | | | `ChooseBranchPayload`, `BranchChoice`, `PushInfoPayload` removed |
| Redesigned Force screen (tabs, tree view, detail panel, slot bar, respec card) | ✓ `client.skill` | | ✓ screenshots `oldrepublic_m2_30..37` | | |
| HUD ability sockets (cooldown sweep, low-energy rim, charge/channel arc, refused hatch) | ✓ `client.hud.ForceHud` | | ✓ screenshot | | |
| Force Pull (Disarming Pull, Reeling Pull) | ✓ `force.pull` | ✓ `ForceGameTests` (speed, brake, aim assist, disarm) | ✓ screenshot; a quick tap as a real short hold (playthrough) | | |
| Force Jump (Crushing Descent, Soaring Leap glide) | ✓ `force.jump` | ✓ `ForceGameTests` | ✓ screenshots; playthrough | | `allow-flight=false` behaviour unchecked |
| Force Heal (Jedi; Shared Light, Serene Ward) | ✓ `force.heal` | ✓ `ForceGameTests` | ✓ screenshot; granted by the Trial (playthrough) | | |
| Force Lightning (Sith; Chain, Consuming) | ✓ `force.lightning` | ✓ `ForceGameTests` | ✓ screenshots, including the frame after stop | | |
| Alignment gates on side powers (opposed cost x1.5, refused) and `unbound` | ✓ `ability.AbilityCosts` | ✓ `ForceGameTests`, `SkillGameTests` | | | |
| Shared saber stamina, light combo, heavy attack, lunge | ✓ `saber.SaberCombat`, `SaberTechniques` | ✓ `SaberGameTests` | | | Feel and timing need play |
| Saber forms Soresu and Ataru (form key, salute, aerial strike) | ✓ `saber.SaberForms` | ✓ `SaberGameTests` (modifiers) | | | |
| Lightsaber passives (Steady Guard, Riposte, Deflection Control) | ✓ `saber.SaberPassives` | | | | |
| Saber Throw (Ricochet, Rending Arc; no item duplication) | ✓ `saber.throwing` | ✓ `SaberGameTests` (no duplication) | | | |
| NPC saber guard, parry and broken guard; duelist AI | ✓ `saber.duel` | ✓ `SaberGameTests` | | | |
| Sparring (yield, parries, technique hits; no death risk) | ✓ `saber.duel.Spar` | ✓ `SaberGameTests` (spar never kills) | ✓ the Trial of the Blade spar (playthrough) | | In memory only |
| Sith Acolyte (Force Shove, push clash; hostile and rival modes) | ✓ `enemy.SithAcolyte` | ✓ `EnemyGameTests` (push clash) | | | Encounter and command spawns only |
| Poacher Captain (vibroblade, overhead heavy, pistol, yields) | ✓ `enemy.PoacherCaptain` | ✓ `EnemyGameTests` | ✓ fight and yield (playthrough) | | |
| Chapter Two: Two Voices dream and the Calling to the meeting point | ✓ `quest.story.TwoVoices`, `MeetingSites` | ✓ `StoryGameTests` (stages, site checks) | ✓ dream (playthrough) | | |
| The Crossing (both mentors, join Jedi / Sith / walk alone, late join once) | ✓ `quest.story.Crossing` | ✓ `StoryGameTests` (one choice, decline, late join) | ✓ screenshot; joining the Jedi (playthrough) | | |
| Mentors and camps (owned, invulnerable, leashed, lost-mentor respawn, summon) | ✓ `mentor`, `quest.story.StoryMentors` | ✓ `StoryGameTests` (one mentor per owner) | ✓ mentor camp (playthrough) | | |
| Lessons (The Reach / What Is Owed; The Still Blade / Relentless) | ✓ `quest.story.Lessons` | ✓ `StoryGameTests` (grants, no refund) | ✓ the Jedi lessons (playthrough) | | |
| Smoke on the Ridge (raider camp, crew, Captain and captives choices, ledger) | ✓ `quest.story.Reckoning` | ✓ `StoryGameTests` (completion logic, abandon at 128) | ✓ screenshots (raider camp, Captain yield); raider camp, Captain and captives (playthrough) | | |
| Rank trials (Trial of the Blade, Trial of Ascension with Brenn's fate), No Master | ✓ `quest.story.Trials` | ✓ `StoryGameTests` (requirements) | ✓ the Trial of the Blade (playthrough) | | The Trial of Ascension and No Master are not driven by a client test |
| Daily sessions, Atone and Embrace | ✓ | ✓ `StoryGameTests` (sessions keep the path open) | | | |
| Chapter Two journal (chapter headers, 25 entries, 9 codex entries) and Poachers' Ledger | ✓ | | ✓ screenshot; journal (playthrough) | | Ledger not covered by a test |
| `story.placeStructures=false` (entities only) | ✓ | ✓ `StoryGameTests` | | | |
| M2 VFX and SFX (55 new sound events, 11 new particle types) | ✓ | | partial (visuals only; CI has no audio) | | Loop stopping not checked by a test |
| Operator commands (`identity`, `dialogue`, `skills`, `story`, `saber`, `enemy spawn`) | ✓ | used by the tests | used by the tests | | |
| Client screenshot test `IdentityBuildsClientTest` | ✓ | | ✓ runs the five `m2/*ClientSteps` | | |
| PlaythroughTest extension (Chapter Two with real inputs) | ✓ Jedi path, steps 19-34 | | ✓ 41/41 (every story beat by real input; walks that stop short teleport to the talking spot) | | Spec 16.3; the Sith and Independent runs are not written (see Known issues) |
| Dedicated server | ✓ (no client classes in `src/main`) | ✓ (server game tests run on a dedicated server) | | | Two players at separate Crossings not tested |

## Milestone 3 (Exploration)

Spec: `docs/MILESTONE_3.md` (features from section 1; owners from 15.2: W1 world, W2 story, missions and identity, W3 Force, skills and holocrons, W4 troopers and armour, W5 boss, constructs and saber). Phase 0 (the lead's contracts, stubs, tags, sound events and tools) is in progress. **Every feature below is not started.** "Cut n" is the feature's place in the cut order of section 1.

| Feature | Spec | Owner | Status | Built | Server test | Client test | Played | Notes |
|---|---|---|---|---|---|---|---|---|
| Chapter Three "The Ones That Sing": dream, openings, first echo, four leads, convergence, confrontation, aftermath and report | 6 | W2 | not started | | | | | |
| Path openings teaching Force Sense: the Jedi lesson "Listen", the Sith lesson "The Hunt", Courier's Fall for Independents | 6.2 | W2 | not started | | | | | |
| Chapter Two cameos (spared characters return) | 5.4 | W2 | not started | | | | | |
| Jedi Ruin "Watchers' Rest" (forests): courtyard, stair, Listening Hall, caches, garrison | 3.2, 3.4 | W1 | not started | | | | | |
| Sith Tomb (deserts and badlands): gate, processional, burial chamber, dig camp, garrison | 3.2, 3.4 | W1 | not started | | | | | |
| Wreck Field (plains): crashed pre-Treaty frigate, salvage camp | 3.2, 3.4 | W1 | not started | | | | | Cut 6 |
| Republic Forward Post and Imperial Listening Post (worldgen, shared runtime fallback in `WorldSites`) | 3.5 | W1 | not started | | | | | |
| Echo Shrines (old-world fallback, one per needed kind) and echo cairns | 3.6 | W1 | not started | | | | | |
| Mission camps (six per-player mission sites) | 3.6, 5.4 | W2 | not started | | | | | |
| Garrisons from `site_spawner` blocks | 3.2 | W1 | not started | | | | | Squads through W4's `Troopers` |
| Structure locating (`StructureLocator`) and `/oldrepublic site` commands | 3.8 | W1 | not started | | | | | |
| Environments: ambience beds, weathering and particles per structure family | 3.7 | W1 | not started | | | | | |
| Harvestable resources: `lumen_moss`, `funerary_urn`, `wreck_salvage` | 3.7 | W1 | not started | | | | | |
| Sith tomb vent hazards | 3.3 | W1 | not started | | | | | |
| Discovery: presence, first-visit XP and toast | 3.7 | W1, W2 | not started | | | | | |
| Puzzle kit: Force-reactive blocks driving hidden redstone (veiled stone, counterweights, step stones, urns, pull anchors, rubble, seals) | 3.3, 3.4 | W1 | not started | | | | | |
| Reactive-block dispatch from Push, Pull and Lift | 10.6 | W3 | not started | | | | | |
| World holocrons (4 Jedi, 4 Sith, 4 Wayfarer) and pedestals (one per player per pedestal) | 4.1-4.3 | W3 (pedestal block W1) | not started | | | | | |
| Gatekeeper projection and attunement | 4.4 | W3 | not started | | | | | |
| Opposed-side holocrons: Seal, Break or Listen | 4.5 | W3 | not started | | | | | |
| Return or carry a holocron | 4.6 | W3 | not started | | | | | |
| Progression from holocrons: milestones, a skill point every second holocron, echoes and shards | 4.7 | W3 | not started | | | | | |
| The Archive: 3 Archive holocrons and the hoard choice | 4.8 | W3 | not started | | | | | |
| Holocrons journal tab | 4.9 | W3 | not started | | | | | |
| The Republic faction and kill reputation | 5.1, 5.2 | W2 | not started | | | | | |
| Trooper attitudes (Hostile, Wary, Neutral, Allied) | 5.2, 7.5 | W4 | not started | | | | | |
| Story missions (two per army, each a lead) | 5.4 | W2 | not started | | | | | |
| Bounty terminals and contracts (five types, daily soft cap) | 5.5 | W2 (terminal block W1) | not started | | | | | |
| Commendations (buy trooper armour) | 5.6 | W2, W4 | not started | | | | | |
| Mission board, HUD tracker and Missions tab | 5.7 | W2 | not started | | | | | |
| Rank 3: Jedi Knight and Sith Lord (the Collector fight as the trial) | 5.8 | W2 | not started | | | | | |
| The Pathfinder title (Independents) | 5.8 | W2 | not started | | | | | |
| Republic troopers (Rifleman, Field Medic, Sergeant) | 7.1, 7.2 | W4 | not started | | | | | |
| Imperial troopers (Rifleman, Grenadier, Sergeant) | 7.1, 7.3 | W4 | not started | | | | | |
| Officers (non-combat) | 7.4 | W4 | not started | | | | | |
| Squads, cold peace, incidents, downed troopers and escorts, barks | 7.1, 7.5 | W4 | not started | | | | | |
| Natural patrols | 7.6 | W4 | not started | | | | | Cut 3 |
| Ruin Sentinel (Watcher and Tomb variants) | 7.7 | W5 | not started | | | | | |
| Crystal poachers working every ruin and tomb (Vasch's diggers) | 7.8 | W4 | not started | | | | | |
| Orren Vasch, the Collector: three phases (The Archivist, The Singing Wall, Unkept) in the Reliquary | 8.1-8.5 | W5 | not started | | | | | |
| Reliquary placement, scaling by player count, reset and session | 8.2, 8.6 | W5 | not started | | | | | |
| Boss bar and boss music | 8.5 | W5 | not started | | | | | |
| Ending choices and the custody, execution or release scenes | 8.7 | W5 (dialogue W2) | not started | | | | | |
| Collector rewards | 8.8 | W2 | not started | | | | | |
| Rematch "Echo of the Collector" and Unchained mode | 8.9 | W5 (rewards W2) | not started | | | | | Cut 4 |
| Republic trooper armour (4 pieces) | 9 | W4 | not started | | | | | |
| Sith trooper armour (4 pieces) | 9 | W4 | not started | | | | | |
| Jedi robes (4 pieces, plus the Knight robe) | 9 | W4 | not started | | | | | |
| Sith robes (4 pieces, plus the Lord robe) | 9 | W4 | not started | | | | | |
| Armour traits, set bonuses, recipes and repair | 9.1-9.3 | W4 | not started | | | | | |
| Robe custom models | 9.4 | W4 | not started | | | | | Cut 5 |
| Uniform disguise | 9.5 | W4 | not started | | | | | Stretch goal, cut 1 |
| Force Sense (Universal, own key H, Danger Sense) | 10.1 | W3 | not started | | | | | |
| Telekinetic Lift (Independent signature) | 10.2 | W3 | not started | | | | | Branches `lift_suspend`, `lift_hurl`: cut 2 |
| Exploration and Survival skill tree (fifth tab) | 10.3 | W3 | not started | | | | | |
| Holocron nodes and rank 3 nodes | 10.4 | W3, W5 | not started | | | | | |
| Saber additions: Archivist pommel, Reliquary emitter, singing crystal (cosmetic) | 10.7 | W5 | not started | | | | | |
| Controls and HUD: Sense key, mission tracker, boss bar, trooper barks, Holocrons and Missions journal tabs | 11 | W2, W3, W4, W5 | not started | | | | | |
| Persistence, multiplayer and dedicated server | 12 | all | not started | | | | | |
| M3 VFX and SFX (121 new sound events, 23 new particle types) | 13 | all | not started | | | | | Placeholder audio for every new sound id is generated in Phase 0 |
| `ExplorationClientTest` (worldgen world, persistence, dedicated server) | 17.2 | lead | not started | | | | | |
| PlaythroughTest extension (Chapter Three, Jedi run; Sith run) | 17.3 | lead | not started | | | | | The Independent run is cut 7 |

## Later milestones (from the design brief and spec section 1, not started)

- **Milestone 4 (Broader Content):**
  - redemption and corruption questlines (path change in play);
  - inhabited Jedi enclave and Sith academy hubs;
  - vendors and reputation shops;
  - armour beyond the four M3 sets, and armour upgrades and mods;
  - player blasters and vibroweapons;
  - crafting specialisation;
  - planet-themed dimensions and transport ships;
  - the remaining powers: Speed, Stasis, Barrier, Protect, Valor, mind influence, Choke, Drain, Fear, Rage and Crush;
  - more saber forms and double-bladed sabers.
- **Milestone 5 (Advanced Features):**
  - ranks above 3 (Master, Darth);
  - endgame encounters and raid-size bosses;
  - further faction stories (Mandalorians, Hutt cartels);
  - flight and large-scale battles.

Gabriel's Milestone 3 queue (Republic and Sith trooper mobs; Republic trooper armour, Sith trooper armour, Jedi robes and Sith robes) is in the Milestone 3 table above. `DECISIONS.md` records the move of these armour sets from Milestone 4.
