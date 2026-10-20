# Boss feature design: Orren Vasch, the Collector, and the Reliquary fight

Owner: W5 (boss, constructs and saber). Part A wrote the fight runtime described here; part B's
"Ruin Sentinels" and "Reliquary placement" sections are merged at the end.

Packages: `com.oldrepublic.boss` and `com.oldrepublic.boss.block` (server and common),
`com.oldrepublic.client.boss` (client). Spec sources: `docs/MILESTONE_3.md` 8 (all), 13 (13.1-13.3 boss rows),
14 (`collector`), 16 and 17. Balance numbers live in `CollectorBalance` (section `collector`); every number
below is its default.

Nothing here has been compiled in this container. APIs were checked against the 26.2 dumps and the Fabric
sources; what could not be checked carries `// API-CHECK:` (section 14).

## 1. Content and ids

| Kind | Id | Class or file |
|---|---|---|
| Entity (boss) | `oldrepublic:orren_vasch` | `OrrenVasch` (`Monster`, `SaberDuelist`, `Aligned` ARCHIVE, `Yieldable`); 0.6 x 1.95, eye 1.62, tracking 10, fire-immune, `noSave` |
| Entity | `oldrepublic:pylon_focus` | `PylonFocus`: the singing crystal's hit box (0.8 x 0.8, non-living, attackable like an end crystal), `noSave` |
| Entity | `oldrepublic:archive_debris` | `ArchiveDebris`: the Hurl chunk (0.75, a thrown-item projectile of stone), `noSave` |
| Blocks | `resonance_pylon`, `reliquary_heart` (+ block entity), `reliquary_seal`, `hoard_plinth` | `block/*`: unbreakable, no loot, in `#oldrepublic:built_blocks`; the pylon's `crystal` property is `PylonCrystal` (DORMANT, SINGING, SILENCED, REGROWING, EMPTY), the seal's `open` and `vault` |
| Damage types | `collector_surge`, `resonant_lash`, `archive_debris`, `archive_grip`, `repelling_palm` | `data/oldrepublic/damage_type/*.json`, `BossDamageTypes` |
| Effect events | `boss_hurl`, `boss_palm`, `boss_lash`, `boss_surge`, `boss_spent`, `boss_grip`, `boss_lunge`, `boss_ward`, `boss_phase`, `boss_yield`, `boss_leash`, `boss_echo`, `collector_resolve`, `pylon_state`, `hoard_open` | `BossEffects` (ids and variants), drawn by `client.boss.BossFx` |
| Particles | `ward_haze`, `ward_current`, `surge_dust`, `lash_seam`, `echo_smoke` (`lumen_spark` is the sentinels'; `crystal_glint` is reused) | `BossParticles`, `client.boss.BossParticleProviders` |
| Loot | `oldrepublic:gameplay/echo_cache` | one pool: `emitter_reliquary` weight 1, empty weight 3 (25%); W2 rolls it per echo clear (twice Unchained) |
| Dialogue | `oldrepublic:reliquary_heart` | the heart's rematch menu: "Call the echo" (`boss:call_echo`), "Unchained" (`boss:unchained`) |
| Lang | `src/main/lang-parts/boss.json` | entities, blocks, bar names, banners, lines, heart and command messages, death messages |
| Commands | `/oldrepublic boss place|start|phase|reset <player>` | `BossCommands` (10.8) |
| Balance | section `collector` | `CollectorBalance` |

Server classes: `BossFeature` (init order: B's `ConstructFeature` and `ReliquarySites` first, then blocks,
entities, particles, damage types, reactions, arenas, dialogues, commands), `BossArenas` (sessions, arena
tracking, `resolve`, the echo, heart and seal use), `ArenaSession` (one fight), `ArenaLayout` (world cells of a
hall), `HeartState` and `HeartRules` (the heart's pure state machine), `CollectorRules` (pure numbers),
`CollectorCombatGoal` (his moves), `CollectorProfiles` (duelist profiles), `CollectorReactions` (Force and
damage hooks), `CollectorDialogues` (dialogue hooks), `CollectorScenes` (the 8.7 endings), `CollectorHooks`
(quest hook sink: `QuestHooks` in play, a recorder in tests).

Client classes: `BossClient` (init), `OrrenVaschModel`, `OrrenVaschRenderer`, `OrrenVaschRenderState`,
`BossFx` (every handler and the ambient look), `SurgeWallFx` (the Surge's dust wall), `BossLoops` (the ward hum
and pylon songs), `BossParticleProviders`. The music was B's `client.boss.music.BossMusic`; since Milestone 5
it is `BossMusicService`.

## 2. The heart and the session

The **heart** (`ReliquaryHeartBlockEntity`) is the saved half of the fight. Tags: `Owner`, `State` (ordinal of
`HeartState`), `FirstClear`, `Attempts`, `EchoClears`, `Unchained`, `HallOrigin`, `HallRotation`, `HallBox`,
`YieldNotified`.

```
DORMANT --OWNER_NEAR--> READY --FIGHT_START--> ACTIVE --YIELD--> YIELDED --RESOLVE--> RESOLVED
        <--OWNER_AWAY--       <-----RESET------
```

`HeartRules.step` is the whole machine. YIELDED and RESOLVED persist; ACTIVE is never restored after a load
(a fight in progress simply resets). Peaceful sends YIELD from READY.

The **session** (`ArenaSession`) is held in memory by `BossArenas`, keyed by dimension and heart position,
ticked from `ServerTickEvents.END_LEVEL_TICK` and cleared on `SERVER_STOPPING`. The heart's block entity
ticker only manages DORMANT/READY and creates the session the state needs (Vasch reading at the lectern for
READY, kneeling at the dais for YIELDED). Stages:

```
WAITING (lectern) --owner crosses, first attempt--> MEETING --closed or 400 ticks--> FIGHTING
WAITING --a later attempt: 6 blocks from the dais, the lectern or Vasch used----->  FIGHTING
FORMING (the echo forms, 60 ticks) ----------------------------------------------> FIGHTING
FIGHTING <--> TRANSITION (60% and 30%, 60 ticks each) --10%--> YIELDED --resolve--> RESOLVING --> ENDED
FIGHTING --nobody alive inside for 200 ticks--> WAITING (reset; an echo ends instead)
```

- **First attempt.** The owner crosses the threshold plane (`ArenaLayout.pastThreshold`); the doorway seal
  closes 40 ticks later once nobody stands in the doorway; W2's `c3_collector_meet` opens with Vasch as the
  speaker (an Independent also hears the offer line). The fight starts on `Dialogues.ON_CLOSE` or after 400
  ticks, closing the dialogue if it is still open.
- **Later attempts** (`attempts` >= 1): no dialogue, the door stays open, he waits at the lectern. A
  participant within 6 blocks of the dais, a use of the vanilla lectern in the hall (`UseBlockCallback`) or a
  use of Vasch starts the fight with "Again, then." (or "Face me."); the door closes behind them as before.
- **Peaceful:** no fight. He says his Peaceful line (an Independent owner hears his offer instead) and kneels
  60 ticks later (`PEACEFUL_TALK_TICKS`, so the line can be read before the yield line), YIELD, so the owner's
  choice still works. Nothing else in the session runs while he talks. He overrides `checkDespawn`, so Peaceful
  never discards him.
- **A new session starts from a quiet hall** (`syncBlocks`, for a fresh READY session, a kneeling YIELDED one and
  the echo): every doorway seal is written open and every pylon crystal DORMANT, whatever the blocks were saved as,
  so a crash, a kill or an unload mid-fight can never leave the owner sealed out. Block writes skip chunks that are
  not loaded (`level.isLoaded`), so ending a session while its hall unloads never loads chunks or loses a write that
  matters: the next session puts the blocks right.
- **Participants** are awakened, living, non-spectator players in the hall box at the start, and newcomers
  while the fight is in phase 1 (the roster closes at the first transition). A dead participant stays listed.
  A listed participant using the closed doorway seal, or standing within 3.5 blocks of the threshold on the
  antechamber side, is let through and the seal closes behind them. A participant who is alive outside the
  whole Reliquary box, in another dimension or offline for 100 ticks is dropped from the list and the bar.
- **Reset** (no participant alive in the hall box for 200 ticks): he returns to the lectern at full health,
  pylons go dormant, sentinels and debris are discarded, item entities in the hall box move to the antechamber
  floor (three cells from the threshold, away from the dais; owner and pickup rules kept), the seal opens, the
  heart is READY and `attempts` keeps counting. An echo session simply ends.
- **The bar** is a vanilla `ServerBossEvent`, players added and removed by the session (refreshed every 10
  ticks): "Orren Vasch · The Archivist" white, "· The Singing Wall" purple, "· Unkept" red; "Echo of the
  Collector" (and "· Unchained") for the rematch. Progress is health over max health.
- **Threat:** the participant with the most damage in the last 200 ticks, else the nearest inside. The Hurl and
  Lunge target switches to the nearest other participant every 200 ticks.
- **Health:** 450 x (1 + 0.6 x (n - 1)), n capped at 4 (x1.5 Unchained). A phase 1 newcomer raises the
  maximum and keeps the current fraction.
- **Leash:** if he leaves the hall box he vanishes in smoke and reappears at the dais (`boss_leash`).
- **Damage gate** (`acceptsDamage`): only while fighting outside a transition, and only when the damage
  source's attacker (so a player's projectiles count) is a listed participant standing in the hall box;
  everything else is refused.
- **Phase floors:** `OrrenVasch.setHealth` never lets a hit take him below the current phase's floor (60%,
  30% or 40% Unchained, 10%; the echo's floor is 1 health in phase 3). Reaching it starts the transition,
  the yield or the echo's dissolve on the session's next tick.
- **Lines** are overlay text to everyone in the Reliquary (subtitled, 8.1): meeting, "Listen to them sing..."
  at the first transition, "Every Order burns..." at the second, the yield line, "Again, then.", "Face me.",
  the Peaceful line.

## 3. Phase 1: The Archivist (100% to 60%)

`CollectorCombatGoal` extends B's `DuelistCombatGoal` with the `collector_archivist` profile (light 6, heavy 11,
guard 140, parries light attacks above 70% stamina 30% of the time, strings 2-3, wind-up 10, recovery 16) and
adds his Force moves. Moves start when the duel goal is neutral and advance every fight tick through the
session (`serverTick`), so a running Surge or Grip goes on even if the duel pauses.

