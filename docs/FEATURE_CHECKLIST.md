# Feature checklist

Status per feature, with how it has been checked. Legend:
- **Built**: code and data exist and compile on CI.
- **Server test**: covered by a server game test (`./gradlew build`).
- **Client test**: driven by the client game tests, with screenshots reviewed (`./gradlew runClientGameTest`). `ClientSmokeTest` uses commands to reach each feature; `PlaythroughTest` plays the whole chapter on a normal-terrain world with real inputs (it teleports between distant places and logs every shortcut).
- **Played**: hand-tested in game by a person. Nothing is at this level yet.

Milestone 2 uses a different table, because none of it has a CI result yet:
- **In code**: the code and data are in the repository (merged in commits `61e27b6` to `22d4be6`). A green CI compile has not been recorded in this file yet.
- **Tests written**: the automated tests that exist for the feature. Their status is "pending CI" until a CI run passes them; none of them counts as a check yet.

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

## Milestone 2 (Identity and Builds)

Spec: `docs/MILESTONE_2.md`. Every row: **automated test status: pending CI**. Nothing has been played.

| Feature | In code | Tests written | Played | Notes |
|---|---|---|---|---|
| Alignment (-100 to +100, bands, major one-time shifts, minor and severe daily caps, shift log) | ✓ `identity` | `IdentityGameTests` (bands, clamp, caps, one-time sources) | | |
| Contextual alignment (helped, defended, cruelty, harmed, killed innocents) | ✓ `identity.ContextualActs` | `IdentityGameTests` (cap rules only) | | Detection itself not driven by a test; `identity.contextualAlignment` turns it off |
| Faction reputation (Jedi Order, Sith Empire, Frontier; five tiers) | ✓ | `IdentityGameTests` (tiers) | | |
| Paths and ranks (None, Jedi, Sith, Independent; Initiate/Acolyte, Padawan/Apprentice) | ✓ | `IdentityGameTests` (path change rules), `StoryGameTests` (trial matrix) | | Jedi ↔ Sith only by operator command |
| Mentor trust (per mentor, 0 to 100) | ✓ | `IdentityGameTests` (clamping) | | |
| Dialogue engine and screen (data-driven JSON, conditions with reasons, stances, actions, sessions) | ✓ `dialogue`, `client.dialogue` | `IdentityGameTests` (conditions, stale session, range); client screenshot | | 8 dialogue files in `data/oldrepublic/dialogue/` |
| Yielding (Captain, Brenn; spare, execute with confirm strike, flee) | ✓ `identity.Yielding` | `IdentityGameTests` (clamp, execute timing), `EnemyGameTests` (Captain yields at 25%) | | |
| Frontier Settler (free, captive, fleeing; captives dialogue; thanks and payment loot) | ✓ | client screenshot (captives dialogue) | | Free settlers exist only through `/oldrepublic identity settler free` (see Known issues) |
| Standing tab and alignment / reputation toasts, rank and band banners | ✓ `client.identity` | client screenshots `oldrepublic_m2_10..17` | | `/oldrepublicfx alignment_toasts on\|off` |
| Skill trees (Universal, Lightsaber, Jedi, Sith; 34 nodes; groups, dormant nodes, granted nodes) | ✓ `skill`, `data/oldrepublic/skill_tree/` | `SkillGameTests` (data, cycles, purchase matrix, switch, dormant) | | |
| Full respec ("Unburden" at the mentor, meditation stone for Independents) | ✓ | `SkillGameTests` (rounding, cooldown, granted kept) | | |
| Ability runtime and four slots (R, Z, V, B; slot 4 at rank 2 or No Master) | ✓ `ability`, `client.ability` | `SkillGameTests` (slots, combat lock, slot 4); client key-clash check | | |
| Force Push on the ability framework, branch choice moved into the tree, save migration | ✓ | `SkillGameTests` (migration), `ForceGameTests` | | `ChooseBranchPayload`, `BranchChoice`, `PushInfoPayload` removed |
| Redesigned Force screen (tabs, tree view, detail panel, slot bar, respec card) | ✓ `client.skill` | client screenshots `oldrepublic_m2_30..37` | | |
| HUD ability sockets (cooldown sweep, low-energy rim, charge/channel arc, refused hatch) | ✓ `client.hud.ForceHud` | client screenshot | | |
| Force Pull (Disarming Pull, Reeling Pull) | ✓ `force.pull` | `ForceGameTests` (speed, brake, aim assist, disarm); screenshot | | |
| Force Jump (Crushing Descent, Soaring Leap glide) | ✓ `force.jump` | `ForceGameTests`; screenshots | | `allow-flight=false` behaviour unchecked |
| Force Heal (Jedi; Shared Light, Serene Ward) | ✓ `force.heal` | `ForceGameTests`; screenshot | | |
| Force Lightning (Sith; Chain, Consuming) | ✓ `force.lightning` | `ForceGameTests`; screenshots incl. the frame after stop | | |
| Alignment gates on side powers (opposed cost x1.5, refused) and `unbound` | ✓ `ability.AbilityCosts` | `ForceGameTests`, `SkillGameTests` | | |
| Shared saber stamina, light combo, heavy attack, lunge | ✓ `saber.SaberCombat`, `SaberTechniques` | `SaberGameTests` | | Feel and timing need play |
| Saber forms Soresu and Ataru (form key, salute, aerial strike) | ✓ `saber.SaberForms` | `SaberGameTests` (modifiers) | | |
| Lightsaber passives (Steady Guard, Riposte, Deflection Control) | ✓ `saber.SaberPassives` | | | |
| Saber Throw (Ricochet, Rending Arc; no item duplication) | ✓ `saber.throwing` | `SaberGameTests` (no duplication) | | |
| NPC saber guard, parry and broken guard; duelist AI | ✓ `saber.duel` | `SaberGameTests` | | |
| Sparring (yield, parries, technique hits; no death risk) | ✓ `saber.duel.Spar` | `SaberGameTests` (spar never kills) | | In memory only |
| Sith Acolyte (Force Shove, push clash; hostile and rival modes) | ✓ `enemy.SithAcolyte` | `EnemyGameTests` (push clash) | | Encounter and command spawns only |
| Poacher Captain (vibroblade, overhead heavy, pistol, yields) | ✓ `enemy.PoacherCaptain` | `EnemyGameTests` | | |
| Chapter Two: Two Voices dream and the Calling to the meeting point | ✓ `quest.story.TwoVoices`, `MeetingSites` | `StoryGameTests` (stages, site checks) | | |
| The Crossing (both mentors, join Jedi / Sith / walk alone, late join once) | ✓ `quest.story.Crossing` | `StoryGameTests` (one choice, decline, late join); screenshot | | |
| Mentors and camps (owned, invulnerable, leashed, lost-mentor respawn, summon) | ✓ `mentor`, `quest.story.StoryMentors` | `StoryGameTests` (one mentor per owner) | | |
| Lessons (The Reach / What Is Owed; The Still Blade / Relentless) | ✓ `quest.story.Lessons` | `StoryGameTests` (grants, no refund) | | |
| Smoke on the Ridge (raider camp, crew, Captain and captives choices, ledger) | ✓ `quest.story.Reckoning` | `StoryGameTests` (completion logic, abandon at 128); screenshots (raider camp, Captain yield) | | |
| Rank trials (Trial of the Blade, Trial of Ascension with Brenn's fate), No Master | ✓ `quest.story.Trials` | `StoryGameTests` (requirements) | | |
| Daily sessions, Atone and Embrace | ✓ | `StoryGameTests` (sessions keep the path open) | | |
| Chapter Two journal (chapter headers, 25 entries, 9 codex entries) and Poachers' Ledger | ✓ | client screenshot (journal) | | Ledger not covered by a test |
| `story.placeStructures=false` (entities only) | ✓ | `StoryGameTests` | | |
| M2 VFX and SFX (55 new sound events, 11 new particle types) | ✓ | client screenshots (visuals only; CI has no audio) | | Loop stopping not checked by a test |
| Operator commands (`identity`, `dialogue`, `skills`, `story`, `saber`, `enemy spawn`) | ✓ | | | Used by the tests |
| Client screenshot test `IdentityBuildsClientTest` | ✓ | runs the five `m2/*ClientSteps` | | |
| PlaythroughTest extension (Chapter Two with real inputs) | written (Jedi path, steps 19-34), not run on CI | | | Spec 16.3; Sith and Independent runs not written; see Known issues |
| Dedicated server | ✓ (no client classes in `src/main`) | server game tests run on a dedicated server | | Two players at separate Crossings not tested |

## Later milestones (from the design brief, not started)
Milestone 3 and later: redemption and corruption questlines (path change in play), Knight/Master and Lord/Darth ranks, holocrons, faction missions, the Republic faction, more Force powers (Sense, Speed, Lift, Stasis, Choke, Drain and others), more saber forms, double-bladed and dual sabers, more structures (enclave, academy), more enemies and the first major boss, ships.
