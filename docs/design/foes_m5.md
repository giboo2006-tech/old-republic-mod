# Foes and bosses design, Milestone 5: five foes, the mentor duel, Darth Ulvaine, the Long Hymn and the boss music

Owner: W8 (foes and bosses). Packages `com.oldrepublic.enemy.{cartel,hask,hymn,scour,stormback}`,
`enemy/FoesM5Feature` and `com.oldrepublic.boss.{ulvaine,raid,mentor,cantor}` (common);
`com.oldrepublic.client.enemy.{cartel,hask,hymn,scour,stormback}`, `client/enemy/FoesM5Client`,
`com.oldrepublic.client.boss.{ulvaine,cantor,music}` (client); `mixin/boss/MusicManagerBossMusicMixin` and the Choke
mixins in `mixin/foes` (kept from M4); `saber/duel/DuelistProfiles` (the new profiles). Spec: `docs/MILESTONE_5.md`
section 12 (all of it), the `hologram` row of 15.3, W8's rows of 15.1 to 15.4, 16.3, 16.4 (`cartel_enforcer`,
`hask_warrior`, `hymnsworn`, `sand_scour`, `stormback`, `ulvaine`, `raid`, `mentor_duel`), 18 and 19 (`FoesM5GameTests`,
screenshots 185 to 199). Assets are in `docs/assets/foes_m5.md`. W3 owns the Hymnal and its anchors, the verse stones,
the hubs' spawner blocks and the weather; W4 owns Chapter Five, its rewards, the choice at the yield, the contracts and
the credit drops; W5 owns the turnings that call the mentor duel; W6 owns the powers the foes answer to.

Nothing in W8's scope was cut. API status: verified against the 26.2 dumps or used as existing mod code uses it,
except one line marked `// API-CHECK:` (section 15).

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Entities (9) | `cartel_enforcer`, `hask_warrior`, `hymnsworn`, `sand_scour`, `stormback`, `darth_ulvaine`, `hymn_resonator`, `first_cantor`, `cantor_choir` | `CartelEntities`, `HaskEntities`, `HymnEntities`, `ScourEntities`, `StormbackEntities`, `UlvaineEntities`, `CantorEntities` |
| Spawn eggs (5) | `{cartel_enforcer,hask_warrior,hymnsworn,sand_scour,stormback}_spawn_egg` | the entity classes; the bosses have none |
| Particles (2) | `sand_burst`, `spectral_wisp` | `ScourParticles`, `CantorParticles`; providers in the client |
| Damage types (2) | `storm_call`, `hymn` | `HymnDamage`, `data/oldrepublic/damage_type/` |
| Effect events (7) | `scour`, `hymnsworn`, `stormback`, `hask_warrior`, `ulvaine`, `first_cantor`, `hologram` | `ScourEffects`, `HymnEffects`, `StormbackEffects`, `HaskEffects`, `UlvaineEffects`, `CantorEffects` |
| Payload (S2C) | `boss_music(bossId, track)` | `boss.raid.BossMusicPayload` |
| Attachment | `oldrepublic:raid` (`RaidData`: `last_clear_week`, `pity`, `clears`, `echo_day`, `echoes`) | `RaidAttachments`; persistent, copied on death, synced to its player |
| Duellist profiles | `mentor_true`, `ulvaine`, `ulvaine_hymn` | `DuelistProfiles` |
| Spawner groups (5) | `cartel_enforcers`, `hask_warriors`, `hymnsworn`, `scour_brood`, `stormbacks` | `CartelEnforcers`, `HaskWarriors`, `Hymnsworns`, `Scours`, `Stormbacks` |
| Hub roles (2) | `enforcer` (Saltgate's wall posts), `warrior` (the holdfast's posts) | `CartelEnforcers`, `HaskWarriors` |
| Sounds (63) and music (2) | `cartel.*`, `hask.*`, `hymnsworn.*`, `scour.*`, `stormback.*`, `boss.ulvaine.*`, `boss.cantor.*`, `story.hologram`; `music.boss.{ulvaine,cantor}` | Phase 0 `ModSounds` names; `docs/assets/foes_m5.md` |
| Loot tables | `entities/{cartel_enforcer,hask_warrior,hymnsworn,sand_scour,stormback,darth_ulvaine,hymn_resonator,first_cantor,cantor_choir}`, `gameplay/long_hymn` | `data/oldrepublic/loot_table/` |
| Balance sections (8) | `cartel_enforcer`, `hask_warrior`, `hymnsworn`, `sand_scour`, `stormback`, `ulvaine`, `raid`, `mentor_duel` | the `*Balance` classes |
| Commands | `/oldrepublic ulvaine start\|echo\|yield\|reset <player>` and `ulvaine phase <player> <2\|3>`; `/oldrepublic raid start <player> [countdown]\|phase <player> <2\|3>\|verse <player> <stormwood\|mire\|basalt>\|silence <player> <1..3>\|clear\|wipe\|status\|unlock <player>` | `UlvaineCommands`, `CantorCommands` (operator level 2, the root's) |

Frozen and append-only: every effect variant number (section 11), `Hymnsworn.ACTION_*`, `SandScour.ACTION_*`,
`HaskWarrior.ACTION_*`, `Stormback.STATE_*`, `DarthUlvaine.Action`, the enforcers' and warriors' `Variant` (saved by
ordinal) and the `boss_music` ids (`collector`, `kaast`, `ulvaine`, `cantor`).

## 2. Shared wiring and helpers

`FoesM5Feature.init` (the lead calls it) registers the five entity types, their eggs, natural spawns, spawner groups
and hub roles, the attitude rules (`StandingRule` for the cartel and the clan, `StormbackRule`), the Force reactions,
then the bosses: the `boss_music` payload, `UlvaineFeature`, `RaidFeature` (the attachment, the First Cantor, the raid
command) and `MentorDuel.init`. `FoesM5Client.init` registers the renderers, layers and particle providers, starts the
effect handlers and loops, and registers every boss's loops with `BossMusicService` (the Collector's three, Kaast's
three, one each for Ulvaine and the First Cantor).