- **Archive Hurl** every 180 ticks at the Hurl target: a chunk (`ArchiveDebris`) tears from the nearest debris
  pile within 16 blocks (the floor beside him when there is none) and drifts to above his raised off-hand for
  the 24-tick tell, then flies at 1.1 for 8 damage (`archive_debris`).
  - Pull (`force_pull` or `pull_disarm`) in the tell rips it out: it falls between them and he staggers 20 ticks.
  - Lift in the tell catches it; it drops harmlessly on release, or with `lift_hurl` it flies back at him
    for the Push-return damage.
  - Push within 6 blocks of the flying chunk returns it: 12 damage (x1.5) and a 30-tick stagger. He staggers only
    when the hit lands (not during a transition's invulnerability, while yielded, or from a returner outside the fight).
  - A saber guard blocks it for 30 stamina and it breaks; it is never deflected (`saberDeflectable` false).
- **Repelling Palm** when a participant has stayed within 3 blocks for 80 ticks: 16-tick tell, then 4 damage
  (`repelling_palm`) and knockback 1.4 in a 110-degree arc in front of him. A Push in the tell is the M2 push
  clash: both are pushed back and he staggers 20 ticks.

## 4. The transitions

At 60% (to the Singing Wall) and 30% (to Unkept; 40% Unchained). Invulnerable for 60 ticks; nothing interrupts.

- **One:** the current move is aborted. He leaps in an arc to the dais (set down on it after 22 ticks if the
  leap fell short) and lifts a singing crystal. The four pylons wake 15 ticks apart (15, 30, 45, 60). The bar
  turns purple. Phase 2 starts with the ward at full strength and the sentinels woken.
- **Two:** every pylon still standing shatters into him, the ward goes down for good, he takes up the stolen
  holocron (hood up, synced `phase` 3) and the bar turns red.

## 5. Phase 2: The Singing Wall (60% to 30%)

- **Resonance Ward:** damage taken x0.35 with 3-4 pylons singing, x0.65 with 1-2, x1.0 with none.
- **Pylons:** each singing pylon has a `PylonFocus` on its crystal with resonance 30 x (1 + 0.25 x (n - 1)).
  What each source takes off:

  | Source | Resonance |
  |---|---|
  | saber hit | 10 |
  | Push (any branch) / Focused Blast | 10 / 15 |
  | Saber Throw | 15 |
  | Lightning | 10 per second |
  | a parried sentinel lance | 15 |
  | Pull within 12 blocks, in sight | rips the crystal out at once (EMPTY) |
  | Lift within 12 blocks, in sight | silences it after a 20-tick channel |
  | anything else a player hurts it with | its damage |

  Only players silence a pylon: damage whose source is not a player (an unparried lance, fire, an explosion, his own
  debris) takes nothing off. A parried lance belongs to the parrier, so it counts. A silenced crystal goes dark (`SILENCED`; ripped: `EMPTY`). It **re-sings** 800 ticks later, with a 60-tick
  regrow tell, only while another pylon still sings and at most once per pylon per phase. Hitting a regrowing
  crystal cuts it back and restarts the 800 ticks.
- **Unsung:** when the last pylon falls silent he is shoved 0.9 blocks off the dais with his guard broken, takes
  x1.5 for 100 ticks, and the ward stays down for the rest of the phase (regrowing crystals are cut back).
- **Resonant Lash** every 140 ticks at his target. He never strays more than 8 blocks from the dais: a target
  beyond that is answered from the dais. A 20-tick tell dragging his blade tip, then a ridge travels along the floor at 0.8
  blocks per tick for up to 20 blocks, following the floor up one step and down up to four. Grounded players
  on the ridge (0.9 either side, up to 0.7 above it) take 9 (`resonant_lash`) and knockback 0.6, once per
  ridge; a jump clears it.
- **Sentinels:** 2 bound Watchers wake in the gallery alcoves, +1 per extra participant, at most 4 (B's
  `Sentinels.spawn`). They are discarded at the yield, the reset and the end.

## 6. Phase 3: Unkept (30% to 10%)

Profile `collector_unkept`: light 7, heavy 12, guard 100, parry 15%, strings 3-4, wind-up 8, recovery 12.

- **Lunge** (at most every 100 ticks, `lungeCooldown`) at the Lunge target when it is beyond his reach and
  within 8 blocks: 14-tick crouched tell, then a 5-tick dash and one heavy strike of 10. `pull_disarm` (its
  own disarm applies) or `force_push` (he staggers 20 ticks) in the tell interrupts it.
- **Holocron Surge** every 300 ticks: 30-tick gather that nothing interrupts; at release, radius 8, 10 damage
  (`collector_surge`), knockback 2.0. **Cover is decided on the server:** two rays (block colliders) from his
  chest to each player's eye and chest centre. Both blocked: nothing; one blocked: x0.5; airborne: x0.5, which
  stacks. A participant who casts Push or a Deflecting Pulse facing him (within 60 degrees) in the last 8
  ticks of the gather **clashes**: they and anyone within 3 blocks behind them (on the far side from him) take
  nothing. Then he is **Spent** for 60 ticks: guard broken, x1.5 taken, full control durations, and Lift lifts
  him 1.2 blocks (`liftHeight`); W3's Lift slams him with its 10-tick boss stagger (`force_lift.bossStaggerTicks`).
- **Archive Grip** every 400 ticks, never within 60 ticks of a Surge (and the next Surge waits for the Grip):
  20-tick tell on the farthest participant, then `Holds.hold` for up to 80 ticks, 1.5 blocks up, with 2
  damage (`archive_grip`) every 20 ticks. He channels and takes x1.25. It breaks when allies deal 8 or more in
  total, or when the held player casts Push (he staggers 20 ticks). In the tell, `force_push`, `force_pull`,
  `pull_disarm` or a single hit of 6 or more interrupts it.

## 7. Resistances and interrupts (8.4)

- Tags `#oldrepublic:push_resistant` and `#c:bosses` (lead), knockback resistance 0.8, armour 10.
- Immune to fall, drowning, fire, poison and wither (`isInvulnerableTo`, `canBeAffected`).
- `CollectorReactions` (a `ForceReactions.Handler`, a `DamageModifiers` entry and a `ControlEffects`
  duration modifier):
  - **Damage taken:** the ward, Unsung, Spent and the Grip channel; Lightning x0.6 and Saber Throw x0.7
    outside Spent. Unsung and Spent break his guard, which the saber pipeline already multiplies by
    `npcBrokenDamageMult`, so only the remainder is added and the totals are the 8.3 values.
  - **Control durations:** x0.5 (`controlMultiplier`) outside Spent; full while Spent and for his own
    counterplay staggers. Diminishing returns always apply.
  - **Lift:** `liftHeight` returns 0 except while Spent.
  - **Interrupts:** the one-argument `interruptWindup` (cause `unknown`) is ignored. The cause overload allows
    only the 8.4 causes for the tell in progress (Hurl: `force_pull`, `pull_disarm`; Lunge: `pull_disarm`,
    `force_push`; Grip: `force_push`, `force_pull`, `pull_disarm`). The Palm's counter is the push clash
    (`onPushHit`). `stagger`, `shock`, `saber_throw` and `heavy_fear` never interrupt; nothing interrupts the
    Surge gather or a transition.

## 8. Yield, endings and scenes (8.7)

At 10% he yields: moves end, sentinels and foci go, he kneels at the dais with his blade retracted, the heart
becomes YIELDED (saved) and the bar is removed. The owner's `c3_collector_yield` opens (W2), and credited
helpers (Chapter Three at RELIQUARY or CONFRONTATION) get `c3_collector_stance`. `onBossYielded` runs once per
yield when the owner is present (`YieldNotified`). Using him reopens the owner's dialogue or a helper's stance
dialogue. If the owner is away, he kneels until they return (after a restart too).

`BossArenas.resolve(owner, outcome)` marks the heart RESOLVED, opens the vault seal (`hoard_open`), opens the
door, plays the scene and calls `QuestHooks.onBossDefeated` once per credited player with the heart's
`firstClear` flag. W2 grants every reward.

| Outcome | Scene (variant) |
|---|---|
| SPARED (Jedi, and any non-Sith path) | (0) two Republic troopers (`Troopers.spawnEscort`) come down the descent; the binders lock (CUFFED pose) and they walk him up and out |
| SPARED (Sith) | (1) he rises, leaves his blade on the dais and walks out alone |
| EXECUTED | (2) one stroke after 12 ticks; he falls forward and lies still while anyone is in the Reliquary to see it |
| BOUND | (3) two Imperial troopers with binders march him out |
| RELEASED | (4) he bows his head, picks up his satchel and walks out alone |
| BROUGHT_TO_REPUBLIC / BROUGHT_TO_EMPIRE | (5) two troopers of that army escort him out |

If no escort can be spawned he walks out alone, bound. A scene ends when he is gone, or (walk-outs, the fallen
body) once nobody is left in the Reliquary; nobody watching ends a scene after at least 200 ticks, and none
lasts beyond 2,400.

## 9. Rematch and Unchained (8.9)

After RESOLVED, using the heart opens `reliquary_heart`. "Call the echo" (`boss:call_echo`) starts an echo
session: the echo forms from smoke at the dais over 60 ticks, has the same phases with damage x1.15, and never
yields: at its phase 3 floor it dissolves into smoke over 40 ticks and every listed participant gets
`onBossDefeated(ECHO, firstClear false)` (W2 applies the daily limit, the cache roll and the pity counter). The
first echo clear unlocks "Unchained" (`boss:unchained`): health x1.5, damage x1.25 (replacing x1.15), phase 3
at 40%; `BossArenas.isUnchained(participant)` tells W2 while `onBossDefeated` runs.

## 10. Client: model and renderer

`OrrenVaschModel` is a humanoid mesh on B's 64 x 64 skin (UV map frozen in the plan, 4.10): head and hat,
body, arms and legs (left limbs mirrored), plus a robe layer (16,32), the robe skirt (0,48), a cloak plane hung
from the shoulders (0,32), a bandolier of empty holocron cradles across the chest (24,48), a satchel at the
front of the right hip (24,54), sleeves (40,32) and the holocron in the left hand (36,54; shown in phase 3).

Poses come from the synced `Action` and its age: READING at the lectern; HURL (off-hand raised); PALM (hand
drawn back); LASH (blade low, tip dragged); LUNGE (the renderer crouches him, blade low); SURGE (holocron
raised overhead, the cloak lifting and streaming out on the inrushing air, its edge rippling; steady with
flashing off); SPENT (sagging, head down); GRIP (off-hand reaching for the target, trembling while he
channels); LEAP (arms raised, crystal lifted); UNKEPT (taking up the holocron); CUFFED (wrists together). The
kneel blends over everything.

`OrrenVaschRenderer` (`HumanoidMobRenderer`) picks `base`, `unkept`, `echo` or `echo_unkept` from
`textures/entity/orren_vasch/`, lowers him 0.55 when kneeling and sags him while Spent, tips him forward over 8
ticks after the execution's stroke (already lying if the stroke was not seen), and draws the echo
translucent and desaturated (alpha 0xB8 x presence, tint 0xD4D8DE), fading in over its form and out over its
dissolve, with its layers (the held blade) hidden while it is less than 60% present. In phase 3 the holocron lights his
hand (block light at least 6). His blade is B's saber layer from `duelSaber()` (amber, Reliquary emitter,
Archivist pommel).

