# Chapter Four: "Stars to Steer By" (Milestone 4)

Owner: W2 (story, vendors, economy). Spec: `docs/MILESTONE_4.md` sections 2 (the player journey), 8 (all), 10.6 (Kaast's
rewards), 12 (saved state and edge cases), 13.3 (`seeker_scan`, `lesson_complete` 8 to 10), 14 (`chapter_four`), 16 and
17. Vendors, credits, hub people and planet contracts are in `docs/design/vendor.md`; the art and sound are in
`docs/assets/chapter_four.md`.

Everything is server-authoritative. The client draws the synced `StoryData` (the journal page, the objective line), the
effect events (`seeker_scan`, the banners) and the dialogues. Other workers are reached only through their Phase 0
contracts (`TravelService`, `Ships`, `PlanetHubs`, `PlanetSites`, `Hunters`, `Droids`, `HuntingCamps`, `KaastFight`,
`Crafting`), so the chapter runs, with logged fallbacks, whatever is merged.

## C1. Classes (`com.oldrepublic.quest.story` unless noted)

| Class | Role |
|---|---|
| `ChapterFourService` | The stage machine and the entry point of every M4 `QuestHooks` call: `enterStage` (forward only, runs each stage's entry), `checkMarked` (C3 done or an old save at C3 COMPLETE enters MARKED), the tick and slow tick, back pay, retries (`due`/`retryLater`), `once` (one-time flags), `banner`, `peaceful`. |
| `ChapterFourRules` | Pure rules: `advance`, the seeker's due time, the briefing per path, parts XP, the motivator routes and daily re-issues, the arc's steps and checklist, the trial rules (`barrierCounts`, `heartstoneDone`, `gripEnds`), the hunt's gates, `challengeReady` (arc, 3 clues, level 14), back pay, the journal objective. Used by the tests and the client journal. |
| `ChapterFourBalance` | Balance section `chapter_four` (C12). |
| `SeekerScene` | The mark (8.2): the safe-moment check, the droid's descent, scan and climb, the stage move to SEEKER; sleep tracking. |
| `ChapterFourRepairs` | The briefing, the hulk (`Ships.placeHulk`, retries and the forward fallback), parts XP, the Wreck Field salvage of the motivator, the smuggler's re-issue, lift-off, a landing's banner, discovery XP and the host's welcome. |
| `WorldArcs` | The arc missions (driven missions), the Heartstone and Sepulchre trials, Tessaly's road, the arc's power under the slot rule, the Speed refund, expeditions and their second crystal, "Not now", Both Doors, the end of WORLD_ARC. |
| `TheHunt` | The sabotage, the two ambushes and their retries, the three clues, hunter bounties, Peaceful clues by journal, the challenge's gate. |
| `KaastDuel` | The marker beacon, the challenge, the camp (`HuntingCamps`), the ring that starts the fight, the yield and its three endings, the fight's rewards (owner, helpers, daily rematch), the stash, the report. |
| `ChapterFourDialogue` | The 8.10 conditions and actions (C9). |
| `ChapterFourCommands` | `/oldrepublic c4 ...` (C10). |
| `StoryData.Chapter4` | The saved state (C2). |
| `StoryGuidance`, `StoryMentors`, `StoryService` | The Calling's STORY target per stage, the mentors' Chapter Four lines, the hook routing. |
| `QuestEffects` | `seeker_scan` (variants `SEEKER_SCANNING` 0, `SEEKER_LEAVING` 1); `lesson_complete` variants 8 (`chapter4`), 9 (`planet`), 10 (`masterwork`). |
| `vendor.VendorItems` | `kaast_pattern`, `tomb_relic`, `sealed_contract`, the `marker_beacon` and `contract_terminal` blocks. |
| client `quest.SeekerFx`, `quest.JournalScreen`, `mentor.MentorClient` | The scan fan, the Chapter Four page, the banners. |

## C2. Saved state (12.1)

`StoryData.chapter4` (`Chapter4`, codec in `StoryData`; every field optional with a default, so a save without it loads
as `NEW`):

| Field | Meaning |
|---|---|
| `stage`, `wait_since` | `ChapterFourStage` ordinal (append only) and the game time the stage began. |
| `arc` | `planet` (the path world's id), `mission_flags` (bits `STEP_FIRST`, `STEP_SECOND`, `STEP_POWER`, `STEP_BOTH_DOORS`), `expedition` (0 not done, 1 done, 2 skipped). |
| `hunt` | `clues` (bit per clue), `sabotage_done`, `ambush_day`, `ambushes`. |
| `duel` | `camp` (GlobalPos), `outcome` (`KaastOutcome` ordinal or -1), `boss` (0, 1 yielded, 2 resolved). |
| `sites` | Named places: the beacon, the two ambush points, the hulk. |
| `back_pay` | Whether the M3 back pay was settled. |

One-time rewards are story flags (`c4.*`, `ChapterFourRules`): `c4.briefed`, `c4.hulk_placed`, `c4.part.<socket>`,
`c4.salvaged`, `c4.lift_off`, `c4.landed.<planet>`, `c4.hub.<planet>`, `c4.met.<host>`, `c4.power_granted`,
`c4.speed_refund`, `c4.expedition.<planet>`, `c4.crystal_pending.<planet>`, `c4.both_doors.<world>`,
`c4.sabotage_noted`, `c4.challenge_played`, `c4.choice.<outcome>`, `c4.kaast_paid`, `c4.stash_opened`, `c4.reported`,
`c4.motivator.<route>`. Per-day counters: `c4.hunter_day`, `c4.hunter_pity`, `c4.helper_day`, `c4.rematch_day`,
`c4.rematch_pity`, `c4.ambush_retry_day`, `c4.reissue.<item>`. Scenes and trials live in memory and restart on return.

**Old saves (8.8).** A player whose Chapter Three is COMPLETE and whose Chapter Four is NOT_STARTED enters MARKED on
their first slow tick after joining; the back pay (100 per M3 milestone, at most 300: attuned holocrons, the Collector's
skill point, rank 3 or the Pathfinder) is settled once, the first slow tick after Milestone 4 loads.

## C3. Stages (8.1)

`NOT_STARTED` → `MARKED` → `SEEKER` → `REPAIRS` → `FIRST_FLIGHT` → `WORLD_ARC` → `HUNTED` → `CHALLENGED` → `DUEL` →
`AFTERMATH` → `COMPLETE`. `ChapterFourRules.advance` refuses any move that is not forward; each entry runs once
(`ChapterFourService.onEnter`).

1. **MARKED** (8.2): due a day (`waitTicks`) after Chapter Three, or on waking from a real sleep (`minSleepTicks`), on
   the home world at a safe moment (the M2 safe moment and no hostile within `seekerHostileRange`). `SeekerScene` spawns
   W6's `seeker_droid` (falls back to an effect-only scene when the type is missing) `seekerDescend` blocks above a point
   3 ahead and 1.5 above the eyes, flies it down for 50 ticks, scans `seekerScanTicks`, sends `SEEKER_LEAVING`, climbs
   for 30 ticks and removes it. A struck droid leaves early; the stage still moves. Logout, death or a dimension change
   forgets the scene; the mark stays due.
2. **SEEKER**: entry `a_price`, codex `bounty_hunters`, a line naming who to talk to. The mentor's `c4:brief` (Jedi and
   Sith) or, for an Independent, holding the journal at Courier's Fall, gives the briefing: XP `briefXp`, the
   destinations (`TravelService.unlock`), the Engineering Bench, a discipline choice, and enters REPAIRS.
3. **REPAIRS** (8.2): the hulk is placed near the giver (`Ships.placeHulk`; retried every `retryTicks`, after
   `forwardFallbackAfter` failures placed `forwardFallback` blocks ahead). Each fitted socket pays `partXp` once. The
   motivator has three routes: Force Sense within `salvageRange` of a Wreck Field `HEART` (once, re-issued daily if
   lost), the officers' requisition stock, or "A Part Off the Books" (`c4_off_the_books`, which hands it over at
   completion with `offTheBooksXp` and `offTheBooksCredits`). Lift-off (`READY` → `LAUNCHING`) enters FIRST_FLIGHT.