Shared server helpers (`enemy/hymn`, public so the bosses and W3 reuse them): `FoeSpawns` (the M1 crew pattern: member
`i` of `n` on a small ring, kit set before `finalizeSpawn`, never despawning, the owner's tag), `FoeTargets` (target
picking for the foes hostile to everyone; players skipped by game mode, never by `isCreative()`; a test's mock player
is creative in its game mode too, so the tests give the foes fake players), `FoeKills` (each foe's death reports once
to `QuestHooks.onFoeKilled` with its kind, faction and hostile snapshot), `TellLog` (each foe's own record of tells
and heavy hits, for the tests), `Interruptible` and `FoeReactions` (a stagger, Push or shock drops the tell in
progress), `Knock` (the Collector's knock-back rule), `StormCall` (the called strike: vanilla's cosmetic bolt, then
`storm_call` damage and a `shock`) and `HymnDamage`. The faction foes share `FactionFoe`, `StandingRule`,
`StandingState` and `StandingTargets` (`enemy/cartel`).

## 3. Cartel enforcer (12.1)

- Kits (`CartelEnforcer.Variant`, saved): **gunman** 26 health, armour 4; **bruiser** 34 / 6; **captain** 48 / 8; speed
  0.28, follow range 28. A Vessba kajidic humanoid in a sand-worn duster; the weapon is the item in hand (the M1 rifle, a
  vibrosword, a blaster pistol).
- `CartelCombatGoal`: the gunman holds 7 to 18 blocks and fires bursts of 2 bolts of 5 behind a 12-tick muzzle glow
  (synced, `CartelGlowLayer`), 40 ticks between bursts; the M1 `PoacherCoverGoal` takes it to cover under 60% health. The
  bruiser walks in and swings a two-blow string, each blow behind the M2 `npc_windup` (the full tell, then the glint
  only): a 7 cut, then a shove of 4 with knock-back 0.9. The captain shoots a pistol (5, a 10-tick tell), lobs a frag
  grenade every 200 ticks at 5 to 16 blocks behind the M3 `grenade_tell` (7 in radius 3), and once per 400 ticks calls
  every enforcer within 24 blocks onto its target (the `KAJIDIC` squad bark).
- Attitude follows the player's kajidic standing (`StandingRule`, the M3 trooper table): Hostile attacks on sight;
  Distrusted is Wary (warned within 8 blocks every 200 ticks, fought if they draw a weapon there after the warning);
  Neutral ignores; Trusted and Honoured are Allied. An enforcer the player struck first is provoked (it fights back,
  the fight never hostile); one that struck first engaged (hostile). Peaceful caps every answer at Neutral. A group
  spawned with an owner (the missions, the counting-house) stands against the owner's party.
- Mind-trickable (Phase 0 tag); can be frightened. Drops credits through W4's `CreditDrops` once W4 lists it (request,
  section 16); the loot table is empty by design. Never spawns naturally. XP keys 45 / 50 / 90 are in balance (see
  section 17 for the kill XP rule).

## 4. Clan Hask warrior (12.1)

- Kits: **rifle** 40 / 10, **blade** 40 / 10, **champion** 80 / 12 (a little taller); speed 0.27, follow range 32,
  knock-back resistance 0.3. It is the M4 `MandalorianHunter` underneath (jets, wrist rocket, saving, the hunters'
  client looks by event id), with its own `HaskCombatGoal`, allegiance `CLAN`, sounds and balance.
- Rifle: holds 8 to 18 blocks; bursts of 2 bolts of 6 behind the hunter's shouldered-rifle tell; a jet hop (the M4
  `hunter_tell` jet) every 200 ticks or when rushed; the wrist rocket (7 in radius 2.5) every 300 ticks behind the
  hunter's 20-tick red-dot tell. Blade: vibrosword strings of two blows (8 each) behind `npc_windup`; every 160 ticks at
  5 to 12 blocks the **jet-slam**: the pack whines and it crouches for 20 ticks (`hask_warrior` 0, a tell), then it jets
  onto the target and lands for 9 (`jet_slam`, `HunterDamage.JET_SLAM`) and knock-back 0.8 within 3 blocks; a Push in
  the air knocks it down and the slam never lands. Champion: strings of 9 behind a clan-iron shield that turns bolts
  within 70 degrees of its front (`hask_warrior` 2), and every 160 ticks the **shield bash**: the shield drawn back for 15
  ticks (`hask_warrior` 3, a tell), then 6, knock-back 1.0 and `stagger` for 20 ticks.
- Attitude follows clan standing, as the cartel's. A group spawned with an owner (the clan's way, 6.3) fights beside
  the owner's party whatever the standing. Fearless and strong-willed (Phase 0 tags). Drops `clan_iron` on a 10% roll
  when a player brought it down, and credits through `CreditDrops` once listed; it inherits the hunter's anti-farm loot
  rule. Never yields, never spawns naturally.

## 5. Hymnsworn (12.1)

- Kinds: **chanter** 24, **blade** 28, **storm-caller** 36, no armour; speed 0.27. Allegiance `HYMN`, hostile to every
  player, never natural, never in Peaceful; force-resistant, not mind-trickable.
