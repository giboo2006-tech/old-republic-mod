# Foes feature design, Milestone 4: the Mandalorian hunter, the security droid, the beasts, Torvin Kaast

Owner: W6 (Force and foes). Packages `com.oldrepublic.enemy.{mandalorian,droid,creature}`, `enemy/FoesFeature` and
`com.oldrepublic.boss.kaast` (common); `com.oldrepublic.client.enemy.{mandalorian,droid,creature}`,
`client/enemy/FoesClient` and `com.oldrepublic.client.boss.kaast` (client); `mixin/foes/MusicManagerKaastMixin`
(since Milestone 5, `BossMusicService` and `mixin/boss/MusicManagerBossMusicMixin` play the music).
Spec: `docs/MILESTONE_4.md` sections 10.1 to 10.7, 3.1 and 3.4 (spawns, the hunting camp), 13 (the foes' effects,
sounds and music), 14 (`mandalorian`, `security_droid`, `creatures`, `kaast`), 16 (ids) and 17 (`FoesGameTests`,
screenshots 63 to 69). The powers they meet are in `docs/design/force_m4.md`; assets in `docs/assets/foes.md`. W2 owns
their story uses and rewards (`quest/story`), W1 the camp template and the site spawners.

No feature was cut: the seeker droid and Rattle (cut item 8 territory) are in, as are both beasts and their spawns.

