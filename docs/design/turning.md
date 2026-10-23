# Paths and ranks: the Fall, the Return, Master, Darth and Wayfarer (Milestone 5)

Owner: W5 (paths and ranks). Spec: `docs/MILESTONE_5.md` sections 8 (all), 6.11 (the conditions and actions W5
registers), 14.1 (saved state), 15.1 and 15.3 (`turning`, `rank_four`, `lesson_complete` 12 and 13), 15.4 (sounds), 16.4
(`turning`), 17.1 and 17.2 (the contracts used), 18 and 19. The art and sound are in `docs/assets/turning.md`.

Everything is server-authoritative. The client draws the rank 4 checklist it is sent (`RankFourClient`, read by W4's
Standing panel), the effect events (`TurningFx`), W4's banners and the dialogues. Other workers are reached only through
their Phase 0 contracts (`StoryMentors.comeTo`, W8's `MentorDuel`, W3's `PlanetSites` and `SiteAnchors`, `SpawnerGroups`,
`Troopers`, W4's `HubNpcs` and the hub people's escort runtime, W1's `Ships` and `ShipRegistry`, `PlanetHubs`, W6's
`PowerDialogues`, `SkillService`, `Skills`, `Identity`, `Progression`, `Rewards`, `DialogueRedirects`, `Guidance`), so
each step runs, with logged fallbacks, whatever is merged. Other workers read the questline only through the lead's
frozen accessor `quest.story.Turnings` (`stage`, `kind`, `done`).

**Days.** Every day in the record (`band_since`, `reoffer_day`, `turned_day`) and the robes' reissue counters is a
game-day index (`Clocks.day`: overworld game time / 24000), so the rules are pure and the tests pass explicit days. The
Standing panel shows the turned day counted from 1 (`turned_day + 1`, as its log does).

## T1. Classes (`com.oldrepublic.quest.turning` unless noted)

| Class | Role |
|---|---|
| `TurningFeature` | Initialisation (the lead calls it from `OldRepublicMod`): the item, the `rank_four_checklist` payload type, the dialogue hooks, the server events, the use callbacks, the Calling and the commands. |
| `TurningService` | The bodies of the frozen Milestone 5 `QuestHooks` (17.1), the slow tick (every 10 ticks per player, offset by entity id: the offer, the step under way, the bladeless mentors, rank 4), death, entity load, logout and server stop, deferred removals and walk-offs, shared helpers, and the seams for the commands and tests (`offer`, `beginConfrontation`, `confrontationRetryAt`, `MESSENGER_TAG`, `LANDED_TAG`). |
| `TurningRules` | Pure rules: who may be offered what and when (`candidate`, `inBand`, `track`, `offerDue`, `decline`, `finalRefusal`, `resting`), the task encodings, `onGreenwatchBay`, the costs (`rankAfter`, `refund`, `oldFactionEnd`, `newFactionEnd`, `signatures`, `rankGrants`), the rank 4 requirements (`Standing`, `Requirement`, `deeds`, `rankFour`, `rankFourBlocker`, `checklistVisible`), the Darth names and the reissue rule. Used by the tests. |
| `TurningBalance` | Balance section `turning` (T12). |
| `TurningOffers` | The offer (T3): the band's slow check, the messenger, `turn:accept` and `turn:decline`. |
| `FallTask`, `WatchHolocronItem`, `TurningItems` | "A Gift for the Empire" (T4): Ansa's and Kaddor's redirects, the holocron lent and delivered. |
| `ReturnTask` | "Amends" (T5): the detention post's scene, the escort, boarding, the Greenwatch landing. |
| `Confrontations` | "The Last Lesson" and "The Overseer's Due" (T6). |
| `Rites` | "Kneel" and "Welcomed" (T7). |
| `TurningCosts` | What turning costs (T8), in the order of 8.4. |
| `RankFour`, `RankFourChecklistPayload` | Rank 4 (T9): the requirements on the server, the checklist payload, the three ceremonies, the robes and their reissue. |
| `Mentors` | Uses of mentors, messengers and the post's people (`UseEntityCallback`), the Compass's pedestal (`UseBlockCallback`), the other-path talk (`StoryMentors.setOtherPathTalk`), the mentors kept bladeless. |
| `TurningDialogue` | The conditions, actions and redirects (T10). |
| `TurningGuidance` | The journal's Calling during a task. |
| `TurningCommands` | `/oldrepublic turning ...` (T13). |
| `TurningEffects` | The `turning` and `rank_four` effect ids and variants (T11). |
| client `quest.turning.TurningClient` | The payload receiver, the effect handlers, the tick and the disconnect reset (the lead calls it from `OldRepublicModClient`). |
| client `quest.turning.RankFourClient` | The last checklist received, and the lines W4's Standing panel draws: `line`, `header`, `ready`, `deedsHint`, `turnedLine`, `darthTitle`. |
| client `quest.turning.TurningFx` | The rites' and ceremonies' looks, as timed beats run from the client tick. |