- Chanter: keeps about 6 blocks back and chants (synced action), healing every Hymnsworn within 8 blocks by 1 each 20
  ticks, and nothing else; staff blows of 3 at anyone who closes in. Blade: fast vibroblade cuts, 5 every 14 ticks
  (light blows, no tell). Storm-caller: keeps about 9 back; every 160 ticks it **calls lightning** onto the patch where
  its target stands (40-tick tell, `hymnsworn` 1; the patch does not follow the target), then 8 `storm_call` and
  `shock` 20 within 2.5 blocks; every 200 ticks a 20-tick **burst of Force lightning** within 12 blocks, 1.5 every 5
  ticks. A stagger, Push or shock drops the call's tell and ends a burst, as does losing sight for the burst.
- The raid's **shades** are blades drawn translucent, never saved, dropping nothing. Ulvaine's pylon chanters are
  summoned chanters, never saved.

## 6. Sand scour and matriarch (12.1)

- 30 health (matriarch 120 and 1.6 times the size, the scale attribute); speed 0.3. Never in Peaceful.
- **Burrowed** it is drawn as nothing and casts no shadow, has no collision, cannot be struck, shot or aimed at, and
  creeps toward the nearest player at speed 0.18; a faint ripple (`scour` 0) every 60 ticks and the `scour.hiss` loop
  within 6 blocks give it away (Force Sense marks it like any creature). A player within 4 blocks makes it **burst**:
  a 15-tick tell (`scour` 1, the sand boils), then out (`scour` 2) and a bite of 6 with Slowness II for 40 ticks if the
  target is near. It fights above ground for 120 ticks, then burrows (`scour` 3) for at least 30; it takes damage only
  above ground (`/kill` and the like still reach it). Hurt below 30% it dives and flees under the sand for 160 ticks.
- Matriarch: never flees; every 200 ticks a tail sweep (20-tick tell, `scour` 4: the tail rises and coils), then 8
  and knock-back 0.8 in a 4-block arc behind and beside her (`scour` 6); every 400 ticks she calls 2 brood up from the
  sand (`scour` 5), at most 6 living.
- Spawns burrowed (natural, groups and calls alike). Natural spawn: monsters in `ghessar_dunes` and `ghessar_canyons`
  (with the `is_ghessar` tag), any light, groups of 1 or 2, registered at weight 20 (`weight` 10 × `stormMultiplier`
  2) with the spawn rule turning away all but 1 natural attempt in 2 outside a sandstorm (`PlanetWeather`), so the storm
  doubles them. Drops leather 1–2 and bone 0–1 (looting adds).

## 7. Stormback (12.1)

- 80 health, armour 6, speed 0.24, knock-back resistance 0.6. A creature (category `CREATURE`, never in Peaceful): a
  neutral grazer of Tyrvann's basalt and stormwood.
- Provocation (`StormbackRule`): struck first by a player it turns on them, provoked (the fight never counts as
  hostile); crowded within 6 blocks for 60 ticks it turns on the crowder, engaged (a hostile fight). Anger lasts 600
  ticks. Creative and spectator players are never its quarrel.
- `StormbackChargeGoal` (the M4 hollowhorn pattern): from 4 to 14 blocks with a clear view it lowers its head and paws
  grit for 20 ticks (`stormback` 1, a tell), then charges in a line fixed at the tell's end at 0.55 a tick for up to
  14 blocks: the first body takes 10 (15 when charged, with a `shock`) and knock-back 1.6 (`stormback` 2); a wall stops
  it stunned for 40 ticks. Up close it butts for 5. Charges and stuns always run out.
- **Charged** when lightning strikes it (`thunderHit`: W3's ridge lightning seeks a stormback within 24 blocks of a
  player, 3.3): 400 ticks of crackling plates (`stormback` 0, synced), +50% damage, its charge shocks for 20 ticks;
  the lightning neither hurts nor burns it. It drops 1–2 `stormglass_shard` if it dies charged (`stormback` 4 when the
  charge runs out).
- Once per anger, below 50%, it rears and **roars** for 30 ticks (`stormback` 3, a tell), then a bolt comes down on it:
  it is charged at once and players within 3 blocks take 6 and `shock` 20. An interrupted roar comes again 60 ticks
  later. Natural spawn: weight 8, groups 1–2, in `tyrvann_basalt` and `tyrvann_stormwood`. Drops leather 2–4 (looting
  adds).

## 8. Spawns, groups and hubs

- Spawner groups (registered in `init`, `SpawnerGroups.register`): `hymnsworn` (verse stones with 3, 4 or 5 guards, a
  group of 5 with a storm-caller; shrines; the processional), `cartel_enforcers` (posts, the counting-house, contracts;
  with an owner, against the owner's party), `hask_warriors` (hunts, contracts, the duel of honour; with an owner, at
  the owner's side), `scour_brood` (contract culls; `bound` makes a matriarch lead it), `stormbacks` (the Hymnal's pit,
  `tyrvann_hunt`).
- Hub roles: `enforcer` and `warrior` stand on W3's `hub_spawner` posts as hub guards: attitude by standing, leashed 8
  blocks to the post, never despawning, never dropping anything (the M4 hub rule), and never starting a fight within 6
  blocks of a pad bay or the medbay.
- W4's code places the counting-house's guards through `CartelEnforcers.spawn`/`squad`; W3's Hymnal through
  `FoeSpawns.spawn`; the mentor duels, Ulvaine's chanters and the raid's shades through `Hymnsworns.summon`.

## 9. The mentor duel (12.1, 8.3)