4. **FIRST_FLIGHT** (8.3): any landing on the path world (Independents: either) ends it. A first landing on a planet
   shows its banner (`lesson_complete` 9) and pays `planetXp`; meeting the host pays `hubXp` and opens their dialogue.
5. **WORLD_ARC** (8.4): order-free steps per path, each a driven mission paying `arcMissionXp` (the power mission
   `arcPowerMissionXp`), `arcMissionCredits`, the faction's standing (`arcRep`, `arcPowerRep`) and entries. Jedi: "The
   Greenwatch" and "The Heartstone" (Barrier); Sith: "The Proving" and "The Sepulchre" (Choke); Independents: "Tessaly's
   Road" (Speed) and "Both Doors". The arc's power is granted under the slot rule (first free slot; with none free a hint
   shows and the step waits). Owning the power already completes the step; a bought Speed node is refunded
   (`c4.speed_refund`). The expedition (a planet's greater site) pays `expeditionXp` and `expeditionSp` once per
   expedition and offers a second crystal hue; "Not now" skips it and keeps the journal entry. All four done enter
   HUNTED.
6. **The trials.** Heartstone: three waves at `heartstoneWaves` (0, 30, 60 s), Barrier granted at `heartstoneGrantTicks`;
   it completes after `heartstoneTicks` once Barrier was raised since the third wave (else on the next raise). Sepulchre:
   Zarvos's echo; Choke on it grips for `sepulchreGripTicks` and breaks it. Leaving the `trialRing`, dying, logging out or
   changing dimension ends a trial and its spawns; it restarts on return.
7. **The hunt** (8.5) runs from FIRST_FLIGHT whatever the stage, so events before HUNTED are kept: the sabotage when the
   ship first lands at home after lift-off (clue 1, the hull to 10, `sabotageXp`), the first ambush `ambushMinDistance`
   from home `ambushSize[0]` hunters (clue 2), the second `ambushGapDays` later with a droid (clue 3 on the droid's
   death, `sealed_contract`). Fleeing beyond `ambushLeaveRange` ends an ambush; it retries after `ambushRetryDays`. On
   Peaceful no hunter spawns and the clues arrive by journal on the same triggers.
8. **CHALLENGED** (8.6): HUNTED with the arc done, three clues and level `duelLevel` places the marker beacon
   `beaconDistance` ahead and plays Kaast's recorded challenge (`c4_kaast_challenge`, once; the beacon replays it). The
   hunting camp is placed by `HuntingCamps.place` (retried); stepping inside `campRing` starts the fight
   (`KaastFight.begin`; on Peaceful the fight resolves straight to the yield).
9. **DUEL** and the yield: W6 calls `onBossYielded`; the `c4_kaast_yield` dialogue offers spare, strip or end
   (`kaast:resolve`), each paid once by `c4.choice.<outcome>`:
   spare `spareLight` light and `kaast_pattern`; strip Kaast's helmet and chest at Masterwork (W4's named items); end
   `endDark`, the Masterwork longshot rifle, Jedi trust `endJediTrust`, Sith trust `endSithTrust`. The owner's first
   clear pays Kaast's XP, skill points, credits and resonance from W6's `KaastBalance` (once, `c4.kaast_paid`); helpers
   and the daily rematch are paid from the same section with their own day counters and schematic pity. The camp's
   stash (`stashAlloy`, `stashFuel`, `stashCredits`) opens once.