## T2. Saved state (14.1)

`StoryData.turning` (the lead's `Turning` record, `optionalFieldOf("turning")`; every field optional, so a save without
it loads as `NEW`):

| Field | Meaning |
|---|---|
| `kind` | `TurningKind` ordinal: `NONE`, `FALL`, `RETURN` (the questline under way, or the last one finished). |
| `stage` | `TurningStage` ordinal: `NONE`, `OFFERED`, `TASK`, `CONFRONTATION`, `RITE`, `COMPLETE`. |
| `band_since` | The day the band was first seen this time, -1 when out of it. |
| `refusals` | Refusals while the band held (3 ends the offers until the band is left). |
| `reoffer_day` | The day a declined offer may come back, -1 for none. |
| `task` | The step's progress (below). |
| `turned_day` | The day of the last rite, -1 if never. |

`task` by step:

| Step | Values |
|---|---|
| Fall `TASK` | 0 `FALL_TO_ANSA`, 1 `FALL_CARRYING` |
| Return `TASK` | bits 0 to 3 the phase (0 `RETURN_HELD`, 1 `RETURN_ESCORT`, 2 `RETURN_ABOARD`); bits 4 to 7 the guards down (bits 0 and 1 the two security droids, 2 and 3 the two troopers); bits 8 to 10 the prisoners aboard |
| `CONFRONTATION` | 0 `CONFRONT_WAITING`, 1 `CONFRONT_DUEL`, 2 `CONFRONT_YIELDED`, 3 `CONFRONT_TALKED` (Peaceful) |
| `RITE` | 0 `RITE_WAITING`, 1 `RITE_HERE` |

Story flags: `turn.crystal_taken` (Saelen's crystal taken in a Fall), `turn.blade_broken` (Sorn's blade broken in a
Return); each is removed at the rite to which that mentor comes with a new blade. The rite also moves the lead's
`c3.rank3_done` to `c3.rank3_paid`. Story counters: `rank4.reissue.<item>`, the day the robe was last given.