`pylon_focus` draws nothing (`NoopRenderer`; the crystal is the pylon block's model); `archive_debris` is a thrown-item
renderer of its item (the pile's block) at 3x scale.

## 11. VFX and SFX, phase by phase

Art direction: dust, grit, debris, bent air and light; no rings, halos, outlines, sigils or ground marks. Tell
particles (variant 0 of hurl, palm, lash, surge, grip, lunge) go through `Vfx.tellCount` and are never thinned;
everything else goes through `Vfx.count`. Every handler checks `Vfx.inRange`. With flashing off nothing
flickers: shimmering motes are made steady and flashes become steady glows. Sounds are positional one-shots at
the event with a 0.95-1.05 pitch spread, scaled by the mod sound volume. Budgets are at Standard.

### 11.1 Archive Hurl (`boss_hurl`)

| Stage | Look | Sound |
|---|---|---|
| Anticipation (tell, 24 ticks) | His off-hand rises (HURL pose). At the pile, the chunk tears loose: 5 block chips of the pile and 3 dust puffs; while it rises and hangs above his hand, grit (cobble chips) pours off it and fine dust falls from it every other tick | `boss.collector.hurl_lift` (stone grinding) |
| Activation (throw) | 4 dust puffs trail back off the chunk | `boss.collector.hurl_throw` |
| Travel | The chunk (the pile's block as a 3x thrown item) flies along its arc | |
| Impact | Ripped: 10 block chips of the ground and the pile, 6 dust puffs spreading low, a light camera shake (0.1) within 6 blocks. Struck or blocked: the same burst quieter | `boss.collector.hurl_rip` |
| Counterplay | Returned: a bent-air streak of 8 cloud wisps along the new path back at him. Caught: the chunk hangs in bent air, cobble grit trickling every 3 ticks and faint haze around it, until released | `boss.collector.hurl_return`; caught: Lift's own loop |

### 11.2 Repelling Palm (`boss_palm`)

| Stage | Look | Sound |
|---|---|---|
| Anticipation (16 ticks) | Hand drawn back (PALM pose); 2 dust motes a tick drawn in from about a block around toward the palm | `boss.collector.palm_tell` |
| Activation | An air shove: 14 cloud wisps rolling out along his facing and 4 dust puffs off the floor in front of him; camera shake 0.15 within 6 blocks | the M2 `acolyte.shove`, pitched down |
| Clash | 14 dust puffs bursting from the meeting point; with flashing on a small explosion puff, with it off 3 steady poofs | `acolyte.shove`, lower |

### 11.3 The transitions (`boss_phase` 1 and 2) and banners

| Stage | Look | Sound |
|---|---|---|
| Activation | Banner "Orren Vasch" with "The Singing Wall" (purple accent) or "Unkept" (danger red); for the echo "Echo of the Collector"; camera shake 0.3 or 0.4 (scaled by the client setting) | `boss.collector.transition` (lower for the second); the music crossfade (B) |
| Travel (first 30 ticks) | Dust shaken from the ceiling over him, each speck from the underside of the first block above | |
| One (first 45 ticks) | Crystal light drawn in to the lifted crystal: twinkling glints, or steady pale motes with flashing off; the pylons wake one by one (11.6) | |
| Two (first 45 ticks) | Glass-white shards streaming into his chest from 3-6 blocks around, plus each pylon's shatter (11.6) | |

`boss_phase` 0 (start) shows "Orren Vasch / The Archivist"; resends with intensity 0 (a returning
participant) only sync the music and loops.

### 11.4 Phase 2: ward, Lash and Unsung (`boss_ward`, `boss_lash`)

| Effect | Stage | Look | Sound |
|---|---|---|---|
| Ward | Ambient | Sparse near-white heat-haze specks (`ward_haze`) rise off his shoulders and along his blade, `ward level` specks every other tick (0-4); never a bubble, halo or outline | `boss.collector.ward_hum` loop on him (11.8) |
| Ward | Ambient | From each singing pylon a thin wavering current of pale crystal motes (`ward_current`, full-bright, about 0.45 blocks a tick) streams to his chest, one mote per pylon every 3 ticks | `pylon.sing` loop on each pylon |
| Lash | Anticipation (20 ticks) | LASH pose; a seam of floor grit (`lash_seam`, 3 a tick) lifts in a straight line from him toward the target, growing out to where the floor runs straight (client-clipped, up to 14 blocks) | `boss.collector.lash_tell` |
| Lash | Travel | The ridge: grit and dust thrown up to a block high across its 0.9-block front, and a ground block chip every other tick, moving at the server's 0.8 blocks a tick | `boss.collector.lash_wave` |
| Unsung | Impact | The last currents snap back into the pylons (5 crystal motes from his chest to each pylon), he reels off the dais, 16 specks of ceiling dust fall over 5 blocks, camera shake 0.2 within 12 blocks; the ward hum stops | `boss.collector.unsung` |

### 11.5 Phase 3: Lunge, Surge, Spent, Grip (`boss_lunge`, `boss_surge`, `boss_spent`, `boss_grip`)

| Effect | Stage | Look | Sound |
|---|---|---|---|
| Ambient | | The holocron's crimson seams light his off-hand (steady motes every 6 ticks, block light 6); the cloak's edges fray into drifting ochre dust (every 4 ticks; steady, no flicker) | |
| Lunge | Anticipation (14 ticks) | Crouch, blade low; 5 dust puffs kicked back, then one every 3 ticks | `boss.collector.lunge` (low) |
| Lunge | Activation and travel | A streak of 10 cloud wisps along 6 blocks of the path, 4 dust puffs where he pushed off | `boss.collector.lunge` (sharp) |
| Surge | Anticipation (30 ticks) | Holocron raised, cloak lifting; floor dust from 3.5-6 blocks around streams in toward him, 2 puffs a tick, and a grit chip every other tick: 75 tell particles | `boss.collector.surge_gather` (rising) |
| Surge | Activation and travel | `SurgeWallFx`: 28 angular slots with jittered angles, 4-6 left empty as gaps, each with its own height (0.15-1.8, knee to over the head) and puff sizes; the front rushes out at 0.62 blocks a tick to radius 8; each slot is clipped on the client by a ray from his feet at the slot's height and its puffs collide with blocks; 10 tumbling ground-block debris; a kick of 6 puffs at his feet. Never a ring, never a flat sprite at floor level. Camera shake up to 0.35 inside the radius | `boss.collector.surge_release` |
| Surge | Impact | Slots blocked short of the radius break against the block (a puff splitting sideways) | |
| Surge | Clash | The wall breaks around the clashing player: the slots in a cone behind them stop at the clash and 8 puffs split to both sides | `boss.collector.surge_clash` |
| Spent | Aftermath (60 ticks) | He sags (SPENT pose); dust settles off him every 3 ticks; the holocron gutters (crimson sparks falling for 32 ticks; steady with flashing off) | `boss.collector.spent` |
| Grip | Anticipation (20 ticks) | Fingers curled toward the target; pebbles (ground chips) and dust rise around the target's feet | `boss.collector.grip` |
| Grip | Hold | The held player strains (`Holds` pose), dust trickles off them every other tick | `boss.collector.grip_hold` at the hold and each damage pulse (softer) |
| Grip | Break | A burst of 12 grit chips and 4 dust puffs at the player's body | `boss.collector.grip_break` |

### 11.6 Pylons (`pylon_state`)

| Variant | Look | Sound |
|---|---|---|
| 0 sing / 4 re-sung | 6 crystal glints at the crystal; the song loop starts | `pylon.sing` (loop, at most 4) |
| 1 hit | 5 crystal chips (the pylon's block particles) and 2 glints | `pylon.crack`, higher and sharper as its resonance runs out |
| 2 silenced | The crystal cracks dark (block state): 6 chips and 8 glints falling; ripped out also throws 4 dust puffs; the loop stops | `pylon.silence` |
| 3 regrow tell (60 ticks) | Glints rising at the crystal every 3 ticks, motes drawn into it every 6 | `pylon.regrow` (rising whine) |
| 5 shatter | 6 chips, then 10 white shards streaming from the crystal into his chest | `pylon.shatter` |

### 11.7 Yield, leash, echo, endings, the hoard

| Effect | Look | Sound |
|---|---|---|
| `boss_yield` | He kneels, the blade retracts; 5 crystal motes sink and dim (steady); 12 specks of dust settle off him | `boss.collector.yield`; the music drops to the drone (B) |
| `boss_leash` 0 / 1 | A puff of 14 smoke wisps where he was; 10 smoke wisps and 8 specks of settling dust where he reappears at the dais | `boss.collector.leash` (the reappearance lower) |
| `boss_echo` 0 form (60 ticks) | Smoke gathers from the floor around the dais into his shape, 2 wisps a tick; the renderer fades him in | `boss.collector.echo_form` |
| `boss_echo` ambient | Slow smoke breaks off his outline every 3 ticks | |
| `boss_echo` 1 dissolve (40 ticks) | He breaks into slow smoke that drains down into the floor; the renderer fades him out | `boss.collector.echo_fade` |
| `collector_resolve` 0, 3, 5 | The binders lock (a few poofs at his wrists), the CUFFED pose, the escort walks him out | `boss.collector.binders` |
| `collector_resolve` 1 | A last steady amber glow where his blade lies on the dais; he walks out | `boss.collector.walk_out` |
| `collector_resolve` 2 | At the stroke (12 ticks): poofs at his feet and settling dust; he falls forward over 8 ticks; his amber blade's light rolls a block and a half to his side and gutters out (steady fade with flashing off). No gore | `boss.collector.execute` at the stroke |
| `collector_resolve` 4 | A little dust off him as he rises; he walks out | `boss.collector.walk_out` |
| `hoard_open` | The vault seal crumbles (16 seal-block chips and 6 dust puffs at the seal); 9 light motes rise from the three holocrons on the plinth | `hoard.open` |

### 11.8 Loops and their stop rules (`BossLoops`)

| Loop | Plays | Stops |
|---|---|---|
| `boss.collector.ward_hum` | on Vasch (hostile, linear attenuation) while the ward level is above 0 in phase 2 | ward level 0, Unsung, the yield, phase not 2, he is removed, dies or kneels; `boss_phase` yield, reset, victory and music stop; no ward event for 200 ticks while no known pylon sings (watchdog) |
| `pylon.sing` | on each singing pylon (blocks, linear), at most 4; restarted each tick for a known pylon whose synced crystal still sings once the player is back within 48 blocks or back in the hall | the silence and shatter events; its crystal no longer SINGING in the synced block state (watchdog) |
| both | | the local player's death, farther than 48 blocks, the participant's music-stop event (then off until the next phase sync), level or dimension change, disconnect |

The music (three streamed loops, the drone, `ClientConfig.bossMusic`, holding off vanilla music) was B's
`BossMusic`, driven by the same `boss_phase` events. Since Milestone 5 it is `BossMusicService`, driven by the
`boss_music` payload the session sends with each phase event.

### 11.9 Particle budget at Standard

| Source | Peak |
|---|---|
| Surge gather + release (+ one clash) | 75 + 76 (+ 8) = 159 (13.1: at most 160) |
| Ward haze, 4 pylons singing | about 40 live |
| Pylon currents, 4 pylons | about 55 live |
| Lash tell or ridge | about 45 live |
| Phase 3 ambient (holocron, fraying cloak) | about 10 live |
| Echo smoke | about 15 live |
| Pylon events, Grip, Lunge, Palm, Hurl | under 40 each |

The worst moment of phase 2 (ward, currents, a Lash, a pylon hit and four sentinels at the sentinel budget)
stays near 300; phase 3 with a Surge near 220. Both are well under the hall's 900.

## 12. Balance keys added to `collector`

Beyond section 14: `p1StringMin` 2, `p1StringMax` 3, `p1GapTicks` 7, `p3StringMin` 3, `p3StringMax` 4, `p3GapTicks`
7, `controlMultiplier` 0.5, `lungeCooldown` 100, `lungeInterruptStaggerTicks` 20, `gripBreakStaggerTicks` 20,
`spentLiftHeight` 1.2.

## 13. Commands and tests

Commands (operator): `/oldrepublic boss place <player>` (a site through `ReliquarySites.place`, else forced 24
blocks ahead with the entrance toward the player), `start <player>` (starts the fight now, or calls the echo at
a resolved heart), `phase <player> <1-4>` (through the transition; 4 yields), `reset <player>`.

`BossGameTests` (the lead registers the class): `heartStateMachine`, `heartNbtRoundTripYieldedResolvedAttempts`,
`resetAfter200TicksMovesItemsToAntechamber`, `healthScalesWithTwoParticipants`, `midPhaseOneJoinKeepsFraction`,
`nonParticipantDamageRefused`, `leashReturnsToDais`, `phaseThresholds`, `interruptsOnlyFrom8_4Causes`,
`pylonSilencedByEverySource`, `resingAtMostOncePerPhaseWhileAnotherSings`, `unsungDoublesDamageWindow`,
`surgeCoverByServerRays`, `gripUsesHoldsAndBreaksOn8OrPush`, `liftOnlyWhileSpent`, `peacefulRefusesFight`,
`resolveCallsHookWithOutcome`, `rematchHookOncePerDayWithEcho`, `unchainedValues`, and B's
`reliquaryEvaluateRejects`. They run sessions on the lead's `arena_24x7x24` fixture through
`BossArenas.openSession` (no heart block entity) with a recording hook sink, listed mock players and the
test seams `forceMove`, `overrideDifficulty` and `surgeExposure`.

Client steps `m3/BossClientSteps` (screenshots `oldrepublic_m3_50` to `_59`): 50 a Watcher waking beside a Tomb
sentinel in its slam tell (on a stage at Y 240); then the Reliquary is forced 32 blocks ahead
(`ReliquarySites.placeAt`, area force-loaded) and the fight is driven through its seams: 51 the threshold, 52
the Archivist in a Hurl tell, 53 the Singing Wall, 54 a silenced pylon, 55 Unsung, 56 the Surge gather from
behind a column, 57 the yield, 59 the hoard opening and 58 the custody scene (Spare, or Bind on the Sith path).
Every shortcut is logged.

## 14. Limits and deviations

- Not compiled here. `// API-CHECK:` marks: `ItemEntity` teleport keeping owner and pickup delay; the renderers'
  `scale` running after the model flip (Vasch and the sentinels); `SlabType.TOP` in the Surge cover test.
- The cloak lifts and streams out on the inrushing air instead of streaming forward: a single cloak plane hung
  behind him cannot swing forward without passing through his body and head.
- The away timer counts only outside the whole Reliquary box, so a participant resting by the antechamber bed
  stays listed.
- The echo forms at the dais; its form event is sent around the dais (nobody tracks an entity on the tick it
  spawns).
- On the Sith Spare walk-out the blade is shown (a steady glow on the dais), not left as an item.
- `hoard_open`'s direction points from the plinth to the vault seal, so one event places both the crumble and
  the motes.

## Ruin Sentinels

Spec 7.7, 7.8, 13.3. W5 part B. Package `com.oldrepublic.enemy.construct` (server and common) and
`client.boss.sentinel` (client). Numbers live in `SentinelBalance` (section `sentinel`); the defaults match spec 7.7 and 14.

### Classes

| Class | Role |
|---|---|
| `ConstructFeature` | Init, called first from `BossFeature.init()`: entity type and attributes, spawn egg, the `lumen_spark` particle, resistances, the `sentinel` command. |
| `SentinelEntities` | `oldrepublic:ruin_sentinel` (`MONSTER`, 1.1 x 2.3, eye 2.0, tracking range 10, no `notInPeaceful`), attributes, `ruin_sentinel_spawn_egg`. Nothing spawns naturally. |
| `RuinSentinel` | The entity (`Monster`, `Aligned`): variant, dormancy and waking, the action state machine, the slam and the lance, the Peaceful crumble, NBT. |
| `SentinelVariant` | `WATCHER, TOMB` (frozen; append only). Saved as `Variant` (lowercase name). |
| `SentinelSlamGoal`, `SentinelLanceGoal` | When to start each attack; the lance goal runs above the slam goal and takes over at range. |
| `SentinelReactions` | Resistances through the shared hooks (Force reactions, damage modifier, control-effect duration). |
| `Sentinels` | `wakeNear` and `spawn` (frozen, 15.1), `spawnArchive` and `isSentinel` (W5 plan 4.6). |
| `SentinelEffects`, `SentinelParticles` | The five effect ids; the `lumen_spark` particle type. |
| `SentinelCommands` | `/oldrepublic sentinel spawn <watcher|tomb> <pos> [bound]` (operators). |
| `client.boss.sentinel.*` | `RuinSentinelModel`, `RuinSentinelRenderer` (2.3 blocks, skin plus an additive full-bright lumen layer), `RuinSentinelRenderState`, `SentinelFx`, `LumenSparkProvider`, `SentinelClient`. |

### Rules

| | Rule |
|---|---|
| Variants | **Watcher**: 48 health, speed 0.18, pale stone, one face slit, pale blue lumen. **Tomb**: 60 health, speed 0.16, dark basalt, bronze shoulder spikes, red lumen. Both armour 8, knockback resistance 0.6. |
| Allegiance | `ANCIENT`; boss sentinels (`Sentinels.spawnArchive`) are `ARCHIVE` like Vasch, so `Allegiances.friendly` keeps their lances off him. |
| Dormant | Stands as a statue at its post (`Post`, `PostYaw`) and only checks for waking every 10 ticks. Wakes when a player comes within `wakeRange` 6 (4 with `quiet_step`, through W3's `SkillPassives.sentinelWakeRadius` and `skill.quietStepWake`), when it is hit, or through `Sentinels.wakeNear`. Touching a pedestal never wakes it. |
| Sides | A Watcher stays dormant for a player at alignment >= +15 (`dormantAlignment`) and bows its head while one is within 6 blocks; a Tomb sentinel stays dormant for a player at <= -15. Both still wake when attacked by that player. `wakeNear(level, pos, radius, cause)` never wakes a sentinel that sleeps for `cause`, and returns how many woke. |
| Bound | `Bound: 1b` (mission, contract and boss spawns): sleeps for no one, always hostile (`Attitudes` and `countsAsHostile` answer hostile for any player), optional `Owner`. |
| Attitude | An `Attitudes` rule (`ConstructFeature`): a dormant, unbound sentinel is `NEUTRAL` to every player (a statue: a holocron pedestal beside it opens, and area powers pass it by); awake, or bound, it keeps the `Monster` default, `HOSTILE`. |
| Troopers | `Troopers.registerBoundCheck` (bound sentinels: fought on sight) and `registerDormancyCheck` (a dormant one is left alone) (W4's 5.2 rule). |
| Peaceful | Dormant sentinels never wake for players (proximity and `wakeNear` both refuse). A bound sentinel with an owner crumbles when its owner comes within 16 blocks: "The binding has failed." (overlay, `boss.oldrepublic.sentinel.binding_failed`), the death effect without drops, and `QuestHooks.onConstructKilled(owner, ...)` so a combat objective completes. Nothing is removed by Peaceful itself. |
| Rest | `isPreventingPlayerRest` is false while dormant, so a bed near a ruin works. |
| Wake | `WAKING` for `wakeTicks` 20: the lumen ignites, stone grinds, dust sheds; then it targets the player who woke it. |
| Back to sleep | Out of a fight for 200 ticks, any sentinel but a boss sentinel walks back to its post and sleeps; if it cannot path home within 800 ticks, its post moves to where it stands. |
| Slam | Within 3 blocks, off cooldown: `SLAM_TELL` 20 ticks (arms rise, dust sheds), then 10 damage and knockback 0.6 to everything hostile in a 90 degree arc out to 2.5 blocks past its edge, then `EXPOSED` 40 ticks (damage taken x1.5), cooldown 60. |
| Lance | A visible target 6 to 20 blocks away, off cooldown: `LANCE_TELL` 24 ticks (the sentinel stands and tracks, turning at most 12 degrees per tick), then one heavy `BlasterBolt` of `Style.SENTINEL`, 7 damage, speed 1.6, cooldown 80. Only a parry returns it (10 damage to the sentinel and `STAGGERED` for 20 ticks; inside the Reliquary a parried lance goes toward the crosshair). A plain guard blocks it for 35 stamina; an NPC that deflects only blocks it (35). See `docs/design/saber.md` 20 and 21. |
| Interrupts | A stagger or any wind-up interrupt (Force reactions) cancels a tell into `RECOVER` (10 ticks). |
| Resistances | Push knockback x0.2 (`#push_resistant` plus its own handler); Lift raises it at most 1.2 blocks; Force Lightning x1.5 and shock lasts x2 (overload); blaster bolts x0.5 (its own parried lance always hits for exactly 10); sabers x0.85; immune to fall, drowning, poison and wither. |
| Drops | Loot table `entities/ruin_sentinel`: `durasteel_plate` 40% (1-2), `lumen_thread` (Watcher) or `grave_silk` (Tomb) 20%. 10 XP (under the 80 cap). Worldgen garrisons never respawn. |
| Saving | NBT `Variant`, `Bound`, `Owner`, `Dormant`, `Post`, `PostYaw` (a numeric `Variant` and any case are read; unknown reads as Watcher). Persistent; `removeWhenFarAway` is false. Boss sentinels are never saved. |

### VFX and SFX, per phase of each effect

All are sent `toTracking` the sentinel with its id as `entityId`. Tell particles go through `Vfx.tellCount` and are never
thinned by the particle preset; everything else goes through `Vfx.count`. With flashing effects off, the lumen never
sputters or flickers (it glows steadily). The lumen layer of the renderer follows the synced action: dark while dormant, igniting
through the wake, brightening through the lance tell, full while the core is exposed, low while staggered.

| Effect | Variant / phase | Sent when | VFX (client, `SentinelFx`) | SFX |
|---|---|---|---|---|
| `sentinel_wake` | 0 wake | the wake starts (pos = lumen, intensity = tell ticks) | The lumen ignites in sputtering `lumen_spark`s that steady over the wake; stone dust (`falling_dust` of calcite or smooth basalt) sheds from the shoulders every other tick for the whole wake; the model rises out of the statue pose with a grinding tremor. | `sentinel.wake`: a stone grind and a rising glassy hum. |
| `sentinel_wake` | 1 bow | a dormant Watcher bows for a Light player (pos = head) | The head lowers over 10 ticks; a little dust falls from the head. | `sentinel.wake`, soft and high-pitched. |
| `sentinel_slam` | 0 tell | the slam tell starts (intensity = tell ticks, dir = facing) | Arms raised overhead, leaning back; dust sheds from the raised forearms, alternating sides (tell particles). | `sentinel.slam_tell`: a heavy grind and a bronze creak rising. |
| `sentinel_slam` | 1 impact | the blow lands (pos = arc centre, dir = facing, intensity = radius) | Irregular dust puffs and ground debris (the block under each point) of varied heights across the 90 degree arc: 9 angular slots with two or three left empty, so it never reads as a ring; a short pressure push at waist height. About 20 particles at Standard, within the slam's 35 with the tell. A small camera kick close by (`CameraShake`, 0.3 at the centre, fading out to the radius plus 4 blocks). | `sentinel.slam`: a deep stone impact, a crunch and a debris spray. |
| `sentinel_core` | - | the core is exposed (pos = lumen, intensity = ticks) | The chest plates swing open (the model) and lumen specks drift steadily from the open chest every 4 ticks for 40 ticks. No flashing. | `sentinel.core_exposed`: plates grinding apart, the core's hum rising. |
| `sentinel_lance` | 0 charge | the lance tell starts (pos = lumen, intensity = tell ticks) | Arms drawn back, chest forward, plates half open; motes are drawn from around the chest into the lumen, faster as the tell closes (tell particles). The lumen brightens. | `sentinel.lance_charge`: an intake of air under a rising glassy whine that peaks at the shot. |
| `sentinel_lance` | 1 fire | the bolt is away (pos = muzzle, dir = shot) | An amber-white burst at the muzzle and a wisp of smoke; the bolt itself is drawn by the enemy feature's `SENTINEL` bolt style. | `sentinel.lance_fire`: a heavy discharge with a crystal crack. |
| `sentinel_death` | variant = `SentinelVariant` ordinal | death, or a Peaceful crumble | The lumen gutters out in falling sparks; the body breaks into stone and bronze (raw copper) block debris; dust keeps falling for 20 ticks while the model slumps. | `sentinel.death`: the hum gutters and falls while the body crumbles. |
| (hurt) | - | vanilla hurt sound | none beyond vanilla | `sentinel.hurt`: a stone crack and a bronze knock. |

### Tests

`SentinelGameTests` (arena fixture): `sentinelVariantsAndNbt`, `sentinelDormantForOwnSideAt15`,
`wakeNearNeverWakesOwnSide`, `boundAlwaysHostile`, `lanceReturnedOnlyByParry`, `parriedLanceAimsAtCrosshairInArena`.
Each reads the difficulty and checks the Peaceful branch instead when the test server runs in Peaceful.

## Reliquary placement

Spec 3.6, 8.2. W5 part B: `boss.ReliquarySites` (frozen `place` and `evaluate`, 15.1), `boss.ReliquaryBuild`,
`tools/assets/gen_structures_reliquary.py`.

### Templates

- `boss/reliquary_surface`, 17x16x17: a watchtower stump. Its lowest 9 layers (the spiral descent) are set into the ground
  (`SURFACE_BURIED`), its top 7 stand above. The door is the template's north side, turned toward the player's approach.
  Anchor `DESCENT_TOP`.
- `boss/reliquary_hall`, 31x18x31, directly below: its top layer is the row under the surface's lowest layer (stand height
  minus 10, so its top face is at stand height minus 9), and its descent column (24, 25) lies under the surface's centre
  column (8, 8), so the spiral runs on through both templates into the antechamber. Both templates use the same rotation
  about `BlockPos.ZERO`, no mirror, no entities.
- Hall layout (template-local, x east, z south, floor heights relative to the hall floor):
  - z 0: north wall.
  - z 1-5: the raised platform (+3). Wings x 6-9 (`PYLON_1`) and x 21-24 (`PYLON_2`); the dais x 10-20 with the
    lectern, shelves and Vasch's spot (`DAIS`, `LECTERN`). The hoard vault is sunk inside the dais (x 11-19, z 1-4,
    interior 9x4x3) with its vault seal in the dais front (x 14-16, z 5; `VAULT_SEAL`, `PLINTH` inside).
  - z 6-19: the hall floor: a sunken floor 15x14 (x 8-22, -1) with six broken 2x2 columns (`COLUMN_1`-`6`) and four
    debris piles (`DEBRIS_1`-`4`); wing stairs at z 6-8; open undercrofts under the galleries.
  - x 1-5 and 25-29: the galleries (+5) from z 1 to 16, two sentinel alcoves each (`SENTINEL_1`-`4`, anchors
    `GALLERY_W`, `GALLERY_E`); the south balcony (+5, z 17-19) joins them, and P3 and P4 (`PYLON_3`, `PYLON_4`) stand at
    the galleries' south ends; gallery stairs x 6-7 and 23-24 climb south to the balcony.
  - z 20: the threshold wall; the door seal (x 14-16, +5, `DOOR_SEAL`, `THRESHOLD`) opens from the balcony into the
    antechamber.
  - z 21-29: the antechamber 9x9 (x 11-19, +5): vanilla bed (`BED`), the heart (`HEART`, set last by the build), a rack
    of emptied cradles. The spiral descent arrives through its east wall from the shaft at x 21-27, z 22-28.
- Light: crystal sconces (amethyst clusters on brackets with a vanilla light block, level 7, beside each); nothing
  flickers. No floor circles, sigils or runes. The generator self-checks the anchors, walkable floor, the threshold
  plane and the light, and writes byte-identical files on every run.

### Choosing a site (`ReliquarySites.place(owner, origin, forward)`)

1. Rings from balance section `chapter_three` (W2's), read by name: `reliquaryRings` 96, 128, 144; if none of those
   yields a site, `reliquaryFallback` 48, 80. Each ring is capped at 144 (`MAX_RING`, 3.6), so a config edit cannot
   push a ring past the loaded area. 16 candidates per ring, starting along `forward`.
2. Each candidate must pass M2's `MeetingSites.evaluate` with the stump's rules: a 13x13 footprint of natural ground,
   a 19x19 clearance, slope at most 3, spawn protection (overworld, when the story balance respects it) and
   `story.siteMinSeparation` from the other players' story sites and runtime sites.
3. The surface: its box and clearance loaded, no worldgen structure piece in the clearance, no `#built_blocks` in the
   box.
4. The hall plus its 2-block shell must pass `evaluate` (below) with the production predicate `worldgenPieces`, and lie
   under ground everywhere (the motion-blocking height, sampled every 3 columns, stays above the shell), so no ravine or
   cliff opens into it.
5. Of the passing candidates the best M2 score wins (mean height against the ring's average, plus 4 x openness), over
   all rings of the list. The stump is turned so its door faces the search origin.
6. None: `place` logs a warning and returns null; the caller (W2's chapter service) retries every 1,200 ticks. There is
   no forward fallback. `placeAt(owner, stand, rotation)` forces a placement with no site checks (commands and client
   steps; it throws when a chunk is not loaded).

### `evaluate(level, hallAndShell, isWorldgenPiece)`

Null when accepted, else a lang-free reason, checked in this order:

| Reason | Refused when |
|---|---|
| `unloaded` | any chunk the box touches is not loaded |
| `too_deep` | the box reaches below `minY + 8` (exactly `minY + 8` is allowed) |
| `worldgen_piece` | `isWorldgenPiece` holds for a chunk: it is asked once per chunk the box touches, with the box's lowest corner clipped into that chunk. The production predicate reads that chunk's structure starts (`getAllStructuresAt`, `startsForStructure`) and tests every piece's bounding box against the chunk's slice of the box, so a mineshaft corridor crossing the interior is caught. |
| `built_blocks` | the box holds a `#oldrepublic:built_blocks` block |

### The paced build (`ReliquaryBuild`)

The surface is stamped at once (recorded in `WorldSites` by `RuntimeSites`). The hall follows over the next ticks:

1. tick 1, the shell: every air, replaceable or fluid cell of the 2-block shell outside the hall and the surface becomes
   stone (deepslate below y 0) with `Block.UPDATE_CLIENTS` only, so no fluid ticks are scheduled; a cell touching a
   `#built_blocks` block is left alone;
2. ticks 2-5, the hall template in 4 Y-slabs, bottom first (waterlogging ignored);
3. tick 6, the heart, facing the template's north turned by the rotation; then the hall's `WorldSites` record (kind,
   origin, rotation, owner, day) and `BossArenas.onReliquaryPlaced(level, heart, owner, hall)`.

A build waits while any chunk of the hall and its shell is unloaded, and is dropped if its dimension is gone. Builds
still running when the server stops are finished on the spot when their chunks are loaded. Jobs are not saved, but
`ReliquarySites.resumeUnfinished` (on `SERVER_STARTED`) finds any owned Reliquary surface record with no hall record
under it and queues its hall again from the shell; every step is safe to repeat, and the hall record is written only
when the heart is set.

### VFX and SFX

Placement itself has no effect events: the build happens out of sight, at least 48 blocks away, under ground. The
Reliquary's presence is carried by `ambient.reliquary` (a 16-second crystal-drone bed, requested from W1's site
ambience) and by the hall's static light; the seals' opening is the heart's (`hoard_open`, `hoard.open`).

### Tests

`BossGameTests.reliquaryEvaluateRejects` (W5 part B, merged from the side file): a clear box is accepted and the
predicate is asked once per chunk with the clipped corner; `worldgen_piece`; `built_blocks`; worldgen before built
blocks; `too_deep` and its edge; `unloaded`, which comes before depth.
