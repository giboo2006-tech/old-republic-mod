# Chapter Five, "The Long Hymn", and the two new factions (Milestone 5)

Owner: W4 (story and factions). Spec: `docs/MILESTONE_5.md` sections 6 (all of Chapter Five), 7 (the Vessba kajidic
and Clan Hask), 5.8 and 5.9 (the space bounties and objectives on the mission side), 8.5 (the rank 4 checklist view,
`title.wayfarer`, the Darth name and the "Turned" line in the Standing panel), 13.2 (the journal and the Standing panel),
14 (saved state and edge cases), 15.1 to 15.4 (`verse_vision`, the `lesson_complete` 11 to 15 banners, `story_m5`),
16.4 (`chapter_five`, `factions_m5`), 17.1 and 17.2 (the contracts used), 18 and 19. The art and sound are in
`docs/assets/chapter_five.md`.

Everything is server-authoritative. The client draws the journal page from the synced story record, the Standing panel
from the synced identity record and W5's checklist payload, the verse vision and the banners from effect events, and the
dialogues. Other workers read the chapter only through the lead's frozen accessor `quest.story.ChapterFive` (`stage`,
`atLeast`, `way`, `clanStands`, `verses`, `ulvaineOutcome`, `deeds`) and report into it only through `QuestHooks`.
Other workers are reached only through their Phase 0 contracts (W1's `Ships`, `ShipRegistry`, `TravelService`,
`FlightBalance`; W2's `SpaceEncounters`, `SpaceMissions`; W3's `PlanetSites`, `PlanetHubs`, `SiteAnchors`,
`StructureLocator`, `SpawnerGroups`; W5's `RankFour` data; W6's `ability:knows`, `mind_trick:use`, `fear:use`; W7's
`Crafting.teach`, `SaberAssembly.crystalStack`, `LegendaryDrops`; W8's `UlvaineFight.resolve`, `UlvaineEffects`,
`CartelEnforcers`, the `hask_warriors` and `scour_brood` groups, `RaidData`), so each step runs, with logged
fallbacks, whatever is merged.

**Stages only move forward** (`ChapterFiveRules.advance`), every reward is guarded by the stage change or a one-time
`c5.*` flag, and steps that can happen out of order are flags, not stages (6.1). The chapter ticks every 10 ticks per
player (`StoryService.SLOW_TICK`, offset by entity id). Days are game-day indexes of the overworld clock
(`Clocks.day`), so the planets count the same days.

## C1. Classes (`com.oldrepublic.quest.story` unless noted)