Identity choices: `turn.fall.done`, `turn.return.done` (the lead's `Turnings`, recorded at the rite), `rank4.done`,
`rank4.paid` (rank 4 held on an earlier path: no SP or XP again), `rank4.darth.<n>` (the Darth name, 1 to 6, removed by a
rite) and `title.wayfarer` (W6's `SkillService.WAYFARER_TITLE`).

In memory only (restarted when needed, never saved): the messenger, the detention post's people, the confrontation's
retry time and running duel, the redirect bypass, the Compass position of the ceremony, the checklist last sent. People
of the turning saved with their chunks (a messenger, the post's people, a landed prisoner) and a dropped holocron are
removed when they load.

## T3. The offer (8.2)

1. **Candidate.** A Jedi whose Chapter Four is `COMPLETE` and who has not Fallen is a candidate for the Fall; a Sith who
   has not Returned, for the Return. The identity and story paths must agree (an operator's forced change still under way
   gets nothing); Independents get nothing. With `enabled` false there is no candidate: a pending offer is withdrawn, a
   questline under way continues.
2. **Band.** On each slow tick between questlines (stage `NONE` or `COMPLETE`, or `OFFERED`): in the band (alignment at
   most `fallAlignment`, or at least `returnAlignment`) the first day seen is kept; out of it, or with no candidate, the
   band's day, the refusals and the re-offer day are forgotten, and a pending offer is withdrawn (its dialogue closes,
   the messenger is removed, the record goes back to where it rested).
3. **Due.** `bandDays` whole days in the band, fewer than `refusals` refusals, past any re-offer day, at a safe moment
   (`StoryMentors.safeMoment`: the home world, no recent hurt, no hostile near, no open dialogue, not riding a ship) and
   not in a spar: the record goes to `OFFERED` and a messenger comes.
4. **The messenger** (in memory, one per player): for the Fall an Imperial acolyte (W4's `acolyte` hub person, one of four
   faces), for the Return a Watch apprentice (`apprentice`, one of three), spawned on free ground 5 to 7 blocks from the
   player on the home world, owned by them and tagged `oldrepublic.turning.messenger`. It walks to the player and makes
   the offer (`turn_fall_offer`, `turn_return_offer`; effect `turning` 0, the offer sting) within 3.5 blocks, or after
   300 ticks wherever it is, when no dialogue is open. Using the player's own messenger reopens the offer; someone
   else's says "not for you". Unanswered, it leaves after 6,000 ticks, when the player is 48 blocks away or leaves the
   home world, and another comes 2,400 ticks later at a safe moment. Logging out removes it; the offer stands.
5. **Accept** (`turn:accept`): `TASK`, entry `the_fall_offered` or `the_return_offered`, a line naming the first step;
   the messenger walks off and is removed after 80 ticks.
6. **Decline** (`turn:decline`): one more refusal, the offer back `reofferDays` later while the band holds; the record
   rests where it was. The third refusal says so ("the offers end"); they start again only after the band is left and
   reached again.

## T4. The Fall's task: "A Gift for the Empire" (8.3)

- While the Fall is at `TASK`, W4's `c4_ansa` and `c4_ansa_meet` open `turn_ansa`, and `c4_kaddor` and `c4_kaddor_meet`
  open `turn_kaddor` (`DialogueRedirects`). Their option "Something else" (`turn:other`) opens the dialogue the player
  asked for, once, unchanged.
- **Ansa** (`turn_ansa`, `turn:holocron`): on Selvaren, with a free inventory slot, she lends the `watch_holocron`; the
  task becomes `FALL_CARRYING` first, so the holocron's own check never removes the copy just given. With no room: a
  hint, and the task stays at Ansa.
- **The holocron** (`WatchHolocronItem`, a keepsake, one per stack, fire resistant): it exists only in the inventory of a
  player whose Fall is carrying it. A copy anywhere else is gone within a second (checked every 20 ticks); one dropped,
  spilled by a death or saved with a chunk is removed on the next tick. When the slow tick finds it no longer carried
  (dropped, stored, lost) or the player dies, the step starts again at Ansa: every copy goes (inventory and ender
  chest), the task returns to `FALL_TO_ANSA`, Ansa's line "takes it back".
- **Kaddor** (`turn_kaddor`, condition `turn:holocron`, action `turn:deliver`): he takes the holocron. `taskXp`, Sith
  Empire `taskRepGain`, Jedi Order `taskRepLoss` (one-shot source `turn.fall.task`), a major shift of `fallDark`, entry
  `a_gift_for_the_empire`; the stage moves to `CONFRONTATION` and Saelen sets out (T6).
- The Calling points to the Greenwatch (Ansa) and then to the Proving (Kaddor).

## T5. The Return's task: "Amends" (8.3)

1. **The post.** W3's `detention_post` site on Varkhast (`PlanetSites.ensure`, 120 to 200 blocks from the Proving). While
   its template is missing, the middle of its ring at the surface stands in, with a plain layout. The Calling points to
   the post (or its ring) while the prisoners are held.
2. **The scene** (in memory): when the player is on Varkhast within 48 blocks (horizontally) of the post, with its chunk
   loaded, the post's people appear at the template's anchors (`WARDEN`, `PRISONER_1` to `3`, `GUARD_1` to `4`): four
   guards unless Peaceful (two security droids, `SpawnerGroups.SECURITY_DROIDS` one at each droid anchor, and two Imperial
   riflemen from `Troopers.spawnSquad`), the warden (a hub host, variant `warden`) and three Republic prisoners (hub
   people, variant `republic_prisoner`, named by lang: Lieutenant Aven Holloway, Medic Reth Pallan, Surveyor Idris
   Vantor). Guards already down stay down; a guard that cannot be made (its feature missing) counts as down. Beyond 96
   blocks, off Varkhast, on logout or on a change of difficulty to or from Peaceful the scene is removed; it comes again
   on return.
3. **The fight.** The troopers stand calm until the owner strikes a guard of the post; from that blow on they fight the
   owner as an incident (`IncidentMode.ARMY_AND_OWNER`), so the fight is hostile (the M3 hostile-kill rules: the
   alignment loss inside its cap, kill XP). Each guard's death sets its bit; when all four are down the cells open. The
   prisoners and the warden cannot be hurt (but by `/kill` and the void).
4. **The warden** (`turn_warden`): with the prisoners held, a player who knows Mind Trick may trick him (`turn:free
   trick`, which re-checks W6's `ability:knows` and runs W6's `mind_trick:use`) and walk them out past the calm troopers;
   on Peaceful there are no guards and he opens the cells after a talk (`turn:free talk`).
5. **The escort** (W4's hub people's escort runtime, `HubNpc.setEscortOwner`): the prisoners follow the player. One more
   than 20 blocks away falls in beside them; one who is missing (unloaded, removed) comes again beside them. Beyond 96
   blocks of the post the guards and the warden stay behind. Leaving Varkhast before all three are aboard restarts the
   step. The Calling points to the player's ship.
6. **Boarding.** With the player's ship landed on the same level (`READY` or `GROUNDED`), a prisoner within 3 blocks of
   the ramp (the ship's dismount spot) or 5 blocks (horizontally, and within 4 vertically) of the hull boards: removed,
   their bit recorded in `task`, a line. All three aboard: `RETURN_ABOARD`, the scene is removed. The Calling points to
   the Greenwatch.
7. **Aboard.** No ship record, a `DISABLED` or `HULK` ship, or a recall (the ship seen flying without its owner aboard on
   two slow looks in a row) sends them back to their cells: the step restarts.
8. **The Greenwatch** (`QuestHooks.onPlanetArrived`, any flight or the autopilot): a touchdown on Selvaren within 8
   blocks (horizontally) of one of the hub's pad bays (`PlanetHubs.padBays`; with none known, within 48 of the hub; with
   no hub, anywhere) puts the three down beside the ramp. They thank the player and walk off (removed after 200 ticks).
   Elsewhere on Selvaren a line says to land at the Greenwatch. Then `taskXp`, Jedi Order `taskRepGain`, Sith Empire
   `taskRepLoss` (one-shot source `turn.return.task`), a major shift of `returnLight`, entry `amends`, and Sorn sets out
   (T6).
9. **Restart** (the player dies, leaves Varkhast mid-escort, or the ship is disabled or recalled): the scene goes, the
   task returns to `RETURN_HELD` with no guard down (all four stand again), a line says so.

## T6. The confrontations: "The Last Lesson" and "The Overseer's Due" (8.3)

- After the task the old mentor (Saelen after a Fall, Sorn after a Return) sets out: `StoryMentors.comeTo(p, role, false)`
  (after `story.mentorSummonTicks`, at the next safe moment on the home world; the camp stays where it is). The slow
  tick sends for them again while the confrontation waits (a restart or a logout drops the call).
- **Arrival**: a line, then the duel through W8's `MentorDuel.start` (`SAELEN` or `SORN`: full strength, real damage,
  the yield at 10%), the mentor's blade lit. On Peaceful the confrontation is a talk: `CONFRONT_TALKED` and the choice at
  once (the M4 rule). A duel lost to a server restart (`CONFRONT_DUEL` with no duel running) waits for the mentor again.
- **The yield** (`MentorDuel.Result.YIELDED`): `CONFRONT_YIELDED`, a line, the blade off, the choice
  (`turn_last_lesson` or `turn_overseers_due`). Using the old mentor reopens it; a mentor farther than 32 blocks (or
  unloaded) is sent for again and reopens it on arrival.
- **A death** during the confrontation (or the duel's reset): back to `CONFRONT_WAITING`, the mentor's blade off and the
  mentor walks home (anchored to the camp with `story.mentorLeash`); they come again after `confrontationResetTicks`.
  Nothing is lost; a choice already won stays won.
- **The choice** (`turn:resolve release|trophy`): let the mentor go, or take the trophy. The Fall's trophy is Saelen's
  crystal: a major shift of `crystalDark` and a green kyber crystal attuned to the player, named "Saelen's Crystal"
  (`SaberAssembly.crystalStack`, cosmetic hue), flag `turn.crystal_taken`. The Return's is Sorn's blade: a major shift of
  `bladeLight`, his saber gone, flag `turn.blade_broken`. Either way `confrontationXp`, entry `the_last_lesson` or
  `the_overseers_due`, the mentor's line, the mentor walks back to the camp, and the new mentor sets out for the rite.
- **Bladeless mentors**: while a trophy flag is set, the slow tick empties that mentor's hand whenever their own setup
  rebuilds the saber (never while sparring), until the rite they come to with a new blade.

## T7. The rites: "Kneel" and "Welcomed" (8.3)

- The new mentor (Sorn after a Fall, Saelen after a Return) sets out with a new camp: `StoryMentors.comeTo(p, role,
  true)`; the camp placed on the arrival spot becomes the player's camp. The slow tick sends for them again while the rite
  waits, and when the player is within 24 blocks of the camp and the mentor is not loaded.
- **Arrival**: the mentor's blade is rebuilt (a mentor whose blade was taken in an earlier turning comes with a new one:
  `turn_kneel`'s `new_blade` node, `turn_welcomed`'s `new_crystal`), `RITE_HERE`, a line, and the rite's dialogue. Until
  the rite this is the other path's mentor: using them reopens the rite through `StoryMentors.setOtherPathTalk`.
- **The rite** (`turn:rite`): the costs (T8); the choice `turn.fall.done` or `turn.return.done`; the record rests at
  `COMPLETE` with `turned_day` (the band, refusals and re-offer day cleared for the other turning); the trophy flag of
  the mentor who came back is removed; `riteXp`, Resonance `the_fall` or `the_return` +`riteResonance` (one-time
  milestones), entry `kneel` or `welcomed`, banner `lesson_complete` 12 (W4's `turning` banner in the new path's colours,
  with `story.turning.rite`), effect `turning` 1 or 2, and a line naming the new path (and the refund).

## T8. What turning costs (8.4)

`TurningCosts.apply`, at the rite, in this order:

1. Both orders' reputation and the rank are read first.
2. The path: the story's (`StoryData.path`) first, then `Identity.setPath(p, to, true)`, so `StoryService.onPathChanged`
   sees the story already on the new path and restarts nothing; `setPath` makes the M2 join changes once.
3. The rank: `Identity.setRank(p, min(max(rank, 1), rankCap))`. A Knight or a Master who turns is an Apprentice.
4. The ceremonies already paid: `c3.rank3_done` becomes `c3.rank3_paid` and `rank4.done` becomes `rank4.paid`, so the new
   path's Knighting and Naming open again without their SP and XP; the Darth name choices are removed.
5. The powers: the old path's two signature powers (`force_heal`, `force_barrier`; or `force_lightning`, `force_choke`)
   and its rank grants (`knights_vigil`, `force_valor`; or `lords_dominion`, `force_rage`) are revoked
   (`SkillService.revoke`). Every node of the old path's tree the player bought is revoked, and half the points spent on
   them all together, rounded down (`refundPct`), come back (`Progression.grantSkillPoints`). The abilities of nodes still
   owned are known again (Lift is taught by more than one tree). The new path's signature powers are granted
   (`Skills.grant`, never refunded). The old tree's other granted nodes (the holocron passives) stay, gated by alignment as
   before.
6. The slots: `SkillService.refresh` (slots 7 and 8 close with rank 4; their powers go back to the list).
7. The orders: the old one ends at `min(before, oldFactionCap)` (Hostile), the new one at `max(before, 0) +
   newFactionBonus`, each as one `Identity.changeReputation(p, f, end - now, "turn.fall" or "turn.return", false)`, so
   the toast shows the net change and the join bonus is not added twice.
8. The mentors: the new mentor's trust becomes `newTrust` and the old one's 0 (one `addTrust` of the difference each).
   The old mentor stays at the old camp, cold: the lead's `StoryMentors` plays `turning.oldrepublic.cold.<role>` instead
   of `not_now`, with no sessions or lessons.

Rank 3 and 4 robes of the old path keep their armour; their set bonuses already fail on alignment (`SetBonusRules`).
Chapter Two's path stage stays `COMPLETE`; Chapters Three to Five keep their records.

## T9. Rank 4: Master, Darth and Wayfarer (8.5)

**Requirements** (`TurningRules.rankFour`, in the order the dialogue names a failure; each `Requirement(id, met, have,
need)`, lang `turning.oldrepublic.rank4.req.<id>` with the need and what the player has):

| Id | Master (Jedi) / Darth (Sith) | Wayfarer (Independent) |
|---|---|---|
| `story` | Chapter Five `COMPLETE` | the same |
| `level` | Force level `rank4Level` | the same |
| `rank` / `title` | rank 3 on the current path | the Pathfinder title (M3) |
| `trust` | the path's mentor at `rank4Trust` | none |
| `reputation` / `factions` | own order at `rank4Rep` | `wayfarerFactions` known factions at `wayfarerTier` (Trusted) or better |
| `alignment` | `rank4Alignment` toward the path (+50 Jedi, -50 Sith) | none |
| `mastery` | `rank4MasteryPowers` powers at mastery rank `rank4MasteryRank` | the same |
| `deeds` | any `rank4Deeds` of: the Long Hymn cleared; Ulvaine's echo defeated `deedEchoes` times; `deedBounties` space bounties; `deedContracts` contracts on Ghessar or Tyrvann; Ulvaine's writ held (Sith only), read from W4's `ChapterFive.deeds` | the same, without the writ |

`RankFour.blocker` gives the first unmet requirement as `key|need|have`, `req.done` for a holder of rank 4,
`req.turning` halfway between two paths (`TASK` to `RITE`: nobody names a Master or a Darth then), `req.no_path` without
a path. The `rank4:ready` condition, the ceremony's re-check and the checklist all read it, so they always agree.

**The checklist** (`rank_four_checklist`: `visible`, `kind` 0 Master / 1 Darth / 2 Wayfarer, `done`, the entries, at most
12): visible from Chapter Five's `AFTERMATH` for a Jedi, a Sith or an Independent. It is looked at every 40 ticks, and on
the next slow tick after a hook that may change it (a space objective, a raid clear, a foe killed, an accepted offer, a
rite, a ceremony), and sent only when it changed (never a hidden one to a client that has not had one). W4's Standing
panel draws it with `RankFourClient` (header, a line per requirement with its tick, the "ready" line naming who holds the
ceremony, the deeds hint), the "Turned on day N" line and a Darth's chosen name.

**The ceremonies** (each re-checks every requirement and names the first unmet one in chat):

| | Master | Darth | Wayfarer |
|---|---|---|---|
| Where | using Saelen when ready: `rank4_saelen` (`letter`, then `master`, action `rank4:ceremony`) | using Sorn when ready: `rank4_sorn` (`writ`, then `names` and `names_more`, the six Darth names, then `confirm_<n>`, action `rank4:ceremony <n>`) | using the Compass's pedestal at Courier's Fall (Chapter Three's `fall`, within 1 block of it) when ready: the M3 Pathfinder look (`rank_ceremony`, `RANK_PATHFINDER`) and Tessaly Arn's echo, `rank4_compass` (`echo`, then `mantle`, action `rank4:wayfarer`, which needs the player within 8 blocks of the pedestal in the overworld) |
| Rank | `Identity.promote(p, 4)` | the same, and the choice `rank4.darth.<n>` | the title `title.wayfarer` (rank stays 0) |
| Power | `force_valor` | `force_rage` | `wayfarers_stride` (Wayfarer's Grace) |
| Robe | `jedi_master_robe` | `sith_darth_robe` | `wayfarer_mantle` |
| Other | `rank4.done`; `ceremonySp` and `ceremonyXp` unless `rank4.paid`; Resonance `third_rank` +`ceremonyResonance`; slots 7 and 8 open (`SkillService.refresh`, W6's rule); entry `master`; banner `lesson_complete` 13; effect `rank_four` 0 facing from the mentor to the player; Saelen's closing line | the same; entry `darth`; effect `rank_four` 1; Sorn's line with the name | the same; entry `wayfarer`; effect `rank_four` 2 at the pedestal's top, toward the player; a line naming the title |

The mentors' dialogues also offer "Something else first." (`rank4:mentor`, the mentor's usual dialogue) and "Not
yet.". A player not yet ready who uses their mentor gets the mentor's usual dialogue; `rank4_saelen` and `rank4_sorn`
opened another way show `not_ready`. The robes are W7's items, looked up by id; a missing one is logged and the ceremony
goes on without it.

**Robes again** (`RankFour.reissue`): a holder of rank 4 who neither carries, wears nor keeps their robe in the ender chest
gets it again, at most once per game day (`rank4.reissue.<item>`): using their own mentor (Master, Darth) or the Compass's
pedestal (Wayfarer).

## T10. Dialogues (6.11)

`turn_fall_offer`, `turn_return_offer` (the messenger; `offer`, `why`, `accept_confirm`, `decline_confirm`, `gone`),
`turn_ansa`, `turn_kaddor` (the redirects), `turn_warden`, `turn_last_lesson`, `turn_overseers_due` (`yielded`, `talk`,
`trophy_confirm`, `later`), `turn_kneel`, `turn_welcomed` (`kneel`/`welcome`, `new_blade`/`new_crystal`, `cost`, `done`,
`later`), `rank4_saelen`, `rank4_sorn`, `rank4_compass`. Every node is reached from its `start` by a condition; an
opening that no longer applies shows a closing node (`gone`, `later`, `empty`, `quiet`, `not_ready`).

Conditions (W5): `turn:offered fall|return`, `turn:stage <stage>[:<task>]` (for the Return's task the phase),
`turn:holocron`, `turn:held`, `rank4:ready`. Actions (W5): `turn:accept`, `turn:decline`, `turn:holocron`,
`turn:deliver`, `turn:other`, `turn:free trick|talk`, `turn:resolve release|trophy`, `turn:rite`, `rank4:ceremony`,
`rank4:ceremony <1-6>`, `rank4:wayfarer`, `rank4:mentor`. Each action re-checks the record on the server, so a stale
dialogue changes nothing. Used as their owners define them: `planet:in`, `story:flag`, `c4:peaceful`, and W6's
`ability:knows` and `mind_trick:use`. Redirects: `c4_ansa`, `c4_ansa_meet`, `c4_kaddor`, `c4_kaddor_meet` (T4).

## T11. Effects and sounds (15.1, 15.3, 15.4)

| Event | Sent | Look | Sound |
|---|---|---|---|
| `turning` 0 (offer) | `toPlayer` when the messenger makes the offer | none beyond the messenger | `story.turning.offer` (UI) |
| `turning` 1 (the Fall's rite) | `toPlayer` at `turn:rite` | a cold wind through the camp lifts grey grit, the fire smothered (30 `gather_dust` over 40 ticks) | the banner's `story.turning.rite` |
| `turning` 2 (the Return's rite) | the same | the dust in the air settles, warm motes rise from the fire (30 `gather_dust` over 40 ticks) | the same |
| `rank_four` 0 (Master) | `around`, entity = the player, dir = mentor to player | the light of Saelen's blade across the open hands, pale motes falling (20 `crystal_glint`) | `story.rank_four` (the banner for the player, at the ceremony for others) |
| `rank_four` 1 (Darth) | the same | Sorn's writ burns from its edges, its ash lifts (12 `flare_ember`, 16 `tomb_dust`) | the same |
| `rank_four` 2 (Wayfarer) | `around` at the pedestal's top, dir = toward the player | Tessaly's figure folds into the Compass, forms again a step aside, and road dust lifts beyond the pedestal (16 `gather_dust`) | the same, and the M3 `holocron.gatekeeper.form` as she forms |
| `lesson_complete` 12, 13 | `toPlayer`, intensity = the new path | W4's banners `turning`, `rank_four` | `story.turning.rite`, `story.rank_four` |

The phase-by-phase treatment, colours and timings are in `docs/assets/turning.md` (A1). Nothing draws a ring, sigil,
halo, beam or glow outline on the ground or the player, and nothing flashes or shakes the camera.

## T12. Balance (`turning`, 16.4)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | true | False: no offers (a questline under way continues) |
| `fallAlignment`, `returnAlignment` | -50, 50 | The bands |
| `bandDays`, `reofferDays`, `refusals` | 1, 3, 3 | Days in the band; days after a refusal; the refusal that ends the offers |
| `taskXp`, `taskRepGain`, `taskRepLoss` | 1500, 100, -150 | The tasks: XP, the new order, the old order |
| `fallDark`, `returnLight` | -5, 5 | The tasks' alignment shifts |
| `confrontationXp`, `crystalDark`, `bladeLight` | 1500, -8, 3 | The confrontations and their trophies |
| `confrontationResetTicks` | 1200 | The old mentor's wait after a death |
| `riteXp`, `riteResonance` | 1000, 300 | The rites |
| `newTrust`, `refundPct`, `rankCap` | 40, 50, 2 | The new mentor's trust; the refund of the old tree; the rank after a rite |
| `oldFactionCap`, `newFactionBonus` | -500, 150 | The orders after a rite |
| `rank4Level`, `rank4Trust`, `rank4Rep`, `rank4Alignment` | 20, 90, 800, 50 | Rank 4's level, trust, reputation, alignment lean |
| `rank4MasteryRank`, `rank4MasteryPowers`, `rank4Deeds` | 3, 2, 2 | Rank 4's mastery and deeds |
| `deedEchoes`, `deedBounties`, `deedContracts` | 3, 5, 5 | What makes a deed count |
| `wayfarerFactions`, `wayfarerTier` | 3, 100 | The Wayfarer's factions at Trusted |
| `ceremonyXp`, `ceremonySp`, `ceremonyResonance` | 1500, 2, 900 | The ceremony (XP and SP once per character) |

The mentors' coming uses the lead's `story.mentorSummonTicks` (1200) and `story.mentorLeash`.

## T13. Commands (operators, every use logged by the command system)

`/oldrepublic turning status|offer|advance|rank4|ceremony|reset <player>` (`TurningCommands`):

- `status`: the record, the band's day, the refusals, the re-offer and turned days (with today), the confrontation's retry
  time and rank 4's first unmet requirement.
- `offer`: the offer now, whatever the band (a Jedi the Fall, a Sith the Return; refused mid-questline).
- `advance`: finishes the step under way: accepts the offer; completes the task (the Fall's delivery, the Return's
  landing); wins the confrontation by letting the mentor go (calling off a mentor on the way); holds the rite.
- `rank4`: the checklist as the player would see it.
- `ceremony`: holds rank 4's ceremony now if every requirement is met (a Darth takes the first name; a Wayfarer's effect
  plays around the player).
- `reset`: forgets a questline under way, the messenger, the post's scene, the holocron and the taken blades; the identity
  choices (`turn.*.done`, `rank4.*`) stay.

## T14. Edge cases (14.2, 14.3)

- **Once each**: a player can Fall once and Return once, in either order (`turn.fall.done`, `turn.return.done`); after
  both, the record rests at the last `COMPLETE` and no candidate remains. A Jedi who Fell and Returned is a Jedi for good.
- **Death**: the Fall's task restarts at Ansa, the Return's at the cells; a confrontation resets (T6); a rite waits.
- **Logout, restart**: the messenger, the post's scene, a coming mentor and a running duel live in memory; the record
  keeps the step, and the slow tick brings each back (a messenger at the next safe moment, the scene on approach with the
  guards already down, the mentor sent for again, a lost duel waits for the mentor). A carried holocron stays carried.
- **Dimensions**: a messenger leaves when the player leaves the home world; the post's scene goes off Varkhast; leaving
  Varkhast mid-escort restarts the step; the mentors come only on the home world.
- **Peaceful**: the confrontations are talks; the post has no guards and the warden talks; nothing else changes.
- **Multiplayer**: everything is per player. Messengers, the post's people, the mentors and the landed prisoners are
  owned; another player's say "not for you" or "not yours". Anyone's kill of a guard counts for its owner; only the
  owner's blow turns the troopers. The `rank_four` effect is sent around, so others nearby see and hear the ceremony.
- **Dedicated server**: nothing in `quest.turning` touches a client class; the client code is in `client/quest/turning`.
- **Old saves**: a save without `turning` loads as `NEW`; a Milestone 4 save at Chapter Four `COMPLETE` starts the band's
  check on its first slow tick.

## T15. Tests (19)

`TurningGameTests` (19.1, 18 methods, each synchronous with `maxTicks` 20, explicit days, mock players removed in
`finally`):
`offerAfterOneDayInTheBand`, `reofferAfterThreeDaysAndStopAfterThird`, `fallOnceReturnOnce`,
`deliveryRestartsWhenStruckDown`, `confrontationResetsAfterDeath`, `peacefulConfrontationIsATalk`,
`returnEscortLandsPrisonersAtGreenwatch`, `riteChangesPathWithoutRestartingLessons`, `rankCappedAtTwo`,
`oldSignaturesRemovedNewGranted`, `boughtNodesHalfRefunded`, `reputationAndTrustAfterTurning`, `coldMentorLine`,
`rankFourChecklistRules`, `ceremonyGrantsPowerSlotsRobeAndPoints`, `wayfarerTitleAndPassive`, `darthNameRecorded`,
`robesReissuedOnceADay`.

Client steps (`m5/TurningClientSteps`, run by the lead's `M5ClientTest`, band 155 to 164, every shortcut logged):
155 `fall_offer`, 156 `last_lesson`, 157 `kneel`, 158 `rite`, 159 `master`, 160 `darth`, 161 `wayfarer`, 162
`robe_master`, 163 `robe_darth`, 164 `robe_wayfarer`. Real inputs: right-clicks on the mentors and the Compass's
pedestal, number keys for the dialogue choices; the state before each scene is set directly, and everything is restored
at the end.

## T16. Decisions

- The messenger and the detention post's people are not saved: they come again from the saved record, so a crash or a
  restart can never leave a stray quest person who means nothing.
- The post's troopers start calm and turn on the owner (`ARMY_AND_OWNER`) at the owner's first blow on a guard, so the
  Mind Trick route can walk the prisoners out past them while a fight still counts as hostile.
- Prisoners board by being removed and recorded (`task` bits 8 to 10), not as riders, and step off as new hub people at
  the Greenwatch; a ship lost, disabled or recalled first restarts the step.
- The Wayfarer's ceremony starts from a use of the Compass's pedestal (Chapter Three's `fall`), where the M3 Pathfinder
  figure already appears; the robes' reissue for a Wayfarer happens there too.
- Rank 4 is refused halfway through a turning (`req.turning`); the checklist shows from Chapter Five's `AFTERMATH`.
- The Darth names are identity choices `rank4.darth.<n>`, removed by a later rite with the rank they named.