10. **AFTERMATH / COMPLETE**: the report to the mentor (`c4:report`; Independents at the Compass) pays `reportXp`,
    `reportTrust`, `reportResonance`, unlocks every destination, shows the Chapter Four banner (`lesson_complete` 8)
    and completes the chapter. The ongoing loop: hunter bounties (`hunterChance` per `hunterCheckTicks` off-world), the
    daily rematch, planet contracts, vendors and commissions.

## C4. Rewards (every one guarded)

All XP goes through `Progression.addXp(p, xp, source)`; skill points through `grantSkillPoints`; credits through
`economy.Credits.give` (inventory, then a dropped stack); standing through `Identity`; trust and alignment through
`StoryService`. Every grant is behind a stage entry, a one-time flag or a per-day counter, so a repeated hook, a
relog or a fight resolved twice pays once (`StoryM4GameTests.kaastChoiceRewardsOncePerFlag`, `partsXpOnce`,
`cluesCountOnce`, `backPayOnceCapped300`).

## C5. Dialogues

`c4_hub_saelen`, `c4_hub_sorn` (the mentors' Chapter Four options), `c4_ansa_meet`, `c4_ansa`, `c4_kaddor_meet`,
`c4_kaddor`, `c4_kaast_challenge`, `c4_kaast_yield`, `c4_hunter_yield`, `c4_smuggler`, `c4_crystal`; the officers'
requisition and motivator options (`officer_republic`, `officer_imperial`); vendors and trainers (`vendor_*`,
`trainer_*`, with W5's `discipline:*` and `trainer:commission`); `apprentice`, `acolyte`. Speakers' portraits are in
`textures/gui/sprites/portrait/`.

## C6. The journal (8.9)

From SEEKER the left page is Chapter Four: the title, the objective with its counts (`ChapterFourRules.objective`:
parts n/4, clues n/3, the level gate), and during WORLD_ARC the arc's checklist (each step ticked, the expedition). Entries
(`JournalEntries`, Chapter Four block): `a_price`, `the_patient_star`, `a_written_off_courier`, `vaschs_courier`, `a_trade`,
`a_part_off_the_books`, `the_ship_flies`, `selvaren`, `varkhast`, `the_greenwatch`, `the_heartstone`,
`the_singing_hollow`, `the_proving`, `zarvos_sepulchre`, `the_rift`, `tessalys_road`, `both_doors`, `two_blades`,
`sabotage`, `first_ambush`, `the_buyer`, `the_challenge`, `kaast_spared`, `kaast_stripped`, `kaast_ended`, `report_c4`.
Codex: `bounty_hunters`, `selvaren`, `varkhast`, `the_watch_on_selvaren`, `the_proving_codex`, `force_speed`,
`force_barrier`, `force_choke`, `mandalorians`, `security_droids`, `hollowhorns`, `ash_hounds`, `disciplines`, `credits`,
`starships`, `the_buyer`, `torvin_kaast`. Off-world the journal's Calling points to the path world's host or the active
step.

## C7. Effects (13.3)

| Event | Sent | Look | Sound |
|---|---|---|---|
| `seeker_scan` variant 0 (scanning) | `toPlayer` (the marked player), entity = droid (or the player when there is no droid), intensity = scan ticks | A narrow fan of red lit motes from the droid's eye down the player, the scan line sweeping down and up every 30 ticks, a hotter core near the lens; nothing on the ground | `seeker.scan` |
| `seeker_scan` variant 1 (leaving) | `toPlayer` | The fan stops | `seeker.chirp` twice (7 ticks apart), `seeker.fly` |
| `lesson_complete` 8 | `toPlayer`, intensity = path | "Chapter Four complete" banner in the path's colour | `story.chapter_four` |
| `lesson_complete` 9 | `toPlayer`, intensity = planet | The world's name over its subtitle in the world's colour | `planet.arrive` |
| `lesson_complete` 10 | `toPlayer`, intensity = path | "Masterwork" banner | `story.lesson_complete` |

The fan is client-side only and stops when the scan's duration ends, the droid leaves, or the level changes
(`SeekerFx.reset` on a missing level or player).

## C8. Edge cases (12)

- Stages never move back; a command or a hook out of order changes nothing.
- The hulk, the camp and the trial sites retry on failure and log each try; nothing blocks the stage forever (the
  hulk's forward fallback, the camp's retry, the arc's power waiting for a free slot with a hint).
- Lost one-time items (the motivator, the relic, the beacon) are re-issued at most once per in-game day.
- Peaceful: no hunters spawn, the clues come by journal on the same triggers, and entering the camp's ring goes
  straight to the yield.
- Multiplayer: everything is per player. Helpers in another player's fight are paid as helpers once per day; the owner's
  rewards are the owner's alone.
- Disconnect, death, a dimension change: scenes and trials end and restart; ambush mobs are removed when their owner
  leaves; nothing is saved mid-scene.

## C9. Dialogue conditions and actions (8.10)

Conditions: `c4:stage`, `c4:stage_is`, `c4:clues`, `credits`, `c4:brief_ready`, `c4:report_ready`, `c4:yield`,
`c4:not_now`, `c4:both_doors`, `c4:crystal`, `c4:relic`, `c4:arc <mission>`, `c4:smuggler`, `c4:peaceful`.
Actions: `c4:brief`, `c4:report`, `c4:not_now`, `c4:both_doors <greenwatch|proving>`, `c4:mission <id>`,
`c4:crystal <blue|green|red>`, `c4:turn_in`, `c4:reissue`, `kaast:resolve <spare|strip|end>`, `vendor:open <stock>`,
`c4:smuggler`. Each action re-checks the chapter on the server. W3's `ship:state` and `ship:recall_free`, and W5's
`discipline:*` and `trainer:commission`, are used as their owners define them.

## C10. Commands (operators, every use logged)

`/oldrepublic c4 status|stage|seeker|clue|sabotage|backpay|camp|yield|report <player> [...]` (`ChapterFourCommands`).

## C11. Tests

`StoryM4GameTests` (17.1, 33 methods, each synchronous, `maxTicks` 20, mock players removed in `finally`):
`stagesOnlyMoveForward`, `oldSaveAtC3CompleteEntersMarked`, `seekerSceneOnSafeMomentOnly`,
`briefingPerPathGivesHulkBenchAndDiscipline`, `motivatorThreeRoutes`, `partsXpOnce`,
`firstFlightEndsOnAnyLandingOnPathWorld`, `arcOrderFreeAndPowerOwnedCompletesStep`, `grantedPowerWaitsForFreeSlot`,
`heartstoneCompletesOnlyWithBarrierSinceWave3`, `sepulchreGripReleasesAtGrant`, `speedRefundForBoughtNode`,
`arcMissionsPay150`, `expeditionSpOncePerExpedition`, `skipExpeditionKeepsJournalEntry`,
`huntEventsRecordedAsFlagsBeforeHunted`, `huntedOnlyAfterWorldArc`, `sabotageOnceWhenShipLandsHome`,
`ambushRetryAfterFleeing`, `clue3CountsOnDroidDeath`, `cluesCountOnce`, `challengedNeedsArcCluesAndLevel14`,
`oneTimeItemsReissuedOncePerDay`, `kaastChoiceRewardsOncePerFlag`, `reportUnlocksAllDestinations`,
`backPayOnceCapped300`, `peacefulCluesByJournal`, and the vendor tests listed in `vendor.md`.

Client steps (`m4/StoryClientSteps`, band 20 to 29): 20 `seeker_scan`, 21 `chapter4_journal`, 22 `ansa_dialogue`,
23 `kaddor_dialogue`, 24 `vendor_locked`, 25 `report_banner`.

## C12. Balance (`chapter_four`)

| Key | Default | Meaning |
|---|---|---|
| `waitTicks`, `minSleepTicks` | 24000, 100 | The mark's wait; the shortest sleep that counts |
| `seekerDescend`, `seekerScanTicks`, `seekerHostileRange` | 20, 40, 24 | The seeker's descent height, scan length, the safe moment's hostile range |
| `briefXp`, `partXp` | 300, 300 | The briefing; each fitted part |
| `salvageRange`, `offTheBooksXp`, `offTheBooksCredits` | 8, 1200, 100 | The motivator routes |
| `arcMissionXp`, `arcPowerMissionXp`, `arcMissionCredits` | 1500, 2000, 150 | Arc missions |
| `expeditionXp`, `expeditionSp` | 2000, 1 | Expeditions |
| `arcRep`, `arcPowerRep`, `arcPowerTrust` | 40, 60, 10 | Standing and trust |
| `planetXp`, `hubXp` | 400, 200 | A first landing; meeting the host |
| `heartstoneTicks`, `heartstoneGrantTicks`, `heartstoneWaves` | 1800, 900, [0, 600, 1200] | The cleansing |
| `heartstoneHollowhorns`, `heartstoneSentinels`, `trialRing`, `sepulchreGripTicks` | 2, 1, 12, 40 | Trial spawns, ring, the grip |
| `sabotageXp`, `ambushXp`, `ambushSize`, `ambushRange` | 300, 800, [3, 4], [24, 32] | The hunt |
| `ambushMinDistance`, `ambushGapDays`, `ambushLeaveRange`, `ambushRetryDays` | 96, 2, 160, 1 | Ambush placing and retries |
| `duelLevel`, `recommendedLevel`, `beaconDistance`, `campRing` | 14, 15, 6, 20 | The challenge |
| `stashAlloy`, `stashFuel`, `stashCredits` | 2, 4, 200 | The camp's stash |
| `reportXp`, `reportTrust`, `reportResonance` | 800, 10, 300 | The report |
| `spareLight`, `endDark`, `endJediTrust`, `endSithTrust` | 10, -10, -15, 10 | The endings |
| `hunterBounties`, `hunterChance`, `hunterCheckTicks` | true, 0.10, 1200 | The ongoing loop's bounty hunters |
| `hunterSchematicChance`, `hunterSchematicPity` | 0.08, 10 | A killed hunter's Mandalorian schematic piece (only hunters whose loot counts) |
| `retryTicks`, `forwardFallbackAfter`, `forwardFallback` | 1200, 3, 12 | Placement retries |

Kaast's own numbers (XP, skill points, credits, resonance, rematch rewards, schematic pity) are read from W6's
`KaastBalance`.
