# Chapter Three: "The Ones That Sing" (Milestone 3)

Owner: W2 (story, missions and identity), part A. Spec: `docs/MILESTONE_3.md` sections 6 (all), 5.8 (rank 3),
8.6 to 8.9 (the Collector's endings and rewards), 10.1 (the Sense provider), 13.3 (story rows), 14 (`chapter_three`)
and 17. Missions, contracts, the board, the tracker, discovery and kill reputation are W2's too and are documented in
`docs/design/mission.md`; this file covers the chapter itself, the Collector's rewards, rank 3 and the Pathfinder, the
journal's Chapter Three page and the dialogue engine's argument actions.

Everything is server-authoritative. The client draws what the server sends: the synced `StoryData`, effect events,
dialogues and the Calling.

## S1. Classes (`com.oldrepublic.quest.story` unless noted)

| Class | Role |
|---|---|
| `ChapterThreeService` | The public facade (`balance`, `addLead`, `onContractCompleted`, `addJournalNote`, `enterStage`, `checkWaiting`), the stage-entry logic, the tick and slow tick, placement retries, and the hook handlers `QuestService` routes to. |
| `ChapterThree` | The frozen 15.1 accessor (`stage`, `atLeast`, `site`, `shrines`), real bodies over `Story.peek`, so it works on both sides. `site(p, FALL)` is the Courier's Fall pedestal, `site(p, RELIQUARY)` the heart. |
| `ChapterThreeRules` | Pure rules: forward-only stages, the wait, leads, the level gate and its Calling target, cairn masks, holocron kinds and the first echo, the shrine guarantee, co-op stances, endings, clashes, daily rewards and the emitter pity, report and send-word eligibility, the late-join refusal, the rank 3 / Pathfinder requirement list, the journal objective and notes. Used by the game tests and the client. |
| `ChapterThreeBalance` | Balance section `chapter_three` (S18). |
| `OnesThatSing` | The wait and the dream (S3). |
| `ChapterThreeOpenings` | The Jedi and Sith lesson, the cairns, Courier's Fall, its scavengers, the Compass and the caches, LISTENING's rewards (S4, S5). |
| `CairnSense` | The Force Sense provider for the player's own cairns and caches (S5). |
| `FirstEcho` | The first-echo target (locator, shrine fallback, retries) and the old-world guarantee (S6). |
| `VaschBeats` | Vasch's recorded message and the counter-hunt (S7). |
| `CollectorRewards` | The meet, the yield, the owner's ending, the helpers' stances, the first clear, the other helpers, the echo and Unchained rewards, the Archive's fate, the report and send word (S9, S10). Reads W5's `boss.CollectorBalance`. |
| `RankThree` | Rank 3 and the Pathfinder: the requirements read from synced data, the ceremonies, the Courier's Fall pedestal use (S11). |
| `SendWord` | Send-word eligibility (both sides) and its dialogue (S10). |
| `BrennCameo` | Brenn beside the Imperial officer (S12). |
| `StoryGuidance` | The STORY provider of the journal's Calling and the journal-hold pulse (S14). |
| `ChapterThreeDialogue` | The `c3:*`, `rank3:*` and `collector:*` conditions and actions (S15). |
| `ChapterThreeCommands` | `/oldrepublic c3 ...` (S17). |
| `ChapterThreeFeature` | Registration, called from `StoryFeature.init`. |
| `quest.Rewards` | Shared reward helpers: `give` (inventory first, the rest dropped at the feet for the owner only), `roll` / `giveLoot` (a loot table rolled as a chest at the player with a seed), `seed` (per player, occasion and salt). |
| `quest.QuestService` | Routes every M3 `QuestHooks` event: missions and kill reputation to `mission.MissionHooks`, the rest here (S19). |
| `dialogue.Dialogues`, `DialogueActions` | Argument actions (S15). |

## S2. Stage machine

`ChapterThreeStage` (saved by ordinal in `StoryData.chapter3`, append only):
`NOT_STARTED → WAITING → SUMMONS → LISTENING → FIRST_ECHO → LEADS → RELIQUARY → CONFRONTATION → AFTERMATH → COMPLETE`.

Every change goes through `ChapterThreeService.enterStage`, which calls `ChapterThreeRules.advance` (forward only) and
runs the new stage's entry logic once. Every reward is guarded by that change or by a one-shot flag, so a repeated
dialogue option, command or hook never pays twice.

| Stage | Entered when | Entry logic |
|---|---|---|
| `WAITING` | Chapter Two's path `COMPLETE` (Jedi, Sith) or flag `no_master` (Independent); checked every 20 ticks, so M2 saves at `COMPLETE` enter it on join with the wait counted from now | `waitSince` = now |
| `SUMMONS` | the dream ends or is cut short | entries `the_ones_that_sing`, `two_flags`; codex `two_flags`; `RepublicSeed.apply` (one-shot, the mission feature's); `Outposts.ensureNear`; Independent: Courier's Fall placed; mentor paths: the mentor's summons line |
| `LISTENING` | `c3:accept_lesson` (Jedi, Sith) or the Compass attuned at the fall (Independent) | the three cairns or caches placed |
| `FIRST_ECHO` | all three cairns or caches touched | target search (or at once on to LEADS if a holocron of the right kind is already attuned) |
| `LEADS` | the first echo attuned | lead `ledger` if Chapter Two found the Ledger; earlier leads count now |
| `RELIQUARY` | `leadsNeeded` leads and level ≥ `reliquaryMinLevel` | `ReliquarySites.place` (retries) and entry `the_reliquary` |
| `CONFRONTATION` | the owner enters `boss/reliquary_hall` around their heart, or any boss phase reaches them within 48 of it, or the boss yields | none |
| `AFTERMATH` | the owner's ending (`collector:resolve`), the fight resolved, or a helper's stance | none (the Archive choice is W3's) |
| `COMPLETE` | the report | none |

`StoryData.Chapter3` fields (frozen, Phase 0): `stage`, `waitSince`, `leads` (≤ 16, kept in order), `cairns` (≤ 3),
`cairnsTouched` (bit mask), `fall` (the fall's pedestal), `shrines` (≤ 4), `reliquary` (the heart), `boss` (0 none,
1 yielded, 2 resolved), `outcome` (the owner's `BossOutcome` ordinal or -1), `stance` (a helper's own, or -1),
`echoDay` (day of the last rewarded rematch), `echoClears`.

Flags (`StoryData.flags`): `c3.collector_sp`, `c3.vasch_message`, `c3.ambush`, `c3.vasch_offer`, `c3.give_back`,
`c3.lesson_accepted`, `c3.listening_paid`, `c3.fall_crew`, `c3.hoard_decided`, `c3.hoard.<return|keep|unmake>`,
`c3.report_done`, `c3.rank3_done`, `c3.pathfinder_done`, `c3.brenn_met`, `c3.dream_due`,
`c3.extra_shrine.<jedi|sith|wayfarer>`, and the journal notes `note.<entryId>|<langKey>`. Counters
(`StoryData.counters`): `c3.echo_pity`, `c3.helper_day`, `c3.place_failures`. Retry timers, first-echo targets, the
dream timer, the counter-hunt and the cameo are in memory only: a restart retries at once, searches again and replays
an unfinished dream.

## S3. The wait and the dream (6.1, 13.3)

- WAITING lasts `waitTicks` (24,000) in any dimension. Waking from at least `minSleepTicks` (100) of sleep while
  WAITING sets `c3.dream_due` (saved, so a relog still plays it).
- The dream plays at the M2 safe moment (not hurt by a mob lately, on the ground, not in lava, not asleep) and only in
  the overworld: `awakening_dream` variant 3 for `dreamTicks` (120), three subtitled lines and the `story.ones_that_sing`
  bed. The overlay shows the two armies' banners as silhouettes against a dusk glow and a crystal glowing in a split
  stone (pulsing slowly, steady with flashing effects off).
- Damage cancels it (`awakening_dream` variant 1, the bed stops) and the chapter still moves to SUMMONS. The bed also
  stops on death, logout and dimension change (the client's dream watchdog). A logout or dimension change mid-dream
  replays the dream later.

## S4. Summons and the openings (6.2)

**Jedi "Listen" / Sith "The Hunt".** At SUMMONS the mentor's dialogue opens on `c3_summons`; "I will listen." / "Show me
the hunt." runs `c3:accept_lesson`: Force Sense granted free (`Skills.grant`), the hood and robe of the path's robe set
through `ArmourSets.piece` (inventory first, else dropped for the player alone; a missing piece is logged), entry
`listen` / `the_hunt`, codex `force_sense`, the mentor's task line, then LISTENING.

**Independent "Courier's Fall".** At SUMMONS the fall is placed on `fallRings` (64/80/96, then 32/48) from the player
with the M2 site rules (15×15 footprint, 19×19 clearance), `story.siteMinSeparation` from every story and mission
site of the other online players and from the owner's own (`Crossing.addSites`: meeting, camp, raider camp, the fall,
shrines and Reliquary heart, plus the others' active mission camps, 3.6); a failure retries every `retryTicks` and after
`forwardFallbackAfter` failures it is centred 12 blocks ahead, its cargo bay facing the player. The Calling points
there (variant `FALL`). When the owner first comes within `missions.siteSpawnDistance` (48), two crystal poachers spawn once at `SCAVENGER_1/2`
(flag `c3.fall_crew`). Attuning `wayfarers_compass` (W3's pedestal flow) grants Force Sense, entry `couriers_fall`
with Vasch's sealed invitation as a journal note, codex `force_sense`, and LISTENING with three caches.

## S5. LISTENING: cairns and caches (6.2, 10.1)

- Jedi and Sith: `EchoCairns.place(owner, camp, 0..cairnRadius, cairnSpacing, cairnCount, false)` around the mentor's
  camp. Independent: `EchoCairns.place(..., cacheMin, cacheMax, cairnSpacing, 3, true)` around the fall. Nothing placed
  retries every `retryTicks`; after `forwardFallbackAfter` failures the centre moves to the M2 forward fallback.
- Each touch (W1's `onCairnTouched`) sets its bit once, sends `cairn_touch` (variant 0 stone, 1 cache) and plays one
  mentor line (Jedi, Sith) or a cache line plus the loot `gameplay/wayfarer_cache` (2 durasteel, 1 lumen thread or grave
  silk). The action bar shows "n / 3".
- The third touch (flag `c3.listening_paid`): Jedi/Sith 400 XP, mentor trust +10, own faction +40; Independent 400 XP,
  Frontier +40; entry `what_the_force_hides`; FIRST_ECHO.
- **Sense provider** (`CairnSense`, registered with `SenseTargets`): during LISTENING the caster's own untouched cairns
  or caches within `cairnSenseRange` (24) are revealed through `EchoCairns.reveal(level, pos, owner,
  SiteBalance.cairnRevealTicks)` and reported as `SenseTargets.Point` (`BLOCK` for a cairn, `CACHE` for a cache). It
  reads the saved positions only; there is no block scan.

## S6. The first echo and the shrines (6.3, 4.3)

- On entry: if the player already attuned a world holocron that ends the first echo (Jedi a Jedi one, Sith a Sith one,
  Independent a Wayfarer one other than the Compass), it counts at once: lead `holo:<id>`, the "careful man" note under
  `first_echo`, and on to LEADS.
- Else `StructureLocator.find(p, kind, firstEchoRadius = 768, HolocronService.usedSites(p), callback)` for the path's
  site kinds (Jedi `JEDI_RUIN`, Sith `SITH_TOMB`, Independent the nearest of `WRECK_FIELD`, `JEDI_RUIN`, `SITH_TOMB`). The
  nearest found pedestal is the target. Nothing found: `EchoShrines.place(owner, kind)` (after `forwardFallbackAfter`
  failures on the M2 forward ring), appended to `Chapter3.shrines`; with four shrines already, the last one is reused.
  Neither works: logged and retried every `retryTicks`.
- The Calling (`HOLOCRON` variant, data = `HolocronKind` ordinal) points at the target. The first world holocron of
  the right kind attuned from FIRST_ECHO on ends the stage: entry `first_echo`, codex `the_collectors_trail`, lead
  `holo:<id>`, LEADS.
- **Old-world guarantee:** from LEADS on, while the journal is held, if the holocrons rank 3 needs (2 of the path's
  kind; the Pathfinder 3 of any kind) are still missing and no unused site of that kind is found within 768 blocks
  (an Independent: no unused wreck, ruin or tomb, the same three site kinds as the first echo),
  one extra Echo Shrine of the needed kind is placed (once per kind, flag `c3.extra_shrine.<kind>`, never beyond four
  shrines). The Calling then points at an unused shrine (outside missions).

## S7. Leads, Vasch's beats and the level gate (6.4)

- Leads (≤ 16, each once): `ledger` (on entering LEADS with flag `ledger_found` or `ledger_delivered`), `holo:<id>` (any
  world holocron attuned from FIRST_ECHO on), `mission:<id>` (the mission feature calls `addLead` when a story mission
  ends), `contract` (`onContractCompleted`, once, only during LEADS). Leads found before LEADS are kept and count when
  it begins. Each new lead at LEADS shows "Lead: ... (n / 4)".
- **Vasch's message** at `vaschMessageLeads` (2): the condition `c3:vasch_message` is met for W3's gatekeeper node; its
  action marks it heard (`c3.vasch_message`). An attunement made with the leads already there marks it heard too.
- **Counter-hunt** at `ambushLeads` (3), once (`c3.ambush`): the next time the player is in the overworld on the surface
  (at or above the column's highest motion-blocking block), at least 64 blocks from every site and story place, the
  `raider_camp_small` crew (`Missions.campCrew`: the `clear_camp` definition's 3, or 4 at level ≥ 9) spawns through
  `Poachers.spawnCrew` 24 to 32 blocks away at a spot the player cannot see,
  target the player and bark; a journal line follows. They are removed if the player gets 128 blocks away, logs out
  or changes dimension; one found after a restart is discarded.
- **Level gate:** `leadsNeeded` leads below level 9 holds LEADS. The player is told once per `retryTicks × 10`; the
  journal's left page shows the gate and the XP sources; the Calling points at the nearest unused chamber of the path's
  kind, else at the nearest outpost board (the mentor's army; an Independent's nearer one), unless a mission is active.
  A level-up re-checks.

## S8. The Reliquary and the confrontation (6.5)

`ReliquarySites.place(owner, playerPos, look)` (W5) gives the heart, stored in `Chapter3.reliquary`; a failure is
logged and retried every `retryTicks` (no forward fallback). With `collector.placeReliquary` false only
`/oldrepublic c3 reliquary` places it. Entry `the_reliquary`; the Calling `RELIQUARY` points at the heart; the objective
names the recommended level (10). Crossing into the hall around the heart, any boss phase within 48 blocks of it, or
the boss yielding moves the owner to CONFRONTATION.

## S9. The Collector (8.3, 8.7 to 8.9)

- **Meet** (`c3_collector_meet`, opened by W5): "Give them back." (`c3:give_back`, MINOR light `meetGiveBackLight`, once);
  "Why?" (lore); an Independent hears his offer and answers "No." or "Not like this." (`c3:vasch_offer`, flag only).
- **Yield** (`onBossYielded`): the owner at CONFRONTATION gets `boss` = 1 and `c3_collector_yield`; every listed player
  in the arena within 48 blocks whose chapter is RELIQUARY or CONFRONTATION and who has no stance yet gets
  `c3_collector_stance`. W5's arena session opens the same two dialogues (with Vasch as the speaker), so each is
  opened here only when it is not already showing.
- **Owner's ending** (`collector:resolve <spare|execute|bind|release|bring_in_republic|bring_in_imperial>`): allowed per
  path (Spare: Jedi, Sith; Execute: any path; Bind: Sith; Release and Bring in: Independent), only once and only at
  CONFRONTATION with `boss` = 1. Records `collector.<spare|execute|bind|release|bring_in>`, applies the `collector.*`
  values (alignment, standing, trust, Bring in: +`bringInRep` and `bringInCommendations` of that army's commendation),
  unlocks `collector_spared|executed|bound|released` (codex `the_collector_bound` on Bind), calls
  `BossArenas.resolve` and moves to AFTERMATH.
- **Helper's stance** (`c3:stance <spare|execute|bind|release>`): a co-op helper at RELIQUARY or CONFRONTATION, once,
  allowed per path as above (Release: Independent only). W5 reports every participant in the tick of the owner's choice,
  so a helper still reading the stance dialogue is credited first: the defeat sets flag `c3.coop_helper`, and
  `ChapterThreeRules.stanceOpen` keeps their stance open once in AFTERMATH. Their own alignment, standing, trust and entry; their chapter
  jumps to AFTERMATH; `HolocronService.markArchivePending` gives them their own Archive choice. The world follows the
  owner.
- **Defeat** (`onBossDefeated`, W5 calls it once per participant):
  - the owner: `boss` = 2, `outcome`, AFTERMATH, the outcome's entry, then the first clear;
  - a co-op helper (at RELIQUARY or CONFRONTATION, or moved on by their stance and not yet paid): the world outcome
    noted, "You fought beside ..." and a journal note under `the_collector`, AFTERMATH, their Archive choice, the
    first clear;
  - **first clear**, once (flag `c3.collector_sp`): `ownerXp` (3,000), `ownerSp` (+2), Resonance `collector`
    +`resonance` (600), `SaberItems.POMMEL_ARCHIVIST` and `M3Items.SINGING_CRYSTAL` (owner-only drop when full), entry
    and codex `the_collector`, banner `boss` (`lesson_complete` variant 6, `story.collector_fallen`);
  - any other helper: `otherHelperXp` (1,000) and `otherHelperShards` holocron shard(s), once per game day (counter
    `c3.helper_day`); their chapter does not move.
- **Echo rematch** (`outcome == ECHO`): once per game day (`Chapter3.echoDay`): `echoXp` (600), `echoDurasteel` (2),
  `echoShards` (1) and a `gameplay/echo_cache` roll; `echoClears` + 1. The emitter `SaberItems.EMITTER_RELIQUARY` has one
  source: an `emitterChance` (25%) roll per cache roll, or the `emitterPity`-th rewarded clear in a row without one (counter
  `c3.echo_pity`, reset when it comes); at most one per clear. An emitter that W5's `echo_cache` table rolls itself is
  skipped, so the table's own 1-in-4 entry never doubles the chance. A second rematch that day says so and pays nothing. **Unchained** (W5's
  `BossArenas.isUnchained(player)` while the hook runs, or a boss id containing `unchained`): `unchainedXp` (900) and
  `unchainedRolls` (2) cache rolls.

## S10. Aftermath, the report and send word (4.8, 6.6, 5.3)

- `onArchiveResolved` (W3): entry `archive_returned|archive_kept|archive_unmade`, codex `archive_unmade` on Unmake, flags
  `c3.hoard_decided` and `c3.hoard.<choice>`, and a line saying where to report.
- **Report**, once (`c3.report_done`): Jedi and Sith at their mentor (`c3_hub` "I have a report." → `c3:report`), an
  Independent at any officer whose army is above Hostile, Wary included (`c3:report <republic|imperial>` in the officer
  dialogues). 600 XP, Resonance `chapter_three` +300, trust +10 (mentor paths) or that army +50 (Independent), entry
  `report_watch|report_overseer|report_officer`, banner `chapter3` (`lesson_complete` variant 5,
  `story.lesson_complete`), COMPLETE. The mentor's lines react to the outcome, the hoard and a helper's clashing stance
  (custody, freedom and death are the three groups).
- **Send word:** an Independent whose report is open while both armies are Hostile sneak-uses the journal
  (`JournalItem`, checked on both sides through `SendWord.eligible`); `c3_send_word` → `c3:send_word` completes the
  report with Frontier +50 instead of an army's standing.
- **Late join:** from SUMMONS on, `StoryRules.lateJoin` refuses and `joinBlocker` answers "Your road is already chosen.
  Walk it." (`message.oldrepublic.story.blocked.road_chosen`).

## S11. Rank 3 and the Pathfinder (5.8, 10.4)

`ChapterThreeRules.requirements(Standing, balance)` is the one rule; `RankThree.standing(Player, level)` reads it from
the synced story, identity, holocron and mission data, so the Standing checklist and the dialogue's fail reason agree.
The trial is Chapter Three COMPLETE (the confrontation won). Fail reasons are `message.oldrepublic.story.rank3.<id>|<need>`,
naming the first unmet requirement.

| Path | Requirements, in order |
|---|---|
| Jedi (Knight of the Watch), Sith (Lord) | trial; level ≥ 11; mentor trust ≥ 80; own order ≥ 500; alignment ≥ +15 (Sith ≤ -15); 2 world holocrons of the path's kind; 3 own-side contracts |
| Independent (Pathfinder title) | trial; level ≥ 11; 3 world holocrons of any kind (the Compass counts, Archive holocrons never); Trusted (≥ 100) with at least one faction; 3 own-side contracts |

- **Ceremony** (`c3_ceremony` → `rank3:ceremony`, once, flag `c3.rank3_done`): `Identity.promote(p, 3)`, +2 SP, Resonance
  `second_rank` +700, 1,000 XP, `jedi_knight_robe` / `sith_lord_robe` (W4's items by id; a missing one is logged), node
  `knights_vigil` / `lords_dominion`, entry `knight_of_the_watch` / `named_lord`, `rank_ceremony` variant 0 (the Knighting:
  blade-light on one shoulder, then the other, `story.blade_touch` each) or 1 (the Naming: the writ's seal cracks, the
  old-name tablet breaks, `story.tablet_break`), banner `rank3` (`lesson_complete` variant 7, `story.rank_three`).
- **Pathfinder:** when ready, the Calling (`FALL`) leads an Independent back to Courier's Fall. Using its pedestal
  (`UseBlockCallback`, within 2 blocks of the recorded pedestal) sends `rank_ceremony` variant 2 (Tessaly Arn's figure
  projects from the Compass, `holocron.gatekeeper_form`) and opens `pathfinder`; "Pathfinder" runs `rank3:ceremony`
  (within 8 blocks of the pedestal): +2 SP, `second_rank` +700, 1,000 XP, node `pathfinders_poise`, choice
  `title.pathfinder`, entry `pathfinder`, banner `rank3`. The rank does not change. Any other use of the pedestal passes
  to W1's block.
- **Standing panel** (`client.identity.StandingPanel`, Path page): a titles row (the `title.*` identity choices) and, for
  a rank 2 Jedi or Sith or an Independent, the rank 3 / Pathfinder checklist with ✓/✗ and the player's values. The client
  uses `new ChapterThreeBalance()` defaults, since balance is server-side.

## S12. Brenn's cameo (5.4)

A Sith who spared Brenn (flag `brenn_spared`) finds him beside the Imperial officer on their first Imperial outpost visit
(`onSiteEntered` with an `IMPERIAL_OUTPOST` piece, flag `c3.brenn_met`): the M2 acolyte in `RIVAL` mode, invulnerable,
noSave, spawned for that player only, tagged `oldrepublic.c3.brenn_cameo`, with a greeting line. He is removed when the
player is 64 blocks away, logs out or changes dimension; one found after a restart is a leftover and is discarded.

## S13. Journal (6.7)

- **Chapter Three page** (`client.quest.JournalScreen`, left page from SUMMONS on): the chapter title, the objective
  with its counts (`quest.oldrepublic.c3.objective.*`: cairns n/3, leads n/4, the gate's level, the recommended level),
  the Collector waiting on an answer at CONFRONTATION, and from LEADS to RELIQUARY the trail: each lead ticked with its
  line (`quest.oldrepublic.c3.lead.<lead with ':' as '.'>`), open slots up to four, and the level gate with the XP
  sources.
- **Notes** (`addJournalNote`, flag `note.<entryId>|<langKey>`): extra paragraphs under an entry, sorted by key. Used by
  the chapter (the careful man, Vasch's invitation, "You fought beside ...") and by the mission feature (story mission
  and cameo notes).
- `JournalScreen.openOnTab(tabId)` opens the journal on its own pages (`journal`, `codex`) or a `JournalTabs` id such
  as `missions` (client test steps).
- Entries: `the_ones_that_sing`, `two_flags`, `listen`, `the_hunt`, `couriers_fall`, `what_the_force_hides`, `first_echo`,
  `the_reliquary`, `the_collector`, `collector_spared|executed|bound|released`, `archive_returned|kept|unmade`,
  `report_watch|overseer|officer`, `knight_of_the_watch`, `named_lord`, `pathfinder` (the four mission entries are the
  mission feature's). Codex: `two_flags`, `force_sense`, `telekinetic_lift` (an Independent attunes `wayfarer_pell`),
  `holocrons` (first world holocron), `the_collectors_trail`, `the_collector`, `the_collector_bound`, `archive_unmade`,
  `era_armour` (`onArmourEquipped`).

## S14. The Calling (5.7)

`StoryGuidance.request` is the STORY provider (priority 100): SUMMONS (Independent) the fall, `FALL`; FIRST_ECHO the
target, `HOLOCRON` + kind; LEADS level-gated the chamber or board (`HOLOCRON` + kind or `OUTPOST` + army), else an unused
extra shrine; RELIQUARY the heart, `RELIQUARY`; COMPLETE (Independent ready for the title) the fall; else an unused
shrine. The gate and shrine requests step aside while a mission is active. The journal-hold tick
(`StoryGuidance.journalTick`), every `missions.guidanceIntervalTicks` (40) while the journal is held in the overworld and
no Chapter One or Two Calling is in play, runs the old-world guarantee and calls `Guidance.pulse`, which sends one
`calling` event for the best request of every provider. The client draws variants 4 to 8 (`CallingFx`, S16). The exact
position is never sent.

## S15. Dialogues and the engine (6.8)

**Argument actions.** An option's `"action"` may carry one argument after a space (`"collector:resolve spare"`,
`"mission:start r1_silent_patrol"`). `Dialogues.runAction` resolves, in order: an action registered under the exact text;
then the id before the first whitespace and the trimmed argument as `id:arg`, as `id_arg`, and finally the argument
action `id` (`DialogueActions.registerArg(id, (p, speaker, arg) -> ...)`, duplicate ids throw like `register`); anything
else is logged once. Actions registered in any of these forms keep working.

| Conditions | Met when |
|---|---|
| `c3:stage <STAGE>` | stage ≥ the argument (any case) |
| `c3:stage_is <STAGE[,STAGE]>` | stage is one of them |
| `c3:leads <n>` | at least n leads |
| `c3:report_ready <mentor\|officer>` | AFTERMATH, hoard decided, not reported; `mentor` = Jedi/Sith, `officer` = Independent |
| `c3:vasch_message` | Vasch's message is due (W3's gatekeeper node) |
| `rank3:ready` | rank 3 / Pathfinder requirements met; the reason names the first unmet |
| `c3:returnable <jedi\|sith\|republic\|imperial>` | the player carries a holocron that receiver takes back (W3's `HolocronReturns.candidate`, 4.6): Saelen's option also shows for a Jedi's sealed Sith holocron |

Actions: `c3:accept_lesson`, `c3:report` (mentor), `c3:report <republic|imperial>` (officer), `c3:send_word`,
`c3:stance <spare|execute|bind|release>`, `c3:give_back`, `c3:vasch_offer`, `c3:vasch_message`,
`collector:resolve <outcome>`, `rank3:ceremony`. Every action re-checks the chapter on the server.

Dialogue files: `c3_collector_meet`, `c3_collector_yield`, `c3_collector_stance`, `c3_send_word`, `pathfinder`, and
`mentor_jedi` / `mentor_sith` extended: `c3_summons` (how, who, the lesson), `c3_report` when the report is open, and
`complete` → `c3_hub` (≤ 6 options: the lesson, the report, today's duty `mission:start duty`, holocron return
`holocron:return <jedi|sith>`, shown by `c3:returnable`, the ceremony, back).

## S16. Effects and sounds (13.3)

| Moment | Event | Visual | Sound |
|---|---|---|---|
| The dream | `awakening_dream` 3 (toPlayer) | banners and the split stone, three subtitled lines | `story.ones_that_sing` bed (stream), stops on end, cancel, death, logout, dimension change |
| Calling, holocron | `calling` 4 | pale motes along the direction, warm gold / rust / brass by kind | `story.calling_holocron` swell |
| Calling, contract | `calling` 5 | thin ash-grey motes at eye height | the Calling chime pitched down |
| Calling, Reliquary | `calling` 6 | deep amber glints, more when closer | `story.calling_reliquary` hum, louder when closer |
| Calling, outpost | `calling` 7 | faint blue-white (Republic) or crimson (Empire) motes | the Calling chime |
| Calling, the fall | `calling` 8 | `falling_ember` streaks slanting down toward the fall | `story.calling_fall` crackle |
| Cairn or cache touched | `cairn_touch` (around) | the stone (grey) or satchel (brown) settles into a low puff of dust, one pale gold mote rises to the toucher | `story.cairn_touch` |
| Knighting | `rank_ceremony` 0 (around) | blade-light on one shoulder then the other, dust drifting down | `story.blade_touch` ×2 |
| Naming | `rank_ceremony` 1 (around) | the writ's red seal cracks with sparks, the tablet breaks in grey dust and chips | `story.tablet_break` |
| Pathfinder | `rank_ceremony` 2 (around) | Tessaly Arn's figure from the Compass (W3's gatekeeper figure), rising shimmer; folds after the dialogue | `holocron.gatekeeper_form` |
| Milestone banners | `lesson_complete` 4 to 7 (toPlayer) | banner in the path colour: mission, Chapter Three, the Collector, rank 3 / Pathfinder | `mission.complete`, `story.lesson_complete`, `story.collector_fallen`, `story.rank_three` |

All of it goes through `ClientEffects` and `Vfx` (range, budget, flashing). No ground circles, sigils or halos.

## S17. Commands (operators, every use logged)

`/oldrepublic c3 status <player>`, `c3 stage <player> <stage>` (forward only, runs the entry logic), `c3 lead add
<player> <lead>`, `c3 reliquary <player>` (places the Reliquary now, also with `collector.placeReliquary` off).

## S18. Balance section `chapter_three`

All the numbers above live in `ChapterThreeBalance` (spec 14 names), read through `BalanceConfig.section`. Two
additions to spec 14: `dreamTicks` (120, the dream's length) and `cairnSenseRange` (24, the Sense provider's reach).
The Collector's values are W5's `collector` section, read only inside `CollectorRewards` with explicit casts.

## S19. Hook routing, persistence and multiplayer

- `QuestService`: `onSiteEntered` → `MissionHooks` then the chapter (hall, Brenn); `onForceSense`, `onTrooperKilled`,
  `onTrooperTended`, `onConstructKilled` → `MissionHooks`; `onCairnTouched`, `onHolocronAttuned`, `onArchiveResolved`,
  `onArmourEquipped`, `onBossPhase`, `onBossYielded`, `onBossDefeated` → the chapter; `onYieldResolved` → the M2 story,
  then `MissionHooks`; `onLevelUp` → the level gate; `onPuzzleSolved` has no story step.
- `StoryData` is sealed, persistent, copied on death and synced to its owner; every change commits a sealed copy and
  re-reads.
- Each player has their own chapter, places and timers. Co-op is handled at the fight (S9). Story entities (the fall's
  scavengers, the counter-hunt, Brenn) carry the owner and are removed with them.

## S20. Tests

`StoryM3GameTests` (17 own tests and 8 delegations to `MissionGameTests`), all pure rules or listed mock players with
the default empty structure, `maxTicks` 20 or 40, no world or global state: `stagesOnlyMoveForward`,
`oldSaveAtCompleteEntersWaiting`, `chapter3CodecRoundTripAndCopyOnDeath`, `saveWithoutChapter3Loads`,
`leadsCountOnceAndBeforeLeads`, `levelGateKeepsCallingOnAChamberOrBoard`, `firstEchoFallsBackToShrine`,
`firstEchoAlreadyMetAdvancesAtOnce`, `extraShrineOncePerKind`, `lateJoinRefusedFromSummons`,
`coopHelperJumpsToAftermathWithOwnStance`, `rank3MatrixAndFailReasons`, `pathfinderNeedsTrustedAndThreeHolocrons`,
`independentReportAtWary`, `sendWordWhenBothArmiesHostile`, `collectorRewardsOncePerFlag`, `otherHelperRewardOncePerDay`.
Client screenshots 20 to 29 come from `gametest.m3.StoryClientSteps` (its Javadoc lists the shortcuts).

## S21. Not verified here

- The Collector's values come from W5's `boss.CollectorBalance` (field names per spec 14, read with casts; all 30 fields
  checked against W5's unmerged class). W5 opens the meet dialogue and calls `onBossYielded` and `onBossDefeated` as 8.9
  says. W5 reports every echo as `collector`, so the Unchained mode is read from its `BossArenas.isUnchained(player)`
  (in W5's unmerged code, not in the Phase 0 stub).
- The Courier's Fall pedestal is written by `gen_structures_story_m3.py` with W1's block state `kind=wayfarer`,
  `always_ready=true` and block entity `fixed_id = "wayfarers_compass"`.
- No Java build runs in the worker container: everything above is reviewed against the API dumps and compiled by the
  lead on CI.