`MentorDuel.start(ServerPlayer p, Entity mentor, Kind kind, Consumer<Result> onEnd)` (W5 calls it): the mentor fights
at full strength with profile `mentor_true` (9 a blow, guard 120, parry 0.35, strings of 3 to 5) and real damage both
ways, at 300 health (a transient `MAX_HEALTH` modifier, so a mentor saved mid-duel keeps its own), under a WHITE bar with
10 notches, leashed 24 blocks around where the duel began (past it the mentor walks back and fights again well inside
it). Saelen pushes (a 12-tick tell, 6 blocks, 4 damage) and raises a Barrier for 60 ticks that turns the player's blows;
Sorn calls Lightning (a 30-tick channel, 2.5 every 10 ticks, range 10) and chokes (a 15-tick tell, then the M3 `Holds`
for 40 ticks, 1 a second). A power comes every 160 ticks, the first after 100. The duel ends once: the blow that would
take the mentor under 10% is refused and it yields (`Result.YIELDED`); the player's death, or 200 ticks away (out of
the leash, another dimension, offline) reset it (`Result.RESET`). Everything changed on the mentor is restored and
`onEnd` runs on the next server tick (never inside `start`). The M2 spar events mark the start and end. No boss hook is
used (12.6).

Decision: `MentorEntity.duel(profile, health)` was not needed. The duel uses the mentor's existing `setProfile`,
`setSparPartner` and `setBladeLit` with the health modifier, and restores them.

## 10. Darth Ulvaine (12.2)

### 10.1 Entity and session

- `darth_ulvaine`: 0.6 × 1.95, a `SaberDuelist` and a `Yieldable` whose yield belongs to the session (`canYield` is
  false); armour 10, knock-back resistance 0.6, speed 0.32. Her saber is the item in hand (red, a duellist emitter), so
  the saber feature draws the blade, guards and strikes. Never saved: her fight respawns her.
- `UlvaineFight` keeps one `UlvaineSession` per owner, in memory. The owner entering the Heart at `THE_HYMNAL` starts
  it (checked each second; `HymnalSites` finds the halls piece and its anchors, `HeartLayout`); a yield still open
  after a restart brings her back kneeling. Participants: the owner and up to 3 helpers in the Heart at the start or
  entering in phase 1. A dead participant stays listed; one away alive, in another dimension or offline for 100 ticks
  leaves the bar. The fight resets after 200 ticks with no participant alive inside; a death alone never resets it.
- Health 600 × (1 + 0.6 × (n − 1)) for n ≤ 4, with the M4 cap trick (`KaastRules.HEALTH_CAP`, `damageScale`): past
  the 1024 attribute cap the attribute stops and each hit is scaled. A participant joining keeps her health's fraction.
  PURPLE bar, 10 notches. Damage gates (`hurtServer`): refused outside a running fight, in a transition, while she
  kneels and from non-participants; cut to 20% while a pylon chanter lives. The phase thresholds are floors
  (`setHealth`): no hit carries her past the next one, and reaching it is the transition, the yield or the echo's end.
- Music: `boss_music(ulvaine, 1)` to each participant on joining, 0 on leaving, the yield, the reset and the end.

### 10.2 Phases

- **The Duel** (100% to 65%): `UlvaineCombatGoal`, the M2 duellist with profile `ulvaine` (10 a blow, guard 140,
  parry 0.40, strings of 3 to 6, deflecting bolts), and three Force moves taken when she is neutral: the **Hymn step**
  every 300 ticks (15-tick tell `ulvaine` 1: she draws in on herself in a violet shimmer; then she reappears 6 blocks
  behind her target, on firm floor inside the Heart, `ulvaine` 2); **Force Lightning** within 12 blocks, at most every
  200 ticks (a 40-tick channel, 3 every 10 ticks, `ulvaine` 3; a Push, a stagger or losing sight ends it); **Force
  Push** every 240 ticks (12-tick tell, 6 blocks, 4 damage, strength 1.4).
- **The Storm** (65% to 30%): a 60-tick transition (`ulvaine` 0; she takes nothing), she raises her arms; the four
  rods charge in turn every 160 ticks: a 30-tick tell (`ulvaine` 4: the rod hums and static crawls over the patch by a
  player), then a strike there (`ulvaine` 5): 8 and `shock` 20 within 2.5 blocks. Two chanters appear at the pylons;
  while either lives she takes 20%. She keeps duelling between strikes.
- **The Hymn** (30% to 10%): she sings and the duel slows 20% (profile `ulvaine_hymn`). Every 160 ticks a **Hymn
  pulse** (`ulvaine` 6, a tell): an irregular wall of bent air and grit rolls out to 16 blocks over 40 ticks, 1.5
  blocks tall; jump it, parry it as it passes, or Push into it (the caster ignores that pulse), else 6 and Slowness I
  for 40 ticks. At 20% the **Crescendo** (`ulvaine` 7): four `hymn_resonator` spires (20 health, only participants can
  break them; `ulvaine` 8 as each lights or breaks) stand at the resonator anchors; if any stands after 400 ticks the
  hall takes 12 (a Barrier stops it; `ulvaine` 9) and all four relight; once all four are broken it never returns.
- **The yield** at 10% (`ulvaine` 10): she kneels, invulnerable; `onBossYielded(owner, "ulvaine")` once the owner is
  alive in the Heart (else when they come back, or when they use her). W4's `ulvaine:resolve` calls
  `UlvaineFight.resolve(owner, outcome)`: every participant's `onBossDefeated` (the owner's the first clear), then the
  scene: spared, she rises and walks out; bound, she is led off with her wrists bound; executed, a short end. A
  resolve with no fight running still reports the owner's defeat.