API status: verified against the 26.2 dumps or used as lead code uses them, except the lines marked `// API-CHECK:`
(section 12), each probed in `tools/api-probe/m4_w6.txt`.

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Entities (10) | `mandalorian_hunter`, `hunter_rocket`, `hunter_net`, `security_droid`, `seeker_droid`, `hollowhorn`, `ash_hound`, `torvin_kaast`, `kaast_droid` (Rattle), `camp_mine` | `HunterEntities`, `DroidEntities`, `CreatureEntities`, `KaastEntities` |
| Items (W6) | `raw_hollowhorn`, `cooked_hollowhorn` (food), spawn eggs `mandalorian_hunter_`, `security_droid_`, `hollowhorn_`, `ash_hound_spawn_egg` | `CreatureItems`, the entity classes |
| Items (lead's `M4Items`, W6 art and drops) | `hollowhorn_hide`, `hollowhorn_horn`, `ash_hound_fang`, `droid_actuator` | loot tables below |
| Block | `camp_gate` (properties `open`, `top`) | `KaastBlocks`, `CampGateBlock` |
| Damage types (4) | `wrist_flamer`, `mandalorian_rocket`, `camp_mine`, `jet_slam` | `HunterDamage`, `data/oldrepublic/damage_type/` |
| Effect events (5) | `hunter_tell` (0 jet, 1 flamer, 2 rocket, 3 net, 4 charge, 5 axe), `hunter_attack` (0 jet, 1 flame, 2 rocket, 3 net), `droid_tell` (0 spin, 1 shield, 2 death), `beast_tell` (0 hollowhorn charge tell, 1 hound lunge tell, 2 howl, 3 hollowhorn run, 4 hollowhorn impact, 5 hound leap), `kaast` (0 phase, 1 mine arm, 2 mine live, 3 slam tell, 4 slam, 5 yield, 6 helmet, 7 mine sensed, 8 music, 9 clash, 10 echo, 11 burn off) | `HunterEffects`, `DroidEffects`, `BeastEffects`, `KaastEffects` |
| Sounds (47) and music (3) | `mandalorian.*` (12), `droid.*` (7), `hollowhorn.*` (6), `ash_hound.*` (6), `boss.kaast.*` (16), `music.kaast.{hunt,trap,last_stand}` | Phase 0 `ModSounds` names; `docs/assets/foes.md` |
| Loot tables | `entities/{mandalorian_hunter,security_droid,seeker_droid,hollowhorn,ash_hound,torvin_kaast,kaast_droid,camp_mine,hunter_rocket,hunter_net}`, `chests/kaast_stash` (camp dressing) | `data/oldrepublic/loot_table/` |
| Balance sections (4) | `mandalorian`, `security_droid`, `creatures`, `kaast` | `HunterBalance`, `DroidBalance`, `CreatureBalance`, `KaastBalance` |
| Command | `/oldrepublic kaast camp|phase|reset <player>` (`phase` takes 1 to 3) | `KaastCommands` |
| Hub spawner role | `droid`: the Proving's patrol droid (`Droids.spawnHubPatrol`), registered after W1's placeholder so it replaces it | `FoesFeature` |

Frozen and append-only: every effect variant number above, `MandalorianHunter.GADGET_*` and `AMBUSH_*`,
`Hollowhorn.STATE_*`, `AshHound.STATE_*` (synced data values), the hunters' four paints (saved index).

## 2. Shared wiring (`FoesFeature`)

Registers the entity types, items, spawns, the Force reactions (`HunterReactions`, `DroidReactions`,
`CreatureReactions`, Kaast's `KaastReactions`), the droid's `DamageModifier`, the attitude rules (droids and
hollowhorns), the hub spawner role, `Netting` (ticked, cleared on server stop) and `KaastFeature`. `FoesFeature.killer`
names the player credited with a kill (the source's owner, else the last player who hurt it).

## 3. Mandalorian hunter (10.1)

- 40 health, armour 10, speed 0.28, knockback resistance 0.2; one of four seeded worn paints; T-visor helmet, jet pack,
  wrist gauntlets, a rifle. Allegiance HUNTERS (shared with escort droids and Kaast), so hunters, their droid and Kaast
  never shoot each other, and poacher crews no longer count as their friends. Troopers fight CREW on sight but not
  HUNTERS (`TrooperEntity`): they answer a hunter only when it attacks them or someone they defend.
- `HunterCombatGoal`: it keeps 8 to 18 blocks off. Every attack has its tell, ended in the tick it acts:
  - rifle: a 10-tick aim (`isCharging`, the rifle shouldered), then a burst of 2 gold bolts 5 ticks apart (6 damage, speed 2,
    lead 0.75 of the target's motion, 1.0 against a Force Speed runner); 40 ticks between bursts;
  - wrist flamer: under 4 blocks, a 14-tick tell (wrist up, pilot flame, hiss), then a 30 degree, 3-block cone for 30
    ticks, 2 damage every 5 ticks and 3 s of fire; cooldown 120; a Barrier halves it; a saber guard cannot block it
    (no source position);
  - jet hop: every 200 ticks or when the target closes inside 5 (at least 60 apart), a 20-tick tell (rising whine,
    flame flicker), then a 16-tick arc of height 3 to a spot 6 to 10 blocks away, high ground preferred, in sight of
    the target (`findHopSpot`); a Push in the air knocks it down (`onPushedInAir`), staggered 40 ticks on landing;
  - wrist rocket: every 300 ticks at 8 to 20 blocks, a 24-tick tell (beep, a small red dot on the target), then a slow
    rocket (0.55) for 7 damage in radius 2. Not saber-deflectable (a guard drains 30 instead); Push sends it back at
    0.9 owned by the pusher; it bursts on a Barrier.
- Low on health it runs for cover and holds there for 40 to 70 ticks (cooldown 240).
- Story hunters are owned (`StoryTags`) and grouped. The last of a story ambush yields at 20% (`Yieldable`, dialogue
  `c4_hunter_yield`); bounty hunters, Kaast's called hunters and lone unescorted hunters never yield. Owned hunters jet
  away when the owner is gone for 200 ticks or more than 160 blocks off.
- Drops (`mayDropLoot`): never from a fight spawn; from an owned hunter only when its owner killed it or was within 32
  blocks; unowned hunters drop normally. Loot: durasteel plate 30%, jet pack 5% (killed by a player). The hunter
  schematic piece is W2's bounty reward (see the report).
- `Hunters.spawnAmbush` (W2's ambushes and bounties: drop in from 14 blocks on the jets, a droid escort optional) and
  `Hunters.spawnForFight` (Kaast's call).

## 4. Mk IV security droid (10.2)

- 50 health, armour 12, speed 0.2, knockback resistance 0.5; keeps 6 to 16 blocks off.
- Volley: a 20-tick barrel spin and whir (`droid_tell` 0), then 8 bolts over 40 ticks (4 damage, speed 1.8); cooldown 80.
  A stagger or shock stops the spin; while staggered it does not fire.
- Resistances (`DroidReactions`, `SecurityDroid`): blaster bolts x0.5, a bolt a saber parried back x1.5, Force Lightning
  x1.5; immune to Choke (does not breathe), the wrist flamer, fire, poison and wither; Lift refused, Push carries it
  half as far.
- Below half health, once a life, a frontal plate for 100 ticks stops projectiles within 70 degrees of its facing
  (sparks where they strike).
- Dying: 20 ticks of sparks, then a small burst (2 damage, radius 1.5).
- Roles: an Imperial guard (crimson bolts; not hostile to Sith-path players unless provoked), or a hunter's escort
  (gold bolts; its death is clue 3 for W2). The Proving's patrol (`spawnHubPatrol`): an Imperial guard, hub-tagged,
  kept to a 12-block beat. Drops: durasteel and redstone, an actuator 25%.

## 5. Hollowhorn (10.3)

- 30 health (bull 45), speed 0.25; herds of 3 to 5 with a bull and sometimes calves (35%, scale 0.6).
- Neutral. Hit one, or come within 6 blocks of a calf (survival players), and every grown, uncorrupted hollowhorn
  within 16 blocks turns on you for 400 ticks. Calves flee and never charge.
- `HollowhornChargeGoal`: at 4 to 14 blocks with a clear line, a 20-tick tell (head down, snort, hoof scraping clods),
  then a straight charge at 0.6 blocks a tick for 8 damage and knockback 1.6; into a wall it is stunned 50 ticks and
  takes x1.5 damage; cooldown 80. Up close, a 4-damage butt.
- The corrupted variant is the tag `oldrepublic.corrupted` (Heartstone waves, corrupted waystones): always hostile,
  never a calf, no drops, dark veins.
- Drops: hide, raw meat (smelted when burning), a horn 20%. Raw meat cooks in a furnace (200 ticks), smoker (100) or
  on a campfire (600), 0.35 XP (`recipe/cooked_hollowhorn*.json`).

## 6. Ash hound (10.4)

- 18 health, speed 0.35, bite 3; fire immune; packs of 3 or 4.
- `AshHoundPackGoal`: the first to see a player howls and calls every hound within 24; one holds in front while the
  others circle to the flanks (110 degrees off the front, 5 blocks out). Each lunges in turn after a 10-tick crouch and
  growl, from 6 blocks or less, for 5 damage; 50 ticks between lunges, packmates' first lunges spread out.
- A parried lunge, a Barrier pulse, a Push or any interrupt during the lunge turns it aside and staggers it 30 ticks.
- Drops: a fang 60%, bones.

## 7. Spawns (3.1)

`CreatureSpawns` adds the natural spawns with `BiomeModifications` on W1's planet biome tags: hollowhorn herds in the
Selvaren meadows (rarely the highwood), ash hound packs in the Varkhast redrock and, more often, the ashfall. Hounds
spawn only at dusk and night (always in the ashfall) and never within 64 blocks of the Proving
(`CreatureRules.houndMaySpawn`). Hunters and droids are placed by W1's site spawners and W2's story code, never
naturally.

## 8. Torvin Kaast and the camp fight (10.5, 10.6)

- **The camp.** `HuntingCamps.place` puts W1's `boss/hunting_camp` template through `PlanetSites` on the owner's path
  world (Selvaren for Jedi, Varkhast for Sith, the current planet for an Independent) and fits `camp_gate` in its
  `GATE` opening. With `kaast.placeCamp` false only `/oldrepublic kaast camp <player>` places one. `CampLayout.of`
  reads the anchors (the heart, four nests, six mine spots, the gate); a camp without them gets fallback nests and mines.
- **The start.** During DUEL, the owner entering the ring (20 blocks round the heart, 12 up or down) starts a
  `KaastSession` (not on Peaceful). The gate closes; a listed participant who uses it is let through, anyone else is
  refused. After a restart with the duel already yielded, Kaast comes back knelt.
- **Participants.** Anyone playing inside the ring is listed (the M3 co-op rules); one out of it for 100 ticks leaves
  the list. Health 500 x (1 + 0.6 per extra participant, at most 4 counted); mines 6 + 2 and called hunters 2 + 1 per
  extra participant.
- **Phase 1, the Hunt** (above 65%): he holds the nests, hopping between them (30-tick tell) every 160 ticks; rifle
  bursts of 3; a wrist rocket every 300 ticks; the flamer for anyone who climbs up. Rattle circles his target and
  scans it: while scanned his bolts deal +25%. A Push or a returned heavy bolt on a nest knocks him off: he falls, is
  staggered 40 ticks (Lift can raise him 1 block then), and hops back.
- **Phase 2, the Trap** (65% to 30%): Rattle leaves; he comes down into the pit, walks to each mine spot and kneels 30
  ticks to set a mine (a heavy blow while he kneels interrupts him); mines blink for 40 ticks, then anyone not on his
  side within 1.5 sets one off (8 damage); Push, Lift, a shot or a blow sets one off from range. A net every 400 ticks
  (20-tick tell) roots a player for 60 ticks or 3 saber hits (`Netting`: Speed, Barrier and jet pack bursts end or are
  refused, only Push is allowed, and Push breaks it). At 50% he calls his hunters (owned fight spawns: never yield,
  never drop).
- **Phase 3, the Last Stand** (below 30%): the helmet cracks and the vibro-axe comes out; Rattle comes back unless it
  was destroyed. Three-blow combos (8, 8, then 12 after a 10-tick wind-up; parrying the third staggers him 30), a
  shoulder charge (14-tick tell, 8 damage), one jet slam at 20% (40-tick rise, 12 damage in radius 3), the rifle at
  range. The axe blocks sabers from the front with its own guard (120, drained 3 per damage); a Barrier does not
  soften it (`BarrierPiercing`). When his one Choke ends he burns it off: a short gout of the flamer at the caster, who
  is staggered 30 ticks.
- **Phase changes** take 60 ticks in which he takes no damage. Control effects on him are 40% shorter (not the Choke's
  hold); only the listed interrupts touch him (`KaastRules.interrupts`): a bolt parried back (20 ticks), a returned
  heavy bolt, the Push off a nest in phase 1, a heavy blow while he kneels at a mine, a parried third combo blow.
  Choke holds him once, in phase 3, for 20 ticks.
- **The yield.** Nothing kills him: at 10% he kneels, takes his helmet off and yields; the owner's choice opens (W2,
  `QuestHooks.onBossYielded`). `KaastFight.resolve(owner, outcome)` (W2's `kaast:resolve`) plays the scene (spared: he
  jets away; stripped: he walks out; ended: a short end) and calls `onBossDefeated` for each participant.
- **Reset.** Nobody in the ring for 200 ticks (or `/oldrepublic kaast reset`): mines, nets, hunters and Rattle go, the
  gate opens, the bar and music stop, Kaast is removed; the next approach starts fresh. Nothing of a fight is saved;
  the server's stop clears every session (the gate reopens before the save).
- **The echo (10.6).** After COMPLETE the owner can use the open gate once per in-game day (`Clocks.day`) to call the
  hunter's echo: the same fight, pale ash as it forms; its yield ends it and pays W2's rematch rewards.
- **Music.** `music.kaast.hunt`, `trap` and `last_stand`, one per phase, crossfaded; stopped on the yield, a victory,
  a reset, leaving the ring, death, a level change, disconnect, the client toggle, and a 200-tick watchdog. Vanilla
  music is held off meanwhile.

## 9. Seeker droid and Rattle (10.7)

`seeker_droid`: a small flying probe for W2's Chapter Four opening (`SeekerScene` flies it). 4 health but
invulnerable, never saved, never hostile, no AI of its own; one left without its scene's tag shuts down. Rattle
(`kaast_droid`): the seeker's model in Kaast's paint, 20 health, flying, immune to Choke, the scan line and mark above.

## 10. Effects (13)

Every tell is drawn in full at every effects setting; nothing draws a ground circle, a halo or an outline (dust
spreads unevenly, with gaps).

- **Hunter.** Tells: the jet's rising whine with a flicker of flame at the nozzles; the wrist's pilot flame and hiss;
  the rocket's targeting beep and a small red dot on the target for the whole tell (the only red light on the field);
  the net launcher's click; Kaast's shoulder-charge scrape and the glint on his axe; the rifle shouldered for its aim. Attacks:
  the hop's gout of flame and smoke (W4's `flame_jet`), the flamer's cone and roar (it follows the synced flaming flag
  and stops the moment the flame ends, he is staggered or dies, or leaves this client), the rocket's smoke trail and
  smoky burst, the net's cords flying apart round the caught player.
- **Droid.** The spin-up's whir and a breath of hot air from the turning barrels; sparks and a clank where a bolt
  strikes the plate; the dying droid's sparking crackle until its burst. The sensor eye sweeps.
- **Beasts.** The hollowhorn's forehoof scraping clods for the whole tell, dust behind the hooves while it charges,
  a burst of dust and clods and a short close shake on the wall. The hound's ash puffs under the crouch, ash kicked
  back at the leap, a breath of ash with the howl.
- **Kaast.** The phase banner ("Torvin Kaast, the hunter" over the phase's name); the mine's spark and blink while
  arming, a puff when live, dust settling on live mines for a Sense user, the burst's flash, smoke and clods; the
  slam's jet flame while he climbs and uneven dust where he will land, then the landing burst; sparks when the axe
  blocks; grit from the cracked visor; dust settling as he kneels; pale ash for the echo.

## 11. Balance (14)

Each section is read through `BalanceConfig.section`; defaults are the spec's table.

- `mandalorian`: `maxHealth` 40, `armor` 10, `movementSpeed` 0.28, ranges 8 to 18, rifle (`rifleDamage` 6, `burst` 2,
  `burstCooldownTicks` 40, `burstGapTicks` 5, `rifleTellTicks` 10, `boltSpeed` 2, `lead` 0.75, `speedLead` 1), jet
  (`jetIntervalTicks` 200, `jetTellTicks` 20, 6 to 10 blocks, `jetFlightTicks` 16, `jetStaggerTicks` 40), flamer (`flamerRange` 4,
  `flamerTellTicks` 14, `flamerTicks` 30, `flamerDamage` 2 every 5, `flamerCooldownTicks` 120), rocket
  (`rocketIntervalTicks` 300, 8 to 20 blocks, `rocketTellTicks` 24, `rocketDamage` 7, `rocketRadius` 2,
  `rocketPushSpeed` 0.9), `yieldAt` 0.2, cover, `ownerLeash` 160, `ownerAbsentTicks` 200, `dropHeight` 14.
- `security_droid`: `maxHealth` 50, `armor` 12, `blasterMult` 0.5, `parriedBoltMult` 1.5, `shockMult` 1.5, `pushMult`
  0.5, `spinTellTicks` 20, `volleyBolts` 8 over `volleyTicks` 40, `boltDamage` 4, `volleyCooldownTicks` 80, `shieldAt`
  0.5, `shieldTicks` 100, `shieldDegrees` 70, `deathTellTicks` 20, `deathBurstDamage` 2.
- `creatures`: the hollowhorn (`hollowhornHealth` 30, `bullHealth` 45, `angerTicks` 400, `calfGuardRange` 6,
  `herdRange` 16, `chargeTellTicks` 20, `chargeMinRange` 4, `chargeRange` 14, `chargeSpeed` 0.6, `chargeDamage` 8,
  `stunTicks` 50, `stunDamageMult` 1.5, herds 3 to 5, `calfChance` 0.35), the hound (`houndHealth` 18, `biteDamage` 3,
  packs 3 to 4, `howlRange` 24, `flankRadius` 5, `lungeTellTicks` 10, `lungeRange` 6, `lungeDamage` 5,
  `lungeCooldownTicks` 50, `lungeStaggerTicks` 30, `houndHubExclusion` 64), spawn weights.
- `kaast`: health and scaling (`maxHealth` 500, `perParticipant` 0.6, `scalingCap` 4), `phase2At` 0.65, `phase3At` 0.30,
  `yieldAt` 0.10, `transitionTicks` 60, `controlShorten` 0.4, each phase's numbers as section 8, `resetTicks` 200,
  `participantLeaveTicks` 100, `placeCamp` true, and the reward keys W2 reads (`ownerXp`, `ownerSp`, `ownerCredits`,
  `resonance`, `helperXp`, `rematchXp`, `rematchSchematicPity`, `rematchCredits`).

## 12. Tests (17)

`FoesGameTests` (the spec's names plus one): `hunterTellsPrecedeAttacks`, `hunterJetHopToCover`,
`flamerConeAndBarrierHalves`, `rocketNotSaberDeflectableButPushable`, `hunterYieldDialogue`, `noFarmDropsFromBounties`,
`netEndsSpeedBarrierJump`, `droidResistsBlasterAndImmuneToChoke`, `droidShockWeakness`, `droidShieldOnceBelowHalf`,
`hubPatrolDroidSparesSith` (extra), `hollowhornNeutralUntilHitOrCalf`, `hollowhornChargeStunsOnWall`,
`ashHoundPackFlanksAndLungeParried`, `kaastPhaseThresholds`, `kaastChokeOnlyOnceInPhase3`,
`kaastInterruptsOnlyFromListed`, `kaastMinesArmThenLive`, `kaastHealthScaling`, `kaastResetAfter200`, `kaastYieldAt10`,
`resolveCallsHook`, `rematchOncePerDay`. All in the lead's `arena_24x7x24`; the walls, high ground and Kaast's camp
(a hand-made `CampLayout`) a test needs are built by the test inside the arena. Every test undoes what it started
(fights, runs, barriers, nets, foes, listed players) on every path and sweeps the arena of its foes and shots.

`m4/FoesClientSteps` screenshots: 63 the flamer tell, 64 the droid shield, 65 the hollowhorn's charge tell, 66 an ash
hound's leap, 67 to 69 Kaast in each phase (through `/oldrepublic kaast phase`) and 69 the yield; each confirmed by
the server; `/oldrepublic kaast reset` ends the fight.

## 13. API status

- `[U]` `AttributeInstance#setBaseValue` (hunter paints, droid, hollowhorn bull, hound, Kaast's scaling, Rattle; the
  lead's `CrystalPoacher` uses it the same way).
- `[U]` `Level#getHeight(Heightmap.Types, int, int)` in `findHopSpot` (as W2's `TheHunt`).
- `[U]` `DamageSources#source(key, direct, causing)` with a null direct entity for the flame (no source position).
- `[U]` `TargetingConditions.Selector` as `(LivingEntity, ServerLevel) -> boolean`.
- `[U]` whether `Mob#setHomeTo` is saved (the patrol droid is respawned daily anyway).
- `[U]` `FoodProperties.Builder` (the hollowhorn meat).
- Client `[U]`: `RenderTypes.lightning()` quads in POSITION_COLOR (the droid's eye and plate glow), the rocket's
  rotation convention, `LivingEntityRenderer#scale` after the model flip.

## 14. Known issues

- W2's `CreditDrops` pays chits for every hunter or droid killed in a hostile fight; it should ask
  `MandalorianHunter#mayDropLoot` and skip hub-tagged droids (requested).