| Class | Role |
|---|---|
| `ChapterFiveFeature` | Initialisation (the lead calls it from `OldRepublicMod`): the chapter's conditions, actions and commands. |
| `ChapterFiveService` | The stage machine (`enterStage`, each stage's entry logic), the slow tick, the bodies of the Milestone 5 `QuestHooks` (17.1), the routes from the M1 to M4 services (ship state, level-up, node learned, site entered, sleep, leftovers, lifecycle), the retry timer and the shared helpers. Public seams for the commands and tests: `enterStage`, `checkWaiting`, `checkCall`, `repairUnlocks`, `applyUlvaineChoice`, `onBlockadeBroken`, `onBlockadeFailed`, `payMission`, `onPersonUsed`. |
| `ChapterFiveRules` | Pure rules (no world access; the tests and the client journal call them): stage moves, the call's wait, the ways' bookkeeping, the counting-house guards, Dask's choice, the blockade (`tyrvannBlockaded`, `blockadeStarts`, `scriptedPass`, `clanHelpOffered`), the verses' order, the choice ids, the daily and weekly counters, `clearsOnArrival`, and the journal's `objective` and `steps`. Every flag and counter name. |
| `ChapterFiveBalance` | Balance section `chapter_five` (C13). |
| `TheCall` | 6.1, 6.2: WAITING's wait (the day, or waking from a real sleep), the call by path, CALLED's unlocks and their repair for old saves, the helm lesson. |
| `SaltgateWays` | 6.3, 7: the first arrival, the kajidic's jobs and Vessba's audience, the matriarch hunt and the duel of honour, the Force's way at the door, Kaast's helmet, the clan hired, and the pay of these story missions. |
| `CountingHouse` | 6.3, 6.4: the counting-house mission (its start, its guards by the way taken, the clan at the player's side), Dask in his room, the hologram, Dask's fate. |
| `TyrvannArc` | 6.5, 6.6, 6.7: the free cannons, ARMED's wait, the blockade (start, failures, victory, the scripted pass), the landing, the verse stones' rewards and visions, the Hymnal's door, the journal's notes on missing worlds and free flight. |
| `UlvaineChoice` | 6.7, 6.9: Ulvaine's yield and the owner's choice, each choice's rewards, the fight's own reward, a helper's daily reward, AFTERMATH and the report. |
| `StoryNpcs` | The chapter's people and fights in the world: the story persons spawned for one owner (Dask, the debtor, the clan's champion), the fights kept in memory per owner (the salvagers, the brood, the guards and the clan allies), and the leave rule. |
| `FactionStanding` | 7: the kill reputation of the two factions' people, the contract gain of their boards, the deed counters (contracts, bounties), the bounty's daily rule, the cipher count. |
| `ChapterFiveDialogue` | The 6.11 conditions and actions W4 owns, and the story's own (C11). |
| `ChapterFiveCommands` | `/oldrepublic c5 ...` (C14). |
| `StoryService`, `StoryMentors`, `StoryGuidance`, `ChapterFourService`, `WorldArcs` (edits) | The chapter's tick, sleep and lifecycle routes; the report's dialogue when the path's mentor is used; the Calling for Chapter Five (C10); the ship-state route; the second power bar's key hint ("Focus + key", the lead's request). |
| `quest.QuestEffects`, `quest.QuestService` (edits) | The `verse_vision` effect id; the level-up, node-learned and site-entered routes. |
| `identity.FactionM5Balance` | Balance section `factions_m5` (C13). |
| `mission.SpaceContracts` (new), `MissionService`, `MissionBoard`, `MissionData`, `PlanetContracts` (edits) | The space objectives and bounties (C9), the Chapter Five pay route, the M5 board rows, the debtor camp of a Ghessar `collect`, Tyrvann's spar partner, the raised caps. |
| `vendor.VendorItems`, `vendor.hub.HubNpcs`, `HubNpc`, `HubEntities` (edits) | `ulvaines_writ`; the M5 hub roles, the story persons (`spawnStory`, `HubNpc.setStoryOwner`), the dialogue table by variant (`hostDialogue`), the champion's real blade, Vessba's own entity. |
| client `quest.ChapterFiveClient`, `quest.VerseVision` | The verse vision (C12). |
| client `quest.JournalScreen` (edit) | Chapter Five's page (C10). |
| client `mentor.MentorClient` (edit) | The `lesson_complete` banners 11 to 15 and their sounds (C12). |
| client `identity.StandingPanel` (edit) | The new factions' rows, the rank 4 checklist, the Darth name, the "Turned" line, the Wayfarer title (C9). |
| client `mission.BoardScreen` (edit) | The planet boards' daily space bounty strip (C9). |
| client `vendor.VessbaModel`, `VessbaRenderer`, `VendorClient` (edit) | Vessba the Hutt's own model and renderer. |

## C2. Saved state (14.1)

`StoryData.chapter5` (the lead's `Chapter5` record, `optionalFieldOf("chapter5")`; every field optional, so a save
without it loads as `NEW`):

| Field | Meaning |
|---|---|
| `stage` | `ChapterFiveStage` ordinal (C3). |
| `wait_since` | Game time the current stage began (WAITING's wait counts from it), -1 before the chapter. |
| `ways` | Bits of the Saltgate ways complete: 1 kajidic, 2 clan, 4 Force. |
| `way` | The way that finished first (`ChapterFive.Way` ordinal), -1 before one has. |
| `dask` | Dask's fate: -1 none, 0 release, 1 clan, 2 end. |
| `blockade_failures` | Blockade battles lost. |
| `verses` | Bits of the verse stones silenced (stone index 1 to 3 is bit 0 to 2). |
| `ulvaine` | The choice over Ulvaine (`BossOutcome` ordinal), -1 before it. |
| `boss` | The fight as the story sees it: 0 none, 1 yielded (the choice waits), 2 resolved. |

Story flags (all `c5.`):

| Flag | Set when |
|---|---|
| `c5.call_due` | waking from a real sleep while WAITING (the call plays at the next safe moment) |
| `c5.helm`, `c5.orbit`, `c5.jump` | each helm-lesson sub-objective is paid |
| `c5.arrived` | the first arrival at Saltgate was handled (both factions met, Clan Hask's first standing) |
| `c5.met_varra` | the player used Varra from SALTGATE on ("Hand him to the clan" needs it) |
| `c5.helmet_returned` | Kaast's helmet was given back |
| `c5.job.debt`, `c5.job.cargo`, `c5.audience` | each kajidic job is paid; the audience is granted |
| `c5.debt.force` / `paid` / `ran` | the debtor's choice in the current attempt (a new debt job forgets it) |
| `c5.matriarch`, `c5.honour` | the hunt and the duel of honour are paid |
| `c5.force.trick` / `fear` / `push` | how the counting-house door was taken |
| `c5.counting_house` | the counting-house was cleared and paid |
| `c5.dask.release` / `clan` / `end` | Dask's fate |
| `c5.cannons_paid`, `c5.blockade_paid` | the free cannons were fitted; the blockade's rewards were paid |
| `c5.verse.<1-3>` | that stone's rewards were paid |
| `c5.hymnal_open` | the Heart's halls were first entered at THE_HYMNAL or later (entry `the_hymnal`) |
| `c5.ulvaine_paid`, `c5.choice.spare` / `bind` / `end`, `c5.reported` | the fight's reward; a choice's rewards; the report |
| `c5.clan_hired`, `c5.writ` | the lead's `ChapterFive.CLAN_HIRED` and `WRIT` (the writ's deed lives in the flag) |
| `c5.dorrun_greeted` | Dorrun greeted the player at VERSES |
| `c5.no_ghessar`, `c5.no_tyrvann`, `c5.flight_off` | kept current by the slow tick for the client's journal (a world missing, free flight off) |

Story counters: `c5.helper_day` (a helper's last reward day), `c5.cipher_day` (the fence's last cipher day),
`c5.cipher.<planet>` (ciphers used at that board, salts its bounty), `c5.bounty_day.<planet>` (the last bounty taken at
that board), `c5.shard_week` (the fence's last shard week), and the lead's `c5.bounties` and `c5.contracts` (the rank 4
deeds). Identity choices: `faction_met:vessba_kajidic` and `faction_met:clan_hask` (`Identity.meetFaction`),
`ulvaine.spare|bind|end` (the choice), and the M3 `KILLED_SOLDIER` of the kill rule.

In memory only (never saved; rebuilt from the record when needed): the retry timer per task, the sleep start, the
blockade failure already counted for the running battle, every fight and its fighters, and the story persons (not
saved: `HubNpc.setStoryOwner`). Every fighter and story person carries the tag `oldrepublic.c5_story`, so one saved with
an unloaded chunk is discarded when it loads (`ChapterFiveService.isLeftover`).

## C3. Stages (6.1)

| Stage | Entered | Entry logic | Leaves when |
|---|---|---|---|
| `NOT_STARTED` | default | | Chapter Four `COMPLETE` (the slow tick, so M4 saves enter on join) |
| `WAITING` | above | `c5.call_due` cleared | `waitTicks` after, or `c5.call_due` set by a sleep of at least `minSleepTicks`, at a safe moment (`StoryMentors.safeMoment`: home world, not aboard a ship, no open dialogue, the M4 seeker rule) |
| `CALLED` | the call plays (C4) | 300 XP, entry `the_factor_asks`, the helm and the `saltgate` destination (`TheCall.repair`), the objective line | the owner is on Ghessar (`onPlanetArrived`, or the slow tick's check) |
| `SALTGATE` | arrival | the objective line | the first way is complete (C5) |
| `THE_FACTOR` | above | the objective line; the counting-house mission starts on a later slow tick (C6) | Dask's fate is chosen |
| `ARMED` | Dask's choice, no cannons | the `holdfast` destination unlocks, "coordinates loaded", the cannons checked at once | the ship has cannons (`Ships.hasWeapons`, the slow tick, or the free fitting) |
| `BLOCKADE` | above, or straight from Dask's choice for a ship with cannons | the holdfast unlocks too (a skipped ARMED), the objective line | the blockade is broken (W2's hook, or the scripted pass) |
| `TYRVANN` | above | the objective line | the owner lands on Tyrvann (`onPlanetArrived`, or the slow tick's check) |
| `VERSES` | above | Dorrun greets a player the clan trusts (once), where the stones came from, the count | three stones silenced **and** level `duelLevel` (checked on each stone, each level-up and each slow tick) |
| `THE_HYMNAL` | above | the objective line with `recommendedLevel` | the owner's choice over Ulvaine (C8) |
| `AFTERMATH` | the choice | who to report to, by the path now | the report |
| `COMPLETE` | the report | | (the ongoing loop) |

From VERSES on, while the owner is on Tyrvann, the slow tick asks W3 to place the owner's unsilenced stones and the shared
Hymnal as they come near (`PlanetSites.ensure`).

## C4. The call and the helm (6.2)

- **The call** (`TheCall.play`, also `/oldrepublic c5 call`): the chapter enters CALLED (which pays and unlocks), then
  the path's mentor speaks: a Jedi hears Saelen in `c5_call_saelen`, a Sith Sorn in `c5_call_sorn` (no speaker: a comm
  call; nodes `call`, `call2`, `why`, and `kaast` first for a player who spared Kaast, "Kaast named a Hutt's factor. Here
  he is."). An Independent hears the Compass hum (`story.calling.holocron`) and Tessaly Arn's echo in three chat lines
  (and the Kaast line first when he was spared).
- **Unlocks** (`TheCall.repair`, at CALLED and on every slow tick of a started chapter, so saves already past a stage get
  them): the helm (`TravelData.helm`) and `saltgate` from CALLED, `holdfast` from ARMED.
- **The helm lesson** (sub-objectives, each once, only from CALLED on): "Take the helm" (`onHelmTaken`: 200 XP, entry
  `the_helm`), "Reach orbit" (`onOrbitEntered`: 300 XP, codex `space`), "Jump from orbit" (`onOrbitEntered` with
  `byJump`: 300 XP). Shown with ticks while CALLED; afterwards only the undone ones stay as hints; hidden with free
  flight off.

## C5. Saltgate: the first arrival and three ways (6.3, 7)

- **The first arrival** (once, `c5.arrived`; on the first touchdown on Ghessar, `onPlanetArrived(GHESSAR)`, whose
  `saltgate` destination unlocks at CALLED, or CALLED's slow check of a player already there): both factions are met (`Identity.meetFaction`, so their Standing rows appear) and Clan Hask's first
  standing is set from Kaast's fate (`clanStartSpared` +100, `clanStartStripped` 0, `clanStartEnded` -150; none chosen
  counts as stripped; source `c5.clan_start`). At CALLED the chapter moves to SALTGATE. The M4 arrival rewards are not
  paid again (W3's and the lead's).
- **The ways progress in parallel.** Each completes once (`Chapter5.withWay`): its bit, its entry (`the_kajidic_way`,
  `the_clans_way`, `the_forces_way`) and a line. The first one also becomes `way` and moves SALTGATE to THE_FACTOR; the
  others stay open as optional faction work, each paid once, changing nothing else.
- **The kajidic's way.** Ommo Teel (`c5_majordomo`) gives the two jobs (`c5:mission c5_debt`, `c5:mission c5_cargo`), in
  either order (the cargo only with free flight):
  - "A Debt in the Canyons" (`c5_debt`, story `recover`, driven): the owner's `debtor_camp` (W3's site, placed when they
    come near, its ring point before) is the mission's site. The debtor (`hub_host` variant `debtor`, a story person at
    the camp's `NPC_DEBTOR`, else its middle) offers `c5_debtor`: **force** (the `scavengers` group, `salvagers` of
    them, at the camp's `SPAWNER`, set on the player; when they are down the box is the player's), **pay** (200 credits,
    `debtCredits`) or **run** (he leaves; the player must tell Teel the box was empty). The debtor leaves either way. At
    Teel (`c5:debt_claim`): `box` hands over a paid or taken box; after a run, `trick` (W6's `mind_trick:use`, only when
    the player can use it) or `bribe` (150 credits, `teelBribe`) completes the job, and `back` drops it to try again.
  - "The Quiet Cargo" (`c5_cargo`, story `space_run`): started through W2's `SpaceMissions` from Ghessar's orbit; W2's
    success completes it, a scan or a lost crate drops it (take it again from Teel). Each Clan Hask fighter of its
    patrol shot down costs clan standing as a hostile kill (C9).
  - Each job pays 1,200 XP, 150 credits, kajidic +60, once. With both done (the debt alone without free flight), Vessba
    (`c5_vessba`, condition `c5:audience`) grants the audience: kajidic +80, the way (`c5:way kajidic`).
- **The clan's way.** Varra (`c5_varra`; her opening by Kaast's fate: `greet_spare`, `greet_strip`, `greet_end`) gives
  "The Matriarch" (`c5_matriarch`, story `clear`, driven): a ground seeded per player at 150 to 250 blocks
  (`matriarchRange`) from Saltgate, moved to the nearest `ghessar_dunes` the biome source finds within 96 blocks; within
  `arriveRange` of it, `matriarchBrood` sand scours of W8's `scour_brood` group (the matriarch among them) are set on the
  player; when they fall it completes (1,500 XP, 150 credits, clan +80) and completes the way. If Kaast died by the
  player's hand, Varra asks first for "A Duel of Honour" (`c5_honour`, story `spar`): Clan Hask's champion (Sheva Hask, a
  story person beside Varra, with a real vibroblade) fights under the M4 spar rules (`SparRules`, goal yield at
  `honourYield` 0.2, damage `honourDamage`); a win pays 800 XP and clan +100 once, settles the grudge and the champion
  leaves; a loss can be fought again.
- **The Force's way.** The counting-house doorman (`c5_doorman`, open only in SALTGATE before any way is done):
  **trick** (Mind Trick through W6's `mind_trick:use`; kajidic -25, codex `cartel_enforcers`), **fear** (Fear through
  `fear:use`; kajidic -50) or, for a player who can use neither now, **push** (a Force Push through the door; kajidic
  -50). 600 XP each, flag `c5.force.<how>`, the way.
- **Kaast's helmet** (7): when Kaast was stripped, Varra asks for `kaast_helmet` back (`c5:helmet`, she keeps it): clan
  +100 once. Keeping it changes nothing.

## C6. The counting-house and the factor (6.3, 6.4)

- **The mission** (`c5_counting_house`, story `clear`, driven): it starts on THE_FACTOR's slow tick when the player has no
  other active mission (so the way's own mission is paid first), retried every `retryTicks` while another runs. Its
  site is Dask's room (the Saltgate template's `NPC_DASK`, else 12 blocks east of the hub's heart).
- **The guards** (`countingHouseGuards`, by the first way): kajidic 0 (Vessba's guards stand aside: done on arrival with
  a line), clan 6 (with `clanAllies` 3 Clan Hask warriors of W8's `hask_warriors` group, spawned with the owner so they
  are Allied), Mind Trick 4, Fear or a forced door 8. W8's `CartelEnforcers.squad` gives the kits (a captain leading from
  three); each stands at a `COUNTING_HOUSE_1..8` anchor, or on a 6-block ring around the room without them. The mission
  counts them down; when the last falls it completes: 1,500 XP, 150 credits, once (`c5.counting_house`). On Peaceful it
  is done on arrival.
- **Dask** (`hub_host` variant `dask`, a story person, invulnerable, talks only to his owner): spawned at his room while
  the owner is within 48 blocks, once the counting-house is cleared and until his fate is chosen. `c5_dask`: `greet`,
  `buyer` (Darth Ulvaine), `want` (the bounty: alive, a singer), `holo_1` (action `c5:hologram`: the `hologram` event,
  entry `the_buyers_face`, codex `darth_ulvaine`), `holo_2`, `coords`, then the choice.
- **The choice** (`c5:dask release|clan|end`, re-checked on the server; the clan's only after `c5.met_varra`): release
  (kajidic +40, light +3), clan (clan +80, kajidic -60), end (kajidic -100, clan -20, dark -5, entry `dask_ended`). Every
  choice: 300 XP, entry `the_factor`, flag `c5.dask.<choice>`, Dask leaves, and the chapter moves to ARMED, or straight
  to BLOCKADE for a ship that already has cannons.

## C7. Arming, the blockade and Tyrvann (6.5, 6.6)

- **The free cannons** (`c5:fit_cannons`, the shipwright's dialogue, at any standing): only in ARMED, with a ship that is
  not busy (`Ships.busy`); the `CANNONS` upgrade (`Ships.update`), once (`c5.cannons_paid`), 200 XP, entry `teeth`; ARMED
  then passes at once. A ship given cannons any other way passes on the next slow tick.
- **The nav** (W1 reads `ChapterFiveRules.tyrvannBlockaded`): Tyrvann is refused (`BLOCKADED`) before BLOCKADE; in
  BLOCKADE every route ends in its orbit.
- **The battle**: arriving in Tyrvann's orbit during BLOCKADE (`onOrbitEntered`) starts W2's encounter
  (`SpaceEncounters.startBlockade`) unless it runs already. W2's `onBlockadeBroken`: 2,000 XP, 200 credits, entry
  `the_blockade`, codex `hymn_interceptors`, once (`c5.blockade_paid`); TYRVANN.
- **A lost battle**: the ship becoming `DISABLED` in BLOCKADE, while the battle runs or in Tyrvann's orbit, counts one
  failure per battle (`blockade_failures`; W2 may also call `onBlockadeFailed`). After `clanHelpFailures` (2) a player
  the clan does not already stand with is told to ask Varra; her `clan_help` node (`c5:hire_clan`) takes 1,000 credits
  and sets `c5.clan_hired`, so `ChapterFive.clanStands` is true for W2's next battle.
- **The scripted pass** (Peaceful, or free flight off): when no battle runs after the arrival in orbit, or on a landing
  on Tyrvann in BLOCKADE, a line ("the interceptors hail the singer and turn away") and the blockade is broken as above.
- **Tyrvann**: a landing in TYRVANN moves to VERSES. Dorrun's greeting comes when the clan trusts the player (Trusted or
  better); otherwise the stones come from Dask's data. Dorrun's `c5_dorrun` follows the standing (`greet`, `stones`,
  `hymnal`, `lore`; `cold` below Trusted).
- **The verse stones** (`onVerseSilenced`, W3): from VERSES on, each stone once: 1,500 XP, 100 credits, the entry of the
  **order** it fell in (`verse_one`, `verse_two`, `verse_three`), codex `the_long_hymn` with the second and
  `the_first_cantor` with the third, 1 SP with the third (`versesSp`), and the vision (C12) with variant = order - 1,
  whatever the stone's index. W3's site is marked done.
- **The Hymnal**: THE_HYMNAL opens the Heart's door for the owner (W3 and W8 read the stage). Walking into the Hymnal's
  halls piece at THE_HYMNAL or later gives entry `the_hymnal` once.

## C8. The yield, the choice and the report (6.7, 6.9)

- **The yield** (W8's `onBossYielded(owner, "ulvaine")`, also on Peaceful where she talks and kneels at once): `boss`
  becomes 1 and `c5_ulvaine_yield` opens for the owner only (not reopened while it shows). Condition `c5:yield`.
- **The choice** (`ulvaine:resolve spare|bind|end`, re-checked): `boss` 2 and `ulvaine` saved first; then W8's
  `UlvaineFight.resolve` plays the scene; the choice's rewards (once per `c5.choice.<id>`), the fight's reward and
  AFTERMATH.

| Choice | Rewards |
|---|---|
| Spare | +8 light; her saber's red crystal, attuned to the player (cosmetic hue) |
| Bind | by the path at the moment of the choice: Jedi +5 light, Jedi Order +100; Sith, Sith Empire +150 and `ulvaines_writ` (flag `c5.writ`); Independent, Clan Hask +150 and 500 credits |
| End | -10 dark, the `emitter_cantor_legendary` schematic (`Crafting.teach`), Jedi trust -10, Sith trust +10 |

  Each also records the identity choice `ulvaine.<id>` and its entry (`ulvaine_spared`, `ulvaine_bound`,
  `ulvaine_ended`).
- **The fight's reward** (once, `c5.ulvaine_paid`, whatever the choice): 5,000 XP, 2 SP, 400 credits, Resonance
  `ulvaine` +600, codex `darth_ulvaine`. Paid by the choice, or by W8's `onBossDefeated` for the owner, whichever comes
  first.
- **A helper** (W8's `onBossDefeated` for a participant who is not the owner; her echo excluded): 1,200 XP
  (`ulvaine.helperXp`, W8's section) and one roll on `gameplay/ulvaine_helper` (2 sunstone, 2 stormglass shards or 2 clan
  iron, equally likely), once per game day (`c5.helper_day`); a second the same day gets a line.
- **The report** (`c5:report`; Saelen's `c5_hub_saelen` or Sorn's `c5_hub_sorn`, opened by `StoryMentors` when the
  path's mentor is used in AFTERMATH; an Independent holds the journal and the Compass takes it on the next slow tick):
  the mentor's line for the choice, 1,000 XP, trust +10 (Jedi and Sith), Resonance `chapter_five` +400, entry
  `report_c5`, banner `lesson_complete` 11, COMPLETE. W5's rank 4 checklist then shows in the Standing panel. The path is
  read at the moment, so a player who Fell or Returned reports to the new mentor (6.9).

## C9. Factions: the Vessba kajidic and Clan Hask (7)

- **Standing** uses the M2 tiers (Hostile -500, Distrusted -100, Trusted 100, Honoured 500). Gains and losses as in 7,
  each with a one-time source id where it is one-time. Kills: W8's `onFoeKilled` with the faction and the hostile
  snapshot, and the cargo patrol's fighters from `onShipKill` (W2's `SpaceMissions.missionOf` and the `hask_fighter`
  type), apply the M3 `KillReputation` rule on the game day (-10 when hostile, at most 200 a day; otherwise -40 and
  `KILLED_SOLDIER`). The attitudes of their people are W8's (`StandingRule`).
- **Vendors** (data `vendor/cartel_fence.json`, `vendor/hask_armourer.json`; the M4 refusals below Neutral, the M4
  Honoured price):

| Vendor | Stock |
|---|---|
| `cartel_fence` (kajidic) | sunstone x4 (60), stormglass x4 (80), repair kit (40), concussion missiles x4 (60), hyperfuel cell (45); Trusted: the jet pack (500, 1 a day); Honoured: the Storm Cell schematic (2,000, 1 a day). Buys sunstone, stormglass, clan iron, wreck salvage, alloy plates, power cores |
| `hask_armourer` (clan) | clan iron x2 (240); Trusted: the Mandalorian set (helmet 320, chest 450, legs 400, boots 300); Honoured: the Hask Pommel and Clan-Iron Plating schematics (2,000 each, 1 a day). Buys clan iron, stormglass, iron |

  The fence's dialogue (`vendor_cartel_fence`) adds the **cipher** (`c5:cipher ghessar|tyrvann`, 50 credits, once a
  day across both boards, only while that board's bounty has not been taken today: it rerolls that board's bounty by
  bumping `c5.cipher.<planet>`) and, at Trusted, the **hymnstone shard** (`c5:buy_shard`, 400 credits, once a week). The
  shipwright's dialogue (`vendor_saltgate_shipwright`) holds W1's `ship:*` services at Neutral kajidic standing (the four
  upgrades, the class refit, the two legendary parts, hull repair, and taking an upgrade or part out for nothing with
  `ship:remove`) and the story's free cannons at any standing.
- **Contracts** (7.2): Ghessar's terminal (`vessba_kajidic`): `ghessar_collect` (`clear` at the owner's debtor camp,
  the salvagers), `ghessar_recover` (`secure` at a crashed freighter), `ghessar_cull` (`clear`, a scour brood),
  `ghessar_escort` (a kajidic factor to a `kajidic_post`), `ghessar_run` (`space_run`), `ghessar_bounty`. The holdfast
  (`clan_hask`): `tyrvann_hunt` (`clear`, a stormback), `tyrvann_spar` (Clan Hask's champion comes to the holdfast for the
  bout), `tyrvann_cleanse` (`clear` at a `hymn_shrine`, Hymnsworn), `tyrvann_recover` (`secure` at a `mire_grave`),
  `tyrvann_patrol` (`space_clear`), `tyrvann_bounty`. Loot tables `gameplay/ghessar_contract`, `gameplay/tyrvann_contract`.
  XP and credits by the M3 and M4 formulas; the issuer gains `contractRep` (+25). Each finished contract on Ghessar or
  Tyrvann except a bounty counts `c5.contracts` (the rank 4 deed).
- **Space objectives** (`SpaceContracts`, 5.9): a `space_run` or `space_clear` mission has no ground site; it is ACTIVE at
  once and started through W2's `SpaceMissions.start` (refused, with a line and no mission kept, with no ship, flight
  off, or W2's refusal). The orbit: Ghessar's for the cargo, the board's own world for a run or patrol. W2 reports the
  end (`onSpaceObjectiveDone`): success completes, failure drops. An abandoned mission cancels W2's objective.
- **Bounties** (5.8): offered every day apart from the board's two rolled contracts (`MissionBoard.planetOffers`), drawn
  as a strip under the cards (`BoardScreen`), not on Peaceful, not with flight off, one per board per player per day
  (`c5.bounty_day.<planet>`). The orbit is one of the player's unlocked orbits, seeded by the day and the board's
  ciphers. Paid in place of the contract formula and outside the daily soft cap: 600 XP, 400 credits, +15 with the
  board's faction, the Hymn-Tuned Drive schematic roll (`LegendaryDrops.roll`, 2% with a guarantee at 25), and
  `c5.bounties` (the deed).
- **The Standing panel** (7.3, 8.5): the two rows appear once met (`IdentityRules.standingRows`). From AFTERMATH the
  next rank is rank 4: "Toward Master", "Toward Darth" or "Toward Wayfarer", each requirement ticked or crossed with the
  player's value as W5 last sent it (`RankFourClient`), who holds the ceremony once all are met, and which deeds count
  when there is room; "Attained" once held. A Darth's chosen name follows the rank ("Darth Valcrane"), a "Turned on day
  N" line follows a turning, and `title.wayfarer` shows in the titles row.

## C10. The journal and the Calling (6.10, 13.2)

- **The page** (`JournalScreen`, from CALLED): the chapter header, the objective (`ChapterFiveRules.objective`, with its
  counts) and the sub-objectives ticked when done (`steps`): the helm lesson, the three ways at Saltgate with their
  progress (the jobs n/2, or n/1 with flight off; "Vessba will see me"; the duel first when owed), and the clan's help
  after the failures. A missing world says the way is closed; below the duel level after three stones it says "not
  ready (level 17)". From COMPLETE the ongoing loop. The client reads the balance defaults (the duel level, the clan's
  price): the server's values are not sent.
- **Entries and codex** as in 6.10, unlocked where the step happens (this document names each). Also: `starfighters` on
  the first `onShipKill`; `cartel_enforcers`, `hymnsworn`, `sand_scours`, `stormbacks` on the first kill
  (`onFoeKilled`; `cartel_enforcers` also with a Mind Trick at the door); each M5 power's page and `mind_trick` from
  `onNodeLearned`; `the_first_cantor_falls` and `the_first_cantor` on a first raid clear (`onRaidCleared`);
  `the_spindle` from the Spindle's log (`c5:codex the_spindle`).
- **The Calling** (`StoryGuidance`, nothing while a mission runs, so the missions keep their own): CALLED, SALTGATE and
  ARMED point to Saltgate from another world; THE_FACTOR to Dask's room; BLOCKADE to the player's ship until within 16
  blocks of it; VERSES to the nearest unsilenced stone (its site, else its ring point); THE_HYMNAL to the Hymnal.

## C11. Dialogues, conditions and actions (6.11)

Dialogues (17, data in `dialogue/`): `c5_call_saelen`, `c5_call_sorn` (`kaast`, `call`, `call2`, `why`),
`c5_hub_saelen`, `c5_hub_sorn` (`report`, `idle`), `c5_harbourmaster` (`welcome`, `curious`, `factor`, `port` while
SALTGATE has no way done; `greet`, `port_greet` after; the M4 free recall), `c5_majordomo` (the jobs, their briefs and status, the debt's claims, the audience, `lore`,
`idle`), `c5_vessba` (`audience`, `sell`, `who`, `after`, `lore`, `greet`), `c5_varra` (the openings by Kaast's fate,
`menu`, `why`, the briefs and status, `lore`, `clan_help`, `idle`), `c5_doorman` (`door`, `after`, `closed`),
`c5_debtor` (`plead`, `gone`), `c5_dask` (C6), `c5_dorrun` (C7), `c5_ulvaine_yield` (`yield`, `gone`), `spindle_log`
(four log entries), `vendor_cartel_fence`, `vendor_hask_armourer`, `vendor_saltgate_shipwright`. Each M5 host opens its
dialogue by variant (`HubNpcs.hostDialogue`): harbourmaster, teel, vessba, varra, dorrun, doorman, dask, debtor, and W5's
warden; the bartender only barks; the champion starts her bout.

Conditions (W4): `c5:stage` (at least), `c5:stage_is` (one of a comma list), `c5:way` (`kajidic`, `clan`, `force` or
`any`), `c5:kaast` (`spare`, `strip`, `end`), `c5:yield`, and the story's own `c5:audience`, `c5:door`, `c5:no_power`,
`c5:debt` (`choice`, `box`, `ran`), `c5:dask_ready`, `c5:honour_owed`, `c5:helmet_asked`, `c5:helmet`, `c5:met_varra`,
`c5:clan_help`, `c5:flight`, `c5:cannons`, `c5:report_ready`, `c5:cipher`, `c5:shard`. A boolean condition's `!`
argument negates it. Actions (W4): `c5:way`, `c5:mission`, `c5:dask`, `c5:report`, `c5:fit_cannons`, `c5:cipher`,
`c5:buy_shard`, `c5:hire_clan`, `ulvaine:resolve`, and the story's own `c5:debt`, `c5:debt_claim`, `c5:doorman`,
`c5:helmet`, `c5:hologram`, `c5:codex`. Every action re-checks the chapter on the server, so a stale or forged choice
changes nothing. Used as their owners define them: W6's `ability:knows`, `mind_trick:use`, `fear:use`; W1's `ship:*`;
the M2 `reputation_min` with a tier name; the M3 `mission:abandon`.

## C12. Effects (15.1, 15.3)

| Event | Sent | Look | Sound |
|---|---|---|---|
| `verse_vision` 0 to 2 | `toPlayer` on a stone's first silencing; variant = the order it fell - 1; intensity = `visionTicks` (120) | a pale grey-gold wash over the screen with three subtitled lines and a faint motif (the stave, the choir, the quiet city); steady with flashing off; ends on death, a level change or a disconnect (`VerseVision`) | `story.verse_vision` (UI); `VerseVision.dipping()` for W3's ambience |
| `hologram` 0 | `around` at `c5:hologram`: pos = the speaker + 1.1 up, entity = Dask, intensity = `hologramTicks` (240) | W8's `HologramFx` (Ulvaine translucent over the desk) | W8's `story.hologram` loop |
| `lesson_complete` 11 | `toPlayer` at the report, intensity = the path | the banner "Chapter Five complete" over "Reported to the Watch", "Reported to the Overseer" or "The Compass is quiet", in the path's colour | `story.chapter_five` |
| `lesson_complete` 12 to 15 | W5 (12, 13), W7 (14), W8 (15) | W4's banners `turning`, `rank_four`, `legendary`, `long_hymn` in the path's colour (`MentorClient`) | `story.turning.rite`, `story.rank_four`, `bench.legendary`, `story.lesson_complete` |

Phase-by-phase looks, colours and timings: `docs/assets/chapter_five.md` (A1).

## C13. Balance

`chapter_five` (`ChapterFiveBalance`): every key of 16.4 with its default, plus these additions:

| Key | Default | Meaning |
|---|---|---|
| `recommendedLevel` | 18 | the Hymnal's objective line |
| `retryTicks` | 200 | before a failed placement, search, spawn or mission start is tried again |
| `matriarchRange` | [150, 250] | the hunt's distance from Saltgate |
| `arriveRange` | 24 | the owner this close to a story spot counts as there |
| `visionTicks` | 120 | the verse vision's length |
| `matriarchBrood` | 4 | the hunt's scours, the matriarch included |
| `salvagers` | 3 | the debtor's salvagers when the box is taken by force |
| `honourYield`, `honourDamage` | 0.2, 1.0 | the duel of honour's yield fraction and blade damage |
| `clanAllies` | 3 | the clan warriors in the counting-house |
| `hologramTicks` | 240 | Ulvaine's message on Dask's desk |

`factions_m5` (`FactionM5Balance`): every key of 16.4, plus `cipherCredits` 50 and `shardCredits` 400 (the fence's
services). The helper's XP is W8's `ulvaine.helperXp`; free flight is W1's `flight.enabled`.

## C14. Commands (operators, every use logged)

`/oldrepublic c5 <sub> <player>` (`ChapterFiveCommands`): `status` (stage, ways, Dask, failures, verses, the fight, the
`c5.*` flags and counters), `stage <stage>` (forward only, running the entry logic), `call`, `way
<kajidic|clan|force>`, `cargo <success|fail>`, `dask <release|clan|end>` (the counting-house counts as cleared),
`blockade <broken|failed>`, `verse <1-3>`, `yield`, `choose <spare|bind|end>`, `report`, `daily` (forgets today's
cipher, shard, helper reward and bounties). They stand in for the hooks other workers fire until integration.

## C15. Edge cases (6.9, 14)

- **Old saves**: an M4 save at Chapter Four `COMPLETE` enters WAITING on its first slow tick; the unlocks are repaired for
  any stage; a save already on Ghessar at CALLED, or on Tyrvann at TYRVANN, moves on at the next slow tick.
- **Missing worlds**: the journal says the way is closed (`c5.no_ghessar`, `c5.no_tyrvann`); nothing is paid or lost.
- **Free flight off** (`flight.enabled` false): the kajidic's way is the debt alone, the blockade is the scripted pass,
  the helm lesson is hidden, no space contract or bounty is offered or started.
- **Peaceful**: the matriarch hunt and the counting-house complete on arrival; the salvagers scatter; the blockade is the
  scripted pass; Ulvaine talks (W8); no `space_clear` contract or bounty.
- **Death, logout, a level change**: the fights end and their fighters go (the M4 leave rule, also past 80 blocks); the
  story spawns them again when the owner comes back; story persons are spawned again within 48 blocks; the retry
  timers go. A blockade failure already counted stays counted.
- **Co-op**: everything is per owner; Dask and the debtor talk only to their owner, two owners each get their own; a
  helper's reward is daily; only the owner's chapter moves.
- **Turning mid-chapter** (8): the mentor lines, the report's mentor and Bind's reward follow the path at the moment.
- **Dedicated server**: nothing in `quest.story` or `mission` touches a client class.

## C16. Tests (19)

`StoryM5GameTests` (19.1, 28 methods, each synchronous with `maxTicks` 20, mock players removed in `finally`):
`stagesOnlyMoveForward`, `m4SaveAtC4CompleteEntersWaiting`, `callOnSafeMomentOnly`, `helmUnlockedAtCalledAndForOldSaves`,
`helmLessonPaysOnce`, `waysProgressInParallelFirstWins`, `forceWayOffersOnlyKnownPowers`, `countingHouseGuardsByWay`,
`daskChoicesAndShifts`, `armedSkippedWhenShipHasCannons`, `tyrvannRefusedBeforeBlockade`,
`blockadeStartsOnArrivalInOrbit`, `twoFailuresOfferTheClan`, `versesNeedThreeAndLevel17`,
`ulvaineChoiceRewardsOncePerFlag`, `reportShowsRankFourChecklist`, `clanStartFromKaastOutcome`, `helmetReturnedOnce`,
`tiersGateVendorsAndAttitude`, `standingRowsOnlyWhenMet`, `contractsValidateOnNewPlanets`,
`spaceObjectivesGoThroughSpaceMissions`, `bountyOncePerBoardPerDayOutsideSoftCap`, `deedsCounters`,
`newFactionKillLossesCapped`, `debtChoicesPayBribeOrForce`, `ulvaineHelperPaidOncePerDay`, `peacefulRules`.

Client steps (`m5/StoryClientSteps`, run by the lead's `M5ClientTest`, band 140 to 153, every shortcut logged, everything
restored at the end): 140 `journal_c5`, 141 `varra`, 142 `vessba`, 143 `vessba_audience`, 144 `dask`, 145 `hologram`,
146 to 148 `verse_vision_1` to `3`, 149 `ulvaine_choice`, 150 `standing_factions`, 151 `report`, 152 `banner`, 153
`standing_rank4`. Real inputs: right-clicks on Varra, Dask and Saelen, number keys for the dialogue choices; the hooks
other workers fire (arrival, verse stones, the yield) are called directly.

## C17. Decisions

- Chapter Five's people and fights are not saved: the record says what is needed, and the slow tick spawns it again when
  the owner comes near, so a crash or a restart never leaves a stray quest person or a half fight.
- The counting-house mission starts on a later slow tick, not inside the way's completion, so the way's own mission is
  paid and closed first; it waits while another mission is active.
- The blockade's failure is counted from the ship's own state change (`DISABLED` in the battle or in Tyrvann's orbit),
  once per battle, so the chapter does not depend on W2 reporting losses; W2 may report too.
- The scripted pass is told by the story whenever no battle runs (Peaceful or flight off), also on a landing in
  BLOCKADE, so the chapter never stalls on a missing encounter.
- The verse rewards follow the order the stones fall, not their index (6.10), and the vision's variant follows it too.
- The fight's reward is guarded by its own flag and paid by whichever comes first, the choice or W8's defeat hook, so a
  resolve with no defeat hook after it (or the reverse) still pays exactly once.
- An Independent reports by holding the journal (the Compass speaks through it), on the slow tick in AFTERMATH.
- The writ's deed is the story flag `c5.writ`, so a lost keepsake loses nothing.
- A space bounty counts only as a bounty deed, not also as a contract deed.
- `tyrvannBlockaded` is true for every player before BLOCKADE, also one who never started the chapter: `holdfast`
  unlocks only from ARMED, so it changes nothing for a player without it.