- Counterplay: Stasis holds her at most 20 ticks (she breaks it herself, `stasisMaxTicks`); Choke at most 20 ticks and
  no Lift (`UlvaineReactions`); Mind Trick fails (not mind-trickable); Drain works. Fear immunity and the push
  resistance come from the tags `#c:bosses` and `#oldrepublic:push_resistant` (lead's files, requested, section 16).

### 10.3 The echo

After the owner's Chapter Five is `COMPLETE`, using the vanilla lectern at the Heart's `LECTERN` anchor (within 2
blocks) calls her echo: the same three phases drawn translucent pale violet, gathering out of the air for 40 ticks,
no yield (she fades at her last floor, `ulvaine` 11). Once a day per player (`RaidData.echo_day`): 800 XP and a 25%
hymnstone shard (seeded per player and day), `echoes` + 1 for the rank 4 deed, `onBossDefeated(..., ECHO, ...)` for
each participant; later the same day it still fights but pays nothing. Peaceful: no echo. The echo uses the fight's
session rules.

## 11. The Long Hymn (12.3, 12.4)

### 11.1 Start, participants and the session

- `LongHymn` keeps one `CantorSession` per undercroft, in memory. A player uses the keystone (`KEYSTONE`): never on
  Peaceful, one Hymn per undercroft, and only when the player or someone playing inside has finished Chapter Five. A
  200-tick countdown (chat), then everyone playing inside the box (`UNDERCROFT_MIN`/`MAX`) is listed, and anyone who
  comes in during the first 600 ticks joins, up to 8. The countdown speaks to and waits for the same players the
  listing takes, creative ones included; with nobody left inside it stops and the engine settles again. W3's
  undercroft door asks `LongHymn.mayOpenUndercroft`: Chapter Five complete, or a listed participant of the running
  Hymn (the M3 seal rule).
- `RaidSession` (shared, many owners): the participant set, the RED bar (12 notches) re-synced to the participants
  once a second, the music (`boss_music(cantor, 1)`), each participant's time away (alive outside, another dimension or
  offline for 100 ticks: no longer a participant), and the wipe: no participant alive inside for 200 ticks stops the
  Hymn and resets it. A dead participant stays listed and may come back in.
- The First Cantor rises at `CANTOR` over 40 ticks (2.6 blocks, floating, never walking; `first_cantor` 0). Health
  2,000 × (1 + 0.5 × (n − 1)) for 1 to 8, with the cap trick; joiners in the window rescale him keeping his fraction.
  Damage gates as Ulvaine's, plus the choir's shield; phases at 70% and 30% are floors (60-tick transitions); at his
  last floor (1 health) he unravels over 60 ticks (`first_cantor` 16).

### 11.2 Phases

- **The Choir**: four `cantor_choir` echoes (60 health each, scaled like him) sing at `CHOIR_1..4`; while any sings he
  is shielded (`first_cantor` 15, a pale-gold shell; blows on him are turned). Every 200 ticks the **verse beam**: dust
  lifts along its coming path for 40 ticks (`first_cantor` 1, a tell), then a beam of pale light sweeps 120 degrees over
  40 ticks (`first_cantor` 2), 18 long and 1.2 wide at a body's height (it cannot be jumped), 8 once a sweep to each
  player it crosses, aimed so its middle passes a participant. If he is not below 70% within 900 ticks of the last echo
  falling, the choir returns. A Push staggers an echo's song for 40 ticks; a Stasis holds it silent.
- **The Verses**: the slam every 140 ticks (20-tick tell `first_cantor` 3: fists raised; then 10 and knock-back 1.2
  within 4, `first_cantor` 4); chain lightning every 180 ticks (3 targets within 16, 5 each, `first_cantor` 5); every 600
  ticks a verse, in turn: **Stormwood** (30-tick tell `first_cantor` 6: cracks spread under a participant; then roots
  grip everyone within 6 for 60 ticks, Slowness IV, `first_cantor` 7; an ally's blow on a held player's roots frees
  them, PvP or not), **Mire** (3 pools of black fog 4 across for 200 ticks, 2 a second and Slowness I inside from a
  second after they spread, `first_cantor` 8), **Basalt** (5 marks plus one per participant beyond four, `first_cantor`
  9 for 40 ticks, then lightning from the vault on each, `first_cantor` 10: 10 and `shock` 20 within 2). The first marks
  and circles fall under participants. Shades every 1,200 ticks (the first after 300): 2, plus 1 per two participants
  beyond two.
- **The Long Hymn**: the engine wakes. The rings sing in turn: 30-tick tell (`first_cantor` 11), then 100 ticks
  singing (`first_cantor` 12), 4 a second on the singing ring (radius 4); one sings at a time. The three silencing
  points open (`first_cantor` 13): a participant crouching within 2.5 blocks of an open point for 60 ticks silences it
  (damage breaks the channel, the verse stone rule; `first_cantor` 14), opening a 400-tick window of double damage (a
  new silence restarts it, never stacks; his light falters while it is open). Once all three are silenced and the
  window has closed, they open again. The slam and the chain go on.
- Counterplay: he takes no knock-back; Choke and Lift are refused (`CantorReactions`); Fear needs `#c:bosses`
  (requested); Drain works on him, never on the echoes (they refuse its damage). The echoes are fearless and
  force-resistant (Phase 0 tags).

### 11.3 Clear, wipe and rewards

