# Force feature design, Milestone 4: Force Speed, Force Barrier, Force Choke, Lift for the paths

Owner: W6 (Force and foes). Packages `com.oldrepublic.force.{speed,barrier,choke}` and `force/ForceM4Feature`
(common), `com.oldrepublic.client.force.{speed,barrier,choke}` and `client/force/ForceM4Client` (client), and the
`mixin/foes` packages of both source sets (`oldrepublic-foes.mixins.json`). Spec: `docs/MILESTONE_4.md` sections 9.1
to 9.5, 13 (the three powers' effects and sounds), 14 (`force_speed`, `force_barrier`, `force_choke`), 16 (ids) and
17 (`ForceM4GameTests`, screenshots 60 to 62). The M2 and M3 powers these sit beside are in `docs/design/force.md`;
assets are in `docs/assets/force_m4.md`. The enemies that react to the powers are in `docs/design/foes.md`.

No feature was cut. All three powers, every branch and passive of 9.5, and Lift for Jedi and Sith are in.

API status: every vanilla and Fabric call in these packages is verified against the 26.2 dumps or used the same way
in lead code, except the two marked `// API-CHECK:` and probed in `tools/api-probe/m4_w6.txt` (section 10).

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Powers (3) | `force_speed`, `force_barrier`, `force_choke` | `ForceSpeed`, `ForceBarrier`, `ForceChoke` (registered in `Abilities`) |
| Tree nodes (17, 9.5) | universal `force_speed`, `speed_burst`, `speed_long_road`; exploration `speed_wayfarer`; jedi `force_barrier`, `barrier_shelter`, `barrier_return`, `guardians_patience`, `lift_learned_jedi`; sith `force_choke`, `choke_crush`, `choke_dread`, `cruel_focus`, `lift_learned_sith`; saber `staff_whirl`, `lens_mastery` | `data/oldrepublic/skill_tree/*.json` |
| Effect events (5) | `force_speed` (0 start, 1 end, 2 burst), `force_barrier` (0 start, 1 end, 2 pulse, 3 break), `force_barrier_impact` (0 bolt stopped, 1 bolt returned, 2 other projectile, 3 blast, 4 melee), `force_choke` (caster: 0 start, 1 release, 2 crush), `force_choke_target` (1 held, 0 dropped) | the power classes |
| Particle | `oldrepublic:held_dust` (the Barrier's drifting dust and leaves) | `BarrierParticles`, client `HeldDustParticle` |
| Damage type | `oldrepublic:force_choke` | `data/oldrepublic/damage_type/force_choke.json`; `ForceChoke.DAMAGE` |
| Attribute modifier ids | `oldrepublic:force_speed`, `oldrepublic:force_speed_step` (the run), `oldrepublic:force_barrier_slow` (walking pace while the barrier is held), `oldrepublic:choke_gravity` (no gravity while held) | all transient |
| Balance sections (3) | `force_speed`, `force_barrier`, `force_choke` | `SpeedBalance`, `BarrierBalance`, `ChokeBalance` through `BalanceConfig.section` |
| Sounds (13) | `force.speed.{start,loop,end,burst}`, `force.barrier.{start,loop,impact,pulse,break}`, `force.choke.{start,loop,release,crush}` | the Phase 0 `ModSounds` names; files in `assets/oldrepublic/sounds/force/` |
| Interfaces for others | `BarrierReactive` (a projectile's own answer to the barrier: the hunter's rocket bursts, the net falls short), `BarrierPiercing` (Kaast's axe is not softened), `ChokeImmune` (Rattle, the seeker) | `force/barrier`, `force/choke` |
| Commands | `/oldrepublic force grant <id> [player]` gains the three powers and their nodes; `/oldrepublic force use <id> start|release|cancel` presses a power for the command's player | `ForceM4Feature.forceCommand` (merged into the lead's `force` command) |
| Mixins | `MobChokeMixin` (no AI while choked, beside the lead's `MobLiftMixin`), `LivingEntityChokeSoundMixin` (a choked creature's hurt sounds are muffled), client `LivingEntityRendererChokeMixin` and `HumanoidModelChokeMixin` (the held pose) | `oldrepublic-foes.mixins.json` |

## 2. Classes

- `ForceM4Feature`: registration (powers, the barrier and choke `DamageModifiers`, a `ForceReactions` handler for a Push
  on a choking caster, the parry hook for Guardian's Patience, server ticks, every end path: leave, death, respawn,
  level change, server stop), the "no attacks, no items behind a barrier" callbacks, the Lift path nodes
  (`tickLiftNodes`), and the `grant`/`use` subcommands. `forgetAll(player, feedback)` ends a player's run, barrier and
  choke at once.
- `speed/ForceSpeed` and `SpeedBalance`: the run (a state, never a channel), Burst, The Long Road, Wayfarer.
- `barrier/ForceBarrier`, `BarrierBalance`, `BarrierParticles`, `BarrierReactive`, `BarrierPiercing`: the channel and
  the projectile check.
- `choke/ForceChoke`, `ChokeBalance`, `ChokedTargets`, `ChokeImmune`: the channel; `ChokedTargets` holds creatures
  (players go through the lead's `Holds`).
- Client: `ForceM4Client` (event handlers, the particle factory, the per-player loops), `speed/FxKit` (shared dust and
  bent-air helpers), `speed/PowerLoop` (one looping sound per power and player, with every stop path),
  `speed/SpeedFx`, `barrier/BarrierFx`, `barrier/HeldDustParticle`, `choke/ChokeFx`.

## 3. Force Speed (9.1)

- A tap. Cost 20 FE (25 with The Long Road), minus 2 per mastery rank; cooldown 600 ticks, started when the run ends.
- The run lasts 160 ticks (300 with The Long Road), plus 20 per mastery rank, times W4's
  `SetBonuses.forceSpeedDurationMultiplier` (Hunter's set). Movement speed +40% (a transient `ADD_MULTIPLIED_BASE`
  modifier), step height +0.5, sprinting hunger halved. A sprint jump carries 20% further: the client moves its own
  player, so `SpeedFx` adds it on the client for the local runner only.
- A second cast ends the run early with no refund. The run also ends on a stagger, slow or shock (all apply slowness),
  a hold, a hunter's net, water, riding, death, disconnect and a change of level. It is not a channel
  (`isActive` stays false), so other powers can be cast during it.
- **Burst** (branch `speed_shape`): the first 20 ticks at +100%, and a dash of up to 6 blocks along the look over
  4 ticks that stops at the first wall (`dashDistance`, tested).
- **The Long Road** (branch `speed_shape`): the longer run above.
- **Wayfarer** (granted to Independents by W2's mission): while running, a projectile that would hit the runner within
  the next 10 ticks has a 25% chance to be turned aside by at most 1.5 degrees, never into a wall
  (`wayfarerPath`, `clearAsBefore`), and falls under 8 blocks do no damage (`allowDamage`).
- Mastery: a point for every 200 blocks run.
- Costs go through `ForcePowers.costMultiplier` (W4's set bonuses and the lead's modifiers), like every power.

## 4. Force Barrier (9.2)

- A channel: 15 FE to raise, 6 FE a second, at most 120 ticks, cooldown 400 ticks from the end.
- A sphere of radius 2 round the caster's chest (3.5 with Shelter). Each server tick, after every entity has moved,
  each projectile's next-tick segment is tested against the sphere (`entry`), so a fast bolt cannot pass between two
  checks. Bolts stop and drop for 3 FE (heavy bolts 10 FE); with **Return** they fly back at the shooter for 60% of
  their damage. Arrows and thrown things fall short. A projectile class can answer for itself through
  `BarrierReactive` (the hunter's rocket bursts at the surface, the net falls short).
- Melee and flame damage to the protected are halved; blasts from outside keep 30% (`damageMultiplier`,
  `modifyDamage`). **Shelter** guards allies and harmless creatures inside its sphere, never enemies.
- While it is held the caster cannot attack or use items, and sprints only at walking pace.
- One hit of 10 or more breaks it (the caster is staggered 20 ticks). Releasing after 40 or more ticks of holding sends
  a pulse: enemies within 3 blocks are thrown back and staggered 10 ticks (and an ash hound's lunge is turned aside).
- **Guardian's Patience** (passive): parries during the 40 ticks after a barrier ends restore 2 FE each.
- Mastery: a point for every 5 projectiles stopped and every 30 melee damage absorbed.

## 5. Force Choke (9.3)

- A channel on one living, breathing target: the crosshair ray first, then a 6 degree cone, within 12 blocks (plus 1
  per mastery rank) and in sight. 25 FE to start, 6 FE a second, at most 60 ticks (20 on a `c:bosses` target), cooldown
  360 ticks.
- Refused for free on anything that does not breathe (`breathes`: droids, constructs, `ChokeImmune`, the
  `oldrepublic:choke_immune` entity tag) and when `ForceReactions.chokeTicks` answers 0 (Kaast outside phase 3).
- A creature is lifted 0.6 blocks with gravity off and its AI paused (`ChokedTargets`, `MobChokeMixin`); players are
  held through the lead's `Holds`, and only with PvP (`pvpEffects`). The hold uses `ControlEffects` diminishing
  returns. 2 damage every 10 ticks (`force_choke`, tagged `force_harm`).
- It ends when released, when the time is up, on a hit of 6 or more on the caster, a Push on the caster, or losing
  sight. On the release the target drops and is staggered 15 ticks.
- Choking a creature that is not hostile shifts the caster's alignment by -3, once per target per day.
- **Crush** (branch `choke_shape`): a release after 40 or more ticks of holding deals 6 more (the invulnerability
  frames are cleared first, so the crush lands even right after a hold tick).
- **Dread** (branch `choke_shape`): the target's allies within 6 blocks flee for 40 ticks.
- **Cruel Focus** (passive): Lightning on a target released from the caster's own Choke within 40 ticks deals +20%.
- Mastery: a point for each hold on a hostile target that lasts 20 ticks (`masteryHoldTicks`); each rank lowers the
  start cost by 2 and adds 1 block of range.

## 6. Lift for the paths and the tree (9.4, 9.5)

- `lift_learned_jedi` and `lift_learned_sith` (2 SP, level 13, path-locked) teach `force_lift`. The skills feature then
  gives the `force_lift` node as granted; `ForceM4Feature.tickLiftNodes` turns that node into a bought one with no cost
  while a path node owns it, so a respec removes Lift with the node that taught it and refunds only the 2 SP paid.
- The spec's `requires force_push:2` cannot be met: `force_push` has one rank. The nodes require `["force_push"]`
  (decision; the spec fix is in the report).
- Every other node is as the 9.5 table. `staff_whirl` and `lens_mastery` are W5's mechanics; W6 adds the nodes, their
  icons and their tooltips.

## 7. Effects (13)

Each event is drawn by the client from the event alone (no polling of server state), and every looping sound has its
own stop paths (`PowerLoop`): the end event, a watchdog of the length the server sent plus 20 ticks, the player's death
or removal, a level change and disconnect. Nothing draws a ground circle, a halo or an outline.

- **Speed.** Start: a puff of displaced air and a little dust at the feet, `force.speed.start`; for the runner only,
  the `force.speed.loop` wind at the ears. Running: two dust motes in the ground's colour kicked back from each
  footfall (one every 1.4 blocks) and six faint bent-air streaks a second at the ankles and wrists. Burst: a line of
  streaks along the dash and `force.speed.burst`. End: a second puff and `force.speed.end`. No glow, no after-images.
- **Barrier.** Start: `force.barrier.start`, up to 24 `held_dust` motes (dust from the ground below, a few leaves over
  grass) drifting slowly on the sphere; the `force.barrier.loop` hum, which for the caster sags and wavers as energy runs
  low. Impact: a bent-air ripple at the point on the sphere (kept at Reduced effects), the bolt fading into sparks, six
  motes shaken along the surface, `force.barrier.impact`. Pulse: the dust thrown outward, `force.barrier.pulse`. End:
  the dust falls. Break: the dust scatters with a crack of air, `force.barrier.break`.
- **Choke.** Start: `force.choke.start` and the `force.choke.loop` creak on the caster. The held target: ten grit motes
  a second lifted from the ground under it, a slight bent-air tremor at the neck and shoulders, its hands at its throat
  and its feet hanging (the humanoid pose mixin). Release: a puff of dust as it drops and `force.choke.release`, or
  `force.choke.crush` and a heavier puff. No glow, no tendrils.
- The caster's arm poses: `PowerAnimations.Kind.BARRIER` (both palms out at chest height, a slow sway) and `CHOKE`
  (the free arm raised along the look toward the target, straining), started and stopped by `BarrierFx` and `ChokeFx`
  with the same watchdogs, drawn in third person by `HumanoidModelChokeMixin` and in first person by
  `PowerAnimations.applyFirstPerson`.

## 8. Balance (14)

`force_speed`: `cost` 20, `cooldownTicks` 600, `durationTicks` 160, `speedPct` 40, `stepHeight` 0.5, `sprintJumpPct` 20,
`sprintHungerMult` 0.5, `burstTicks` 20, `burstSpeedPct` 100, `burstDistance` 6, `burstDashTicks` 4, `longRoadTicks` 300,
`longRoadExtraCost` 5, `wayfarerMissChance` 0.25, `wayfarerMaxDegrees` 1.5, `wayfarerSafeFall` 8, `masteryBlocks` 200,
`masteryCostPerRank` 2, `masteryTicksPerRank` 20, `pvpEffects` false.

`force_barrier`: `startCost` 15, `channelCost` 6, `maxTicks` 120, `cooldownTicks` 400, `radius` 2, `shelterRadius` 3.5,
`boltCost` 3, `heavyBoltCost` 10, `returnDamageMult` 0.6, `blastMult` 0.3, `meleePct` 50, `flamePct` 50, `breakDamage` 10,
`breakStaggerTicks` 20, `sprintMoveMult` 0.77, `pulseMinTicks` 40, `pulseRadius` 3, `pulseKnockback` 0.85,
`pulseStaggerTicks` 10, `guardiansPatienceFe` 2, `guardiansPatienceTicks` 40, `masteryProjectiles` 5,
`masteryMeleeAbsorbed` 30, `masteryCostPerRank` 1, `masteryTicksPerRank` 10, `pvpEffects` false.

`force_choke`: `startCost` 25, `channelCost` 6, `maxTicks` 60, `cooldownTicks` 360, `range` 12, `coneDegrees` 6,
`liftHeight` 0.6, `damage` 2, `damageInterval` 10, `releaseStaggerTicks` 15, `interruptDamage` 6, `bossMaxTicks` 20,
`nonHostileShift` -3, `crushMinTicks` 40, `crushDamage` 6, `dreadRadius` 6, `dreadTicks` 40, `cruelFocusPct` 20,
`cruelFocusWindow` 40, `masteryHoldTicks` 20, `masteryCostPerRank` 2, `masteryRangePerRank` 1, `pvpEffects` false.

## 9. Tests (17)

`ForceM4GameTests` (the spec's 19 names): `speedDurationCostAndCancels`, `speedBurstDash`,
`wayfarerMissChanceNeverThroughWalls`, `barrierStopsBoltsAndCostsFe`, `barrierHeavyCostsMore`, `barrierPulseAfter40Ticks`,
`barrierShelterProtectsAllies`, `barrierReturnSendsBoltsBack`, `barrierBreaksOnBigHit`, `chokeTargetsBreathingOnly`,
`chokeHoldUsesDiminishing`, `chokeOnPlayersOnlyWithPvp`, `chokeShiftOnNonHostileOncePerDay`, `crushAndDreadBranches`,
`cruelFocusWithin40TicksOfRelease`, `chokeTicksHookRespected`, `liftNodesInJediAndSithTreesOnly`,
`newNodesVisibleAndPurchasable`, `masteryThresholds`. All run in the lead's `arena_24x7x24`, at most 400 ticks, seeded
randomness, with Fabric fake players (or listed mock players where a rule reads `level.players()`), and undo
everything they start on every path. Long timers (cooldowns, the daily alignment shift) are tested through the pure
rules.

`m4/FoesClientSteps` screenshots 60 (Speed's dust trail, from behind, sprinting), 61 (Barrier stopping two hunter
bolts) and 62 (Choke on a hunter), each confirmed by the server before the step moves on.

## 10. API status

- `[U]` `Player#causeFoodExhaustion` with a negative amount (halved sprint hunger): whether `FoodData#addExhaustion`
  clamps below zero.
- `[U]` `LivingEntity#getSoundVolume()` as the mixin target for the muffled hurt sounds.
- Everything else `[V]`.

## 11. Known issues

- None beyond the `[U]` checks in section 10. Nothing here has been compiled against Minecraft yet.
