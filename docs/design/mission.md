# Missions: story missions, contracts, the board, the tracker and discovery

Milestone 3 spec: `docs/MILESTONE_3.md` sections 3.6 (per-player runtime sites), 3.7 (discovery), 5.2 (kill
reputation), 5.3 to 5.7 (givers, story missions, contracts, commendations, tracking), 6.9 (Peaceful), 11.2 and 11.3
(tracker, Missions tab) and 13.3 (effects). This page describes how the mission feature implements them.

Code: `com.oldrepublic.mission` (+ `mission.network`), `identity.KillReputation`, `identity.RepublicSeed`,
`client.mission`. Data: `data/oldrepublic/mission/*.json`, the six dialogues listed below, the loot tables
`gameplay/frontier_contract` and `gameplay/r1_supply`. Tunables: `config/oldrepublic/balance.json`, section `missions`
(`MissionBalance`, read through `MissionFeature.balance()`).

## Storage

One persistent attachment `oldrepublic:missions` (`MissionAttachments.MISSIONS`, `MissionData`), copied on death and
synced to its owner only. Like the other M3 attachments it is sealed: `MissionService.commit` stores
`data.copy().seal()`, and the sync sends the frozen copy, never the live object.

| Field | Meaning | Bound |
|---|---|---|
| `active` | The one active mission: `id`, `base` (the contract a duty runs), `kind`, `stage` (`PLACING`, `TRAVEL`, `ACTIVE`, `DECIDE`, `RETURN`, by ordinal, append only), `site`, `progress`, `target`, `extra` (R1's wounded tended), `started_day`, `seed`, `issuer`, `board` (where it was taken), `flags`, `site_kind` | one |
| `story_done` | Story missions done | 8 |
| `contract_day`, `contracts_today` | Today's completed contracts and duties (the soft cap) | |
| `board_day`, `used_offers` | Today's used offers: terminal position and offer index | 16 |
| `own_side` | Completed contracts per issuer key (faction id, or `mentor` for duties) | 4 keys |
| `duty_day` | The day the last mentor duty was taken | |
| `duties_today` | Duties taken on `duty_day` (a set day counts at least one) | |
| `discovered` | `SiteKind` ids seen at least once | 16 |
| `surveyed` | Heart positions already surveyed (oldest dropped) | 32 |
| `pull_until` | Game time the interrogation (or freed settler) pull lasts until | |
| `cameos` | One-time Chapter Two cameos already played (`settler`) | 8 |

Every field is optional in the codec, so a save without mission data loads empty, and lists load bounded.

**Days.** Every daily rule (offers, soft cap, duties, the kill-reputation cap) takes the day index as an argument:
`IdentityRules.dayIndex(level.getGameTime())` (game time / 24,000, the M2 convention), never `getDayTime`, which
`/time set` moves. The pure rules are in `MissionRules`, `Objectives` and `KillReputation`, so game tests pass a day.

## Definitions (`data/oldrepublic/mission/<id>.json`)

Loaded on server start and `/reload` (`MissionLoader`, Fabric's data reload listener); only the `oldrepublic`
namespace is read; a broken file is logged and skipped. Definitions never leave the server. The format is in the
Javadoc of `MissionDefinition`.

| Id | Kind | Issuer | Objective | Site (`counts.footprint`) | Counts |
|---|---|---|---|---|---|
| `r1_silent_patrol` | story | republic | rescue | `mission/patrol_ambush` (13) | 3 sentinels, 3 downed |
| `r2_credits_from_the_core` | story, after R1 | republic | recover `archive_record` | `mission/smuggler_cache` (13) | crew 4 |
| `i1_names` | story | sith_empire | recover `fence_book` | `mission/fence_camp` (15) | crew 4 |
| `i2_border_incident` | story, after I1 | sith_empire | yield | `mission/survey_post` (15) | the squad, 1 downed Imperial |
| `clear_camp` | contract | frontier | clear | `mission/raider_camp_small` (11) | crew 3, 4 from level 9 |
| `survey` | contract | terminal | survey | worldgen ruin, tomb or wreck | |
| `secure_site` | contract | terminal | secure | worldgen ruin, tomb or wreck | 3 sentinels or 4 scavengers |
| `patrol_intercept` | contract | terminal | intercept | `mission/skirmish_line` (17) | 3 kills, +1 rifleman from level 11 |
| `watch_duty` | duty (Jedi) | mentor | one of `clear_camp`, `survey`, `secure_site` | as the base | |
| `tribute` | duty (Sith) | mentor | as above | as the base | |

Story missions carry `gate: {"c3:stage": "summons"}`, `lead: true` and their journal `entry`. `issuer: "terminal"`
means the board's army; `clear_camp` names `frontier` because the Frontier issues it at either terminal. The four
story missions' choices hold their values (reputation per faction, a major alignment shift, contextual acts,
commendation count and army, loot, codex, Resonance, whether the mission item is handed over, and dialogue conditions
for turn-ins) so balance changes need no code.

## Taking work

### Officers (`officer_republic.json`, `officer_imperial.json`)

Opened by W4's officer troopers; the speaker plate takes the officer's own name. `start` routes on the tier with the
officer's army: Neutral and up to `greet`, Distrusted to `wary`, otherwise `hostile` (leave only).

- `greet` (at most 6 visible): work, about the patrols, return a holocron (`holocron:carrying any`, hidden when
  unmet), trade 2 shards (`shards:exchange`), report (Independent, `c3:report_ready officer`, hidden), leave.
- `wary`: make amends (return a holocron, or `shards:amends`), report, leave.
- `work`: the turn-ins of a mission waiting at this officer (`mission:ready`), the army's story missions
  (`mission:available` shows the reason when locked), the active mission's orders (abandon, confirmed), the terminal
  hint, back. The Imperial officer also takes R2's **sell** turn-in and I1's three turn-ins (doctored list hidden
  without `story:flag ledger_found`); I2's brief has two wordings by `story:flag brenn_spared`.

### Mission dialogue conditions and actions (`MissionDialogue`)

`mission:available <id|duty>`, `mission:active <id|any|duty>`, `mission:ready <id|duty>`,
`mission:officer <republic|sith_empire>`; actions `mission:start <id|duty>`, `mission:turn_in <choice>`,
`mission:abandon`, `mission:leave_wounded`, `mission:burn_book`. Reasons are lang keys
`message.oldrepublic.mission.req.*`. A story mission is refused when done, when another mission is active, when the
one before it is not done, below Neutral with its army, or when its gate fails. Mentors give today's duty with
`mission:available duty` / `mission:start duty` (A's `c3_hub`).

### The board (`MissionBoard`, `BoardScreen`)

A bounty terminal (W1) calls `Missions.openBoard(player, terminal, army)`. The server starts two locator searches
(a survey site and a secure site, cached answers come at once) and sends `mission_board` when both answered or after
60 ticks. Offers:

- 2 per terminal, player and day: the day's seeded order of `clear_camp`, `survey`, `secure_site`,
  `patrol_intercept` (`MissionRules.boardSeed(day, uuid, terminal)`, Fisher-Yates with `SplittableRandom`), skipping
  survey and secure when no site exists. A used offer keeps its place and shows as taken; it is never replaced or
  offered again that day.
- Issuer: the Frontier for `clear_camp`, otherwise the terminal's army; each needs that faction ≥ Neutral.
- Each card shows the issuer emblem, title, one of 3 briefings per type and issuer
  (`mission.oldrepublic.contract.<type>.<republic|imperial|frontier>.<0..2>`), the objective, the rewards on two
  lines (XP and standing, then the commendation or the Frontier's supplies; past the cap "XP only"), and
  "Full pay n/3 today". Buttons: Accept (one active mission at a time), or Abandon on the active mission's card.
- `mission_action (terminal, action, offer)` is validated again on the server against the board last sent: the
  same terminal and day, within `boardReach` (8) blocks, the offer not used, no active mission, the gate.

### Duties

`dutiesPerDay` (1) per day (`duty_day`, `duties_today`; `MissionRules.dutyOpen`). Today's type is the first of a seeded order (`MissionRules.dutyOrder`) whose site
exists: Jedi `watch_duty` and Sith `tribute` frame the same three base contracts with the mentor's voice
(`mission.oldrepublic.contract.<type>.<jedi|sith>.<n>`).

## Sites and objectives

A contract or story mission with a template gets a **runtime camp** (`MissionSites.place`):
`RuntimeSites.placeSurface` on `siteRings` (96, 128, 144) and `siteFallback` (48, 80), the template's ground layer
set into the terrain (`buried` 1), `siteSeparation` (48) from the other online players' story and mission sites (`StoryService.otherSites`: their M2
meeting points, camps and raider camps, Courier's Fall, Echo Shrines, Reliquary heart and active mission camps, 3.6),
and `story.maxSlope`. While
placing the stage is `PLACING`; a failed search retries every `siteRetryTicks` (200) and after
`siteForwardFallbackAfter` (3) failures falls back to the M2 meeting search straight ahead (the camp is stamped when
it fits, otherwise the mission runs around the bare spot). With `story.placeStructures` off no blocks are placed.

Survey and secure use worldgen sites found by the locator (`surveyRadius` 512, `secureRadius` 384); a survey skips
hearts the player already surveyed.

The objective's people spawn once, when the owner comes within `siteSpawnDistance` (48), at the template's anchors
(read by name through `SiteAnchors`, with a ring fallback). Every spawn is tagged `oldrepublic.mission`,
`oldrepublic.mission.<id>`, an owner tag and `oldrepublic.mission_role.<role>`, and is persistent. A lost mission
item is issued again at the site while the mission is active. When a mission ends, standing troopers walk off (W4
removes them out of sight), other fighters are removed, and untended downed troopers start to bleed out after
`downedBleedTicks`.

| Objective | Spawns | Counts | Completes |
|---|---|---|---|
| `clear` | the crew (`Poachers.spawnCrew`) | each death | all down |
| `recover` | the crew and the chief (R2: by Hessa's fate, below) or the fence Dovan Lisk (`PoacherCaptain`, yields at `fenceYield`; R2's Rell Okkar at `chiefYield`) | the item taken from the crate or the book | at the officer's turn-in (`RETURN`) |
| `rescue` | 3 bound Watcher sentinels and 3 downed Republic troopers | the guards, then the wounded | all tended (or "leave them", confirmed) |
| `survey` | nothing | Sense within `surveyHeartRange` (8) of the heart | at once |
| `secure` | 3 bound sentinels at a ruin or tomb, 4 scavengers at a wreck, at `SURFACE_GUARD` | each death | all down |
| `intercept` | the other army's squad, incident `ARMY_AND_OWNER`, sergeant yields at `interceptYield` (`yield_intercept`) | each death | 3 down and the sergeant decided |
| `yield` | I2's Republic recon squad, incident `ARMY_AND_OWNER`, Sergeant Aldo Brannick yields at `borderYield` (`yield_border`); a downed Imperial | each death | the sergeant's fate |

Squads are two riflemen, a medic and the sergeant, plus a rifleman from level `squad_extra_level` (11).

**Peaceful** (`Objectives.resolveOnArrival(objective, target, peaceful)`): bound guards crumble and crews flee, so
`clear` and `secure` complete on arrival, R1 goes straight to the wounded and the chief or fence is gone; trooper
squads still spawn but stand down with the sergeant yielding at once. A survey is never combat.

## Story missions (`StoryMissions`)

Each runs once, from Chapter Three's SUMMONS, pays `storyXp` (1,200) and `storyCommendations` (2) of the giver's
army unless the choice says otherwise, applies the choice once (`Identity.recordChoice("mission.<short>.<choice>")`
guards it; reputation is one-shot per faction and source), unlocks its journal entry, adds the lead
`mission:<id>`, and shows the `mission` banner (`lesson_complete` variant 4, `mission.complete`). For a Jedi every
Republic gain from Republic work (a Republic-issued story mission or contract; not I2's spare) also pays
`jediOrderShare` (0.5) as Jedi Order standing.

| Mission | Choice | Values (from the JSON) |
|---|---|---|
| R1 | **tend** (all 3 tended: sneak-use kneel `tendTicks` 60, or Force Heal within `healTendRange` 4) | Republic +100, `HELPED_OTHER` ×3 |
| R1 | **leave** (the supply crate, confirmed in `r1_leave_confirm`) | Republic -50, `CRUELTY`, loot `gameplay/r1_supply` (6 durasteel) |
| R2 | **return** (Republic officer) | Republic +100, light +3, the record handed over |
| R2 | **sell** (Imperial officer) | Sith Empire +60, Republic -100, 3 Imperial commendations instead |
| R2 | **keep** | Republic -20, codex `vasch_records`, Resonance `vasch_records` +100 |
| I1 | **hand_over** | Sith Empire +120, Frontier -150, dark -6 |
| I1 | **burn** (the camp's fire with the book in hand, confirmed in `i1_burn_confirm`, or at the officer) | Sith Empire -60, Frontier +80, light +6, no commendations |
| I1 | **doctored** (only with `ledger_found`) | Sith Empire +60, codex `doctored_list` |
| I2 | **spare** | Republic +40, Sith Empire -20, light +4 |
| I2 | **handed_over** | Sith Empire +80, Republic -60, dark -4; two Imperial escorts (`Troopers.spawnEscort`) come from the post's edge and walk him off; all three are removed once out of the owner's sight after a short walk, or after `escortDespawnTicks` (300) |
| I2 | **execute** | Sith Empire +40, Republic -100, dark -8 |
| I2 | tend the downed Imperial (a bonus, once) | Sith Empire +20 |

If I2's sergeant flees or dies undecided, the mission completes with the base reward and no choice.
`StoryMissions.yieldChoice` maps the yield outcomes (`SPARED`, `HANDED_OVER`, `EXECUTED`) to I2's choices.

### Chapter Two cameos (`Cameos`)

- **R2's chief** by `StoryData.captainFate()` (`MissionRules.r2Chief`): spared, Hessa Dray steps out when the owner
  comes within 12 blocks, yields at once and hands over the record; executed, her lieutenant Kesh Varro fights to
  the end; anything else, Rell Okkar as written. Each adds a note under the journal entry.
- **Freed captives**: on the first visit to a Republic outpost, one freed settler waits beside the officer and starts
  the 12,000-tick pull toward an unused chamber (played once, `cameos`).
- **Brenn spared**: I2's brief names him (the officer dialogue's flag split); the outpost cameo itself is A's.

## Contracts: pay and limits

- XP: `min(contractXpCap 900, contractXpBase 250 + contractXpPerLevel 40 × level)`.
- In full: issuer +`contractRep` (25) (for a Jedi, Republic contracts also pay Jedi Order +12), and 1 commendation
  of the issuing army, or the Frontier loot `gameplay/frontier_contract` (3 durasteel, lumen thread or grave silk,
  2 to 4 emeralds) for `clear_camp`.
- Duties: XP, mentor trust +`dutyTrust` (5), own faction +`dutyRep` (25), no commendation.
- **Soft cap:** the first `fullPayPerDay` (3) contracts and duties of a day pay in full; later ones pay
  ×`overCapMultiplier` (0.25) XP and nothing else.
- Abandoning pays nothing and frees nothing: the offer stays used for the day.
- Own side (rank 3, `Missions.ownSideContracts`): Jedi count Republic contracts and duties, Sith Imperial contracts
  and duties, Independents everything. Contracts past the soft cap still count.
- The first contract completed during LEADS adds A's `contract` lead, once (`ChapterThreeService.onContractCompleted`).

## Kill reputation (5.2)

`MissionHooks.onTrooperKilled` (routed from W4's trooper death through `QuestHooks`) applies
`KillReputation.apply(player, army, wasHostile, day)`; `wasHostile` is the lead's `Attitudes.hostileSnapshot`. See
`docs/design/identity.md`.

## Discovery (`Discovery`)

The first time a player enters a site piece of a kind (mission camps excepted) it is recorded in `discovered`, pays
`discoveryStructureXp` (300) for a Jedi Ruin, Sith Tomb or Wreck Field or `discoveryOutpostXp` (150) for each army's
outpost, and shows the `site_discovered` toast (variant = `SiteKind` ordinal, "Discovered: <site>", one of three
`site.discovered` variants). Codex pages: `the_treaty_line` (first outpost), `republic_troopers` /
`imperial_troopers` (first outpost of that army, or the first kill or tend of its trooper), `ruin_sentinels` (first
ruin or tomb, or the first sentinel destroyed).

## Guidance (`MissionGuidance`)

Providers of the lead's `Guidance` (the highest request wins while the journal is held):

- **MISSION (50):** the site while travelling or there, the giver for a turn-in (`CALLING_CONTRACT`); nothing while
  placing or outside the Overworld.
- **INTERROGATION (40):** for `interrogatePullTicks` (12,000) after Interrogate or the freed settler's lead, the
  nearest ruin, tomb or wreck within `interrogateRadius` (1,024) whose pedestal has not given the player a holocron
  (`CALLING_HOLOCRON`, tinted by the holocron kind).
- **OUTPOST (20):** from SUMMONS, the nearest terminal (within `outpostGuidanceRadius`) of an army whose outpost
  the player has not discovered; a Sith's own army first, everyone else the Republic first (`CALLING_OUTPOST`, data =
  the army's `Faction` ordinal).

## Client

- **Tracker** (`MissionTrackerHud`): top right under the mob effects, 160 px at most; title, objective line with its
  counter, a chevron turned toward the target and the band **Far** (> `trackerNear` 160), **Near** (48 to 160) or
  **Here** (< `trackerHere` 48). The server sends `tracker_hint (yaw, band, progress, target)` every
  `trackerHintTicks` (20); coordinates never reach the client. Hidden with a screen open, with the HUD hidden, with
  `showHud` off, or with `/oldrepublicfx tracker off` (`ClientConfig.showMissionTracker`).
- **Feedback** (`MissionFx`): action bar progress ("Sentinels 2/3"); `mission_update` variant 0 on accept
  (`mission.accept`) and 1 on progress (`mission.update`, a short flash of the tracker); the completion banner and
  `mission.complete` come from the lead's `lesson_complete` variant 4.
- **Discovery toast**: top centre for 90 ticks, the site's name (`site.oldrepublic.<kind>`) under "Discovered".
- **Missions tab** (`MissionsPanel`, journal tab `missions`, order 30, shown from SUMMONS or with any mission
  history): left page the mission in hand (title, objective, band and counter), today's full pay n/3, the mentor
  duty, and own-side contracts toward rank 3; right page the four story missions, each done, in hand, available from
  its officer, or locked with the reason. The client has no balance file, so the counts shown use the defaults.

## Commands (operators, logged)

`/oldrepublic mission start <player> <id>` (no gate; a contract's issuer follows the path, a duty takes today's first
type), `complete <player> [choice]`, `abandon <player>`, `offers <player>` (today's board use, soft cap, duty,
own-side counts and the active mission), and the test aid `board <player> <republic|imperial>` (opens that army's
board at the player's feet as if a terminal were there).

## Balance (`missions`)

| Key | Default | Use |
|---|---|---|
| `storyXp`, `storyCommendations` | 1200, 2 | story pay |
| `jediOrderShare` | 0.5 | a Jedi's Order share of Republic gains |
| `offersPerBoard`, `usedOffersMax`, `dutiesPerDay` | 2, 16, 1 | board limits |
| `fullPayPerDay`, `overCapMultiplier` | 3, 0.25 | soft cap |
| `contractXpBase`, `contractXpPerLevel`, `contractXpCap` | 250, 40, 900 | contract XP |
| `contractRep`, `contractCommendations`, `dutyTrust`, `dutyRep` | 25, 1, 5, 25 | contract and duty pay |
| `siteRings`, `siteFallback`, `siteSpawnDistance`, `siteSeparation` | [96,128,144], [48,80], 48, 48 | runtime camps |
| `siteRetryTicks`, `siteForwardFallbackAfter`, `boardReach` | 200, 3, 8 | placement retries, board reach |
| `surveyRadius`, `surveyHeartRange`, `secureRadius` | 512, 8, 384 | worldgen objectives |
| `interceptYield`, `borderYield`, `chiefYield`, `fenceYield` | 0.25, 0.20, 0.25, 0.25 | yield fractions (the chief and fence get theirs at spawn through `Yielding.setYieldFraction`, an entity tag) |
| `tendTicks`, `healTendRange`, `downedBleedTicks`, `escortDespawnTicks` | 60, 4, 2400, 300 | wounded and escorts |
| `interrogatePullTicks`, `interrogateRadius`, `outpostGuidanceRadius` | 12000, 1024, 1024 | pulls |
| `discoveryStructureXp`, `discoveryOutpostXp` | 300, 150 | discovery |
| `trackerHintTicks`, `trackerNear`, `trackerHere`, `guidanceIntervalTicks` | 20, 160, 48, 40 | tracker and Calling |

## Tests

`MissionGameTests` (16 game tests) covers the offer seeding, used offers through accept, completion and abandon,
the soft cap through the service, the XP formula, own-side counting per path, the contract lead, every objective's
trigger and the targets from the definitions, story choices applied once (and a whole story completion), the Jedi
Order share, the yield dialogues and the new `INTERROGATED` / `HANDED_OVER` outcomes, the I2 hand-over escorts
despawning, the `MissionData` codec, tracker bands, Calling priorities, the Republic seed and the commendations. It
also holds the bodies of the eight delegated `StoryM3GameTests` cases (Peaceful arrival, the five kill-reputation
rules, kills never gaining, and the R2 chief by Hessa's fate). Every daily rule is given its day; Peaceful is tested
through `resolveOnArrival`.

The yield and hand-over tests use the M2 poacher captain as the stand-in sergeant, so they check only W2's side
(the outcome reaching the mission hook, the I2 choice, the escort tag and the despawn) and pass with W4's Phase 0
`Troopers.spawnEscort` stub; the trooper's own yield dialogue, walk-off and binding are W4's `TrooperGameTests`.
The class still compiles only with W4's `TrooperEntity` merged (the `TAG_BORDER_INCIDENT` constant, checked
against the I2 mission tag), which the 15.3 merge order guarantees.