At the clear every participant listed, alive or dead, is paid by `RaidRewards` and reported through
`QuestHooks.onRaidCleared(p, firstClear)`. Once per raid week (`Clocks.day / 7` in `last_clear_week`): 2,000 XP,
3 rolls of `gameplay/long_hymn` (a hymnstone shard each), 400 credits and a legendary schematic roll (25%; after 2
weekly clears without one, the next is sure: `RaidData.pity`) among the six the Hymn teaches and the player does not
know (the Cantor's Emitter, Sunforged Grip, Stormglass Lens, Hymnweave Lining, Sunstone Edge, Hymn-Tuned Drive). The
first clear adds Resonance `long_hymn` +500, the `long_hymn` banner (`lesson_complete` 15), W7's Cantor's Mask and the
entry `the_first_cantor_falls`. A participant already paid this week helps for 300 XP. A wipe pays nothing; dead
participants are told it failed. Nothing about a running raid is saved: a restart resets it.

## 12. The boss music service (12.5)

`client/boss/music/BossMusicService`: `register(bossId, type, tracks)` and the payload `boss_music(bossId, track)`.
Track 1 to n crossfades to that loop over 40 ticks; 0 fades out; a loop already playing never restarts and one still
fading comes back. Stop paths: track 0, another loop crossfading in, the local player's death, a level or dimension
change and disconnect (at once), `ClientConfig.bossMusic` off (it resumes when turned on mid-fight), the engine
stopping the sound (`/stopsound`, a reload), and a watchdog: no entity of the registered type within 64 blocks for 200
ticks. While a loop plays or fades vanilla music is held off by the one `MusicManager` mixin
(`MusicManagerBossMusicMixin`), which replaces the M3 `MusicManagerMixin` and the M4 `MusicManagerKaastMixin`. The
Collector and Kaast keep their three loops (their senders are the lead's: section 16). `BossMusicPayload.send` on the
server; `wanted()` and `holdsVanillaMusic()` for the tests.

## 13. The `hologram` effect (6.4, 15.3)

W4's dialogue action sends `hologram` (around; pos = the desk, entity = the speaker; variant 0 start with intensity =
its ticks, 1 end). `HologramFx` stands Ulvaine's model small over the desk between the speaker and the player, facing the
player, translucent blue and full-bright with the banded hologram skin; scan bands climb slowly through her and her
edges flicker (a faint shell whose light stutters), steady with flashing effects off. No particles. The `story.hologram`
loop plays while she shows; she folds away at the end event, after her ticks, when the player moves 16 blocks away,
dies, changes level or disconnects.

## 14. Effects and sounds, phase by phase (15)

Every look goes through `client/fx/Vfx` budgets; tells use `Vfx#tellCount` and are never thinned; nothing draws a
ground circle, halo, sigil or outline. The server plays the one-shot sounds; the client keeps the loops (`FoeLoops`,
each with a key and a condition read every tick, fading out on every 15.4 stop path; `FoeWatch` restarts synced-state
loops, so no lost event leaves a loop playing or silent).

| Event | Anticipation (tell) | Activation and travel | Impact | Aftermath | Sound |
|---|---|---|---|---|---|
| Bruiser cut and shove (`npc_windup`) | blade drawn back, glint | the swing | M2 hit | — | M2 wind-up, `cartel.shove` |
| Gunman and captain shots | muzzle glow 12 / 10 ticks (`CartelGlowLayer`) | the M1 bolt | `blaster.impact` | — | `blaster.fire` |
| Captain's frag | M3 `grenade_tell` (the arm drawn back) | the M3 grenade | M3 blast | — | M3 grenade sounds |
| Hask jet-slam (`hask_warrior` 0, 1) | crouch, 8 `engine_exhaust` from the pack over 20 ticks | the jets' arc | flame and a burst of the ground's dust, uneven, with gaps | dust settling | `hask.slam_tell`, `hask.slam` |
| Hask shield (2, 3, 4) | bash: shield drawn back, 4 `spark_shower` off the rim over 15 ticks | the shove | a crunch of grit; block: sparks off the shield | — | `hask.bash_tell`, `hask.bash`, `hask.shield_block` |
| Chant (`hymnsworn` 0) | — | breath fogging before the veil, grit trembling in a few uneven spots (no ring) | — | — | `hymnsworn.chant` (loop while chanting) |
| Storm call (`hymnsworn` 1, 2) | 20 `lightning_spark` crawling over an uneven patch, 10 `gather_dust` lifting, over 40 ticks; static between the raised hands | vanilla's cosmetic bolt | scorch: smoke, the ground's dust, sparks | ash settling | `hymnsworn.call_tell`, `hymnsworn.call_strike` |
| Force lightning (`hymnsworn` 3) | — | an arc from the left hand per pulse (`FoeArcs`, steady with flashing off) | sparks on the chest | — | `force.lightning.release` |
| Scour (`scour` 0–6) | ripple: a faint stir along an uneven line; burst tell: 15 `sand_burst` boiling over 15 ticks; sweep tell: 12 `sand_burst` along the tail's arc over 20 ticks | burst: 30 `sand_burst` thrown up with the ground's dust, a jolt up close | bite; sweep: dust along the arc | burrow: sand slumping in, in clumps; brood call: churning in 2–3 spots | `scour.hiss` (loop), `scour.burst_tell`, `scour.burst`, `scour.burrow`, `scour.matriarch.{sweep,call}` |
| Stormback (`stormback` 0–4) | charge: 10 `gather_dust` pawed up over 20 ticks; roar: 12 `lightning_spark` gathering over the plates over 30 ticks | the charge | a heavy thud of dust and grit | charged: a burst of static, then 1 `lightning_spark` per 4 ticks while it lasts; spent: falling sparks and a wisp of smoke | `stormback.charge_tell`, `stormback.impact`, `stormback.roar`, `stormback.charged` (loop) |
| Ulvaine step (`ulvaine` 1, 2) | 12 `gather_dust` drawn in and one `air_ripple` over 15 ticks | — | air shoved aside where she reappears | — | `boss.ulvaine.step_tell`, `boss.ulvaine.step` |
| Ulvaine lightning (3) | — | an arc from her raised hand to the struck chest per pulse | — | — | `boss.ulvaine.lightning` (loop while channelling) |
| Rods (4, 5) | `StormTellFx` on the patch over 30 ticks | vanilla's cosmetic bolt (in the domed Heart it shows from the ceiling down) | scorch under the bolt | — | `boss.ulvaine.rod_charge`, `boss.ulvaine.rod_strike` (with vanilla thunder) |
| Hymn pulse (6) | the wave itself is the tell: 48 `gather_dust` along the front every 4 ticks, an irregular wall cut into slots of varied height with gaps (the M3 Surge look) | rolls out to 16 over 40 ticks | — | — | `boss.ulvaine.pulse` |
| Crescendo (7, 8, 9) | a banner, then 6 `light_mote` rising from each standing resonator a second | a resonator lights in a flare of motes | blast: the hall shakes, dust falls from above | a broken spire sinks into its plinth in violet shards | `boss.ulvaine.crescendo`, `boss.ulvaine.resonator`, `boss.ulvaine.blast` |
| Phases, yield, echo (0, 10, 11) | — | a banner and a gust of dust | — | yield: dust as she kneels; echo: pale wisps as it comes apart | `boss.ulvaine.phase`, `boss.ulvaine.yield`, `boss.ulvaine.echo_fade` |
| Cantor beam (`first_cantor` 1, 2) | 24 `tomb_dust` lifting along the coming path over 40 ticks | the rotating beam drawn at a body's height with a faint sheet of the band it sweeps | — | — | `boss.cantor.beam_tell`, `boss.cantor.beam` (loop while sweeping) |
| Slam (3, 4) | 16 `gather_dust` lifting under him over 20 ticks | the blow | the floor cracks, debris | — | `boss.cantor.slam_tell`, `boss.cantor.slam` |
| Chain (5) | — | an arc per link | — | — | `boss.cantor.chain` |
| Roots (6, 7) | cracks spreading, 20 `tomb_dust` along them over 30 ticks | the grip | roots bursting at a freed player's feet | — | `boss.cantor.roots` |
| Mire (8) | — | a dark soft-edged stain creeping over the floor, with wisps | — | — | `boss.cantor.fog` |
| Basalt (9, 10) | `StormTellFx` on each mark over 40 ticks | a bolt from the vault (drawn by the client: no sky bolt under the roof) | stone cracking | — | `boss.cantor.mark`, `boss.cantor.strike` |
| Rings (11, 12) | 12 `vent_grit` trembling over the stones over 30 ticks | while singing, 2 `gather_dust` and one `air_ripple` per 5 ticks in uneven patches | — | — | `boss.cantor.ring_tell`, `boss.cantor.ring` (loop while singing) |
| Silencing (13, 14) | a slow column of motes over each open point; the channel draws light in | — | a silenced point bursts | his light falters during the window | `boss.cantor.silence` |
| Choir and shield (15) | — | pale motes drifting from the echoes to his robes | — | a falling echo fades into wisps | `boss.cantor.choir` (loop for each singing echo; once as the choir rises and, lower, as an echo falls) |
| Rise, phases, unravel (0, 16) | the keystone stirs the engine | he gathers out of the floor in drifting light; banners | — | 120 `spectral_wisp` over 60 ticks as he unravels | `boss.cantor.phase`, `boss.cantor.engine` (loop in phase 3), `boss.cantor.unravel` |
| `hologram` | — | the translucent figure | — | — | `story.hologram` (loop) |

Lasting looks: the Cantor and each echo shed 2 `spectral_wisp` a tick; the stormback's charged sparks and the
resonators' light follow synced state. Storm flashes are only vanilla's own bolt flash (W3 and the lead dim it with
`stormFlashes`); `FoeArcs` and the beams flash nothing. Final sounds are described per id in `docs/assets/foes_m5.md`.

## 15. API status

The one `// API-CHECK:` line was probed after the merge (`tools/api-probe/m5_w8.txt`): `client/enemy/cartel/CartelGlowLayer`
copies `ItemInHandLayer#submitArmWithItem`'s transform (rotX −90, rotY 180, translate), as the M1 `MuzzleGlowLayer`
does, and 26.2 still applies it to an adult's held item, so the muzzle glow sits on the muzzle.

The mock raiders in `gametest/m5/FoesClientSteps` use W6's recipe (`io.netty.channel.embedded.EmbeddedChannel` and
`PlayerList#placeNewPlayer`), which compiled and ran on CI at `c532e60`, so it carries no check.

`RaidRewards` gives W7's `EquipmentItems.CANTORS_MASK` directly (it no longer looks the mask up by id), so it
carries no check.

Verified in bytecode or by existing use: `EntityTypes.LIGHTNING_BOLT`, `LightningBolt#setVisualOnly`,
`Mob#checkDespawn` discarding creatures on Peaceful, `BiomeModifications.addSpawn`, `ServerLivingEntityEvents`
(`ALLOW_DAMAGE`, `ALLOW_DEATH`, `AFTER_DAMAGE`), `UseBlockCallback`, `AttackEntityCallback`, the attachment API.

## 16. Requests to other owners

- Lead: register `FoesM5GameTests` in `src/gametest/resources/fabric.mod.json`; remove the five deleted classes'
  references (`FoesClient`, `BossClient`, two javadocs); switch the Collector's and Kaast's music senders to
  `boss_music`; add `darth_ulvaine` and `first_cantor` to `#c:bosses` and `darth_ulvaine`, `first_cantor`,
  `cantor_choir` to `#oldrepublic:push_resistant`; optionally a per-type kill XP hook (section 17).
- W4: `cartel_enforcer` and `hask_warrior` in `CreditDrops.DROPPERS`.

## 17. Balance (16.3, 16.4)

Every number of 12.1, 12.2 to 12.4 and 16.3 is a key in its section, with the spec's defaults. Additions (keys the
spec states in prose or leaves open) are marked in each class: the enforcers' and warriors' ranges, cooldowns and tell
lengths, cover thresholds, wary range, engage and provoke times and hub leash; the scour's ripple, flee and brood
numbers; the stormback's provoke, anger, stun, butt and charged-shock numbers; Ulvaine's armour, knock-back
resistance, speed, Heart size, tell timings, lightning range and cooldown, Push numbers; the raid's armour, transition,
rise and unravel times, choir stagger, beam geometry and interval, slam and chain intervals and reach, basalt radius and
shock, the shades' first spawn, ring radius and silence reach; the mentor duel's tell timings and powers.

Kill XP: the 16.3 XP column is in balance (`gunmanXp` and the rest), but `Progression` pays every kill by the M1 rule
(`min(killXpCap 80, maxHealth × 1.5)` with fatigue), so a matriarch pays 80, not 400. A per-type hook in
`Progression.onDeath` (the lead's) would let the foes pay their own numbers; until then the M1 rule stands.

## 18. Persistence, multiplayer and edge cases

- Saved: the five foes (variants, burrowed state, charge timer, standing state is in memory only, like the troopers),
  `RaidData`. Never saved: Ulvaine, the resonators, the First Cantor, the choir, summoned chanters and shades; every
  session is in memory, so a restart resets a running fight (the M3 rule) and a still-open yield brings her back
  kneeling from W4's story flag.
- Multiplayer: every look goes through effect events; loops follow synced state; the bars re-sync each second;
  participants are kept by the M3 rules; the raid scales from 1 to 8.
- Death, respawn, dimension change and disconnect stop the music and every loop at once; the leave timers handle the
  rest. Peaceful: Hymnsworn, scours and stormbacks never spawn (and despawn); the cartel and the clan stay at Neutral at
  most; Ulvaine talks, then kneels; no echo and no raid.

## 19. Tests (19.1, 19.2)

`FoesM5GameTests` (20 tests, each ≤ 400 ticks, small arena fixtures; boss sessions use recorded hooks so no story is
touched): `tellsPrecedeHeavyHits`, `enforcerAttitudeByStanding`, `haskFearlessAndStrongWilled`, `chantHealsAllies`,
`stormCallTellThenStrike`, `scourBurrowBurstCycle`, `scourHurtOnlyAboveGround`, `stormbackNeutralUntilProvoked`,
`stormbackChargedByLightning`, `mentorDuelYieldsAndResets`, `ulvainePhasesAndYield`, `ulvaineHealthScalingAndCap`,
`resolveCallsHook`, `echoOncePerDay`, `raidParticipantsAndScalingOneToEight`, `raidWeeklyLockoutAndPity`,
`raidWipeResets`, `newFoesReportKills`, `ulvaineBreaksStasisAfter20Ticks`, `cantorChoirShieldVersesRingsAndWindow`.

`m5/FoesClientSteps`: `bossMusicServiceRegistry` (every boss id with its tracks in order and its watchdog type; an
unknown id has none), then screenshots 185 to 199: the bruiser's wind-up, the jet-slam tell, the storm call's tell, a
scour's burst tell and burst, a stormback's charge tell and its charged plates, Ulvaine in each phase and the yield,
the First Cantor in each phase with four players (three mock raiders), and a wipe. Shortcuts are logged; the player is
shielded with hidden Resistance V (an invulnerable player would not be targeted); everything is put back.

## 20. Decisions

- Tell lengths not in the spec: the slam 20, roots 30, basalt 40, rings 30 (as 12.3), the beam's width 1.2 and height a
  body's (so it cannot be jumped); the hologram stands small over the desk between speaker and player.
- Impacts use vanilla and shared particle types (the M4 rule) beyond W8's two; resonators sink into their plinths and
  echoes fade instead of toppling.
- Non-music loops are 3 to 4 s and not streamed; the rods' strike is layered on vanilla's thunder.
- The enforcers', warriors', Hymnsworn's and bosses' loot tables are empty by design: credits come from W4's drops,
  clan iron and stormglass from code, the bosses pay through their fights.
- Test seams: `FoeKills` listeners and each session's own hook sink (`CollectorHooks.Sink`), so no test swaps
  anything global.
- `GameTestHelper#spawnWithNoFreeWill` still runs `customServerAiStep`: inert dummies use `setNoAi(true)`.
- Mobs never target a test's mock player and mock players cannot be spectators: the tests use villagers and fakes as
  prey, and the mentor duel's spectator-away case is covered by the leash instead.
- Scours spawn burrowed. Fights in the tests use small arena fixtures laid out by `HeartLayout` and `UndercroftLayout`
  instead of `RuntimeSites.placeAt` (the layouts take explicit spots, and fall back to rings around a centre).
- A raider who fled alive gets no wipe line (only the dead are told); the wipe itself has no look beyond the engine
  falling quiet and the music stopping.
- `BossMusicService` gives a fresh loop 20 ticks to become active before it counts as stopped by the engine.

## 21. Known issues

- Kill XP follows the M1 rule, not 16.3's column, until a per-type hook exists (section 17).
- Until the lead adds the tags (section 16), Ulvaine and the First Cantor are not in `#c:bosses`, so what spares
  bosses (immunity to Fear and Pacify, Stasis's boss multiplier, the saber's rending throw) treats them as ordinary
  foes; and Ulvaine is not `#oldrepublic:push_resistant`, so a Push moves her at full strength (the Cantor's and the
  choir's own handler already refuses it). She still breaks Stasis after 20 ticks herself.
- Until `CreditDrops` lists them, cartel enforcers and Hask warriors drop no credits.
- The owncheck tool reports false positives on Minecraft supertypes (five "reference to isDuelling is ambiguous" in the
  tests, `FoeLoops`' inherited `stop()`/`isStopped`).
- Nothing is compiled or run in this container: every test and screenshot waits for CI.
