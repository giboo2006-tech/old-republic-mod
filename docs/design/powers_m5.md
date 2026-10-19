# Force powers, Milestone 5: the second power bar, three control kinds and six powers

Owner: W6 (powers). Common code: `force/ForceM5Feature`, `force/ForceMobEffects`, and the packages
`com.oldrepublic.force.{stasis,valor,drain,fear,rage,mindtrick}`, plus `force/choke/ForceChoke` (Dread moved onto the
shared fear). Client code: `client/force/{StasisFx,ValorFx,DrainFx,FearFx,RageFx,MindTrickFx,ForceM5Client}`,
`client/force/particle/{StasisRimeParticle,DrainMoteParticle}`, and the second bar in `client/ability/AbilityInput`,
`client/hud/ForceHud`, `client/skill/ForceScreen` and `client/force/ForceKeys`. Skill code: `skill/{SkillRules,SkillService,
SkillBalance,SkillIds,SkillNodeEffects,NodeInfo}`. Mixins are in `oldrepublic-force.mixins.json`. The spec is
`docs/MILESTONE_5.md`: 9.1 to 9.9, 15 (the six powers' effects and sounds), 16.4 (the six balance sections and the
`skills` keys), 18 (ids) and 19 (`ForceM5GameTests`, screenshots 165 to 173). The M2 to M4 powers these sit beside are
in `docs/design/force.md` and `docs/design/force_m4.md`. The assets are in `docs/assets/powers_m5.md`.

Nothing was cut. The cut order (spec 1) has no item for this work. All six powers, their twelve branches, mastery, the
second bar, the three control kinds and the six status effects are in. Two small details of the look are not drawn
(section 13).

API status: every vanilla and Fabric call is verified against the 26.2 dumps or is used the same way in committed code,
except the seven marked `// API-CHECK:` and listed in section 12.

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Powers (6) | `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick` | `ForceStasis`, `ForceValor`, `ForceDrain`, `ForceFear`, `ForceRage`, `MindTrick` (registered in `Abilities` by `ForceM5Feature.init`) |
| Tree nodes (22) | jedi `force_stasis`, `stasis_field`, `stasis_lock`, `force_valor`, `valor_shared`, `valor_steadfast`; sith `force_drain`, `drain_life`, `drain_essence`, `force_fear`, `fear_terror`, `fear_dread`, `force_rage`, `rage_unbound`, `rage_cold`; exploration `force_mind_trick`, `mind_trick_confuse`, `mind_trick_mass`, `wayfarers_stride`; saber `form_makashi`, `form_djem_so`, `form_jar_kai` (W7's mechanics) | `data/oldrepublic/skill_tree/{jedi,sith,exploration,saber}.json`; ids in `skill/SkillIds` |
| Status effects (6) | `oldrepublic:stasis`, `fear`, `pacified`, `valor`, `rage`, `drained` | `force/ForceMobEffects` (`Registry.registerForHolder` on `BuiltInRegistries.MOB_EFFECT`) |
| Control kinds (3) | `ControlEffects.STASIS`, `FEAR`, `PACIFY` (the lead's constants) | runtimes `stasis/StasisHolds`, `fear/Frightened`, `mindtrick/Pacified` |
| Effect events (6) | `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick` | section 10 |
| Particles (3 new) | `oldrepublic:stasis_rime`, `drain_mote`, `valor_dust`; reused by id: `air_ripple`, `shadow_wisp`, `flare_ember` | `ForceM5Feature` (types), `ForceM5Client` (providers) |
| Damage type | `oldrepublic:force_drain` | `data/oldrepublic/damage_type/force_drain.json`; `#oldrepublic:force_harm` needs it (a lead tag file, section 14) |
| Entity tags (4) | `#oldrepublic:fearless` (hask_warrior, cantor_choir), `strong_willed` (hask_warrior, vessba), `mind_trickable` (republic_trooper, sith_trooper, crystal_poacher, poacher_captain, cartel_enforcer), `force_resistant` (hymnsworn, cantor_choir) | `data/oldrepublic/tags/entity_type/`; every entry `required: false`, so a missing type never breaks the tag |
| Tags read, not owned | `#oldrepublic:droids`, `#oldrepublic:push_resistant`, `#oldrepublic:ship_targets` (W2's, still to be made), `#c:bosses` | |
| Attribute modifier ids (transient) | `oldrepublic:valor_damage`, `valor_armor`, `valor_knockback`; `rage_damage`, `rage_attack_speed`, `rage_move_speed` | `ForceValor`, `ForceRage` |
| Balance sections (6) | `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick`, and three `skills` keys | `StasisBalance`, `ValorBalance`, `DrainBalance`, `FearBalance`, `RageBalance`, `MindTrickBalance`, `SkillBalance` |
| Sounds (14) | `force.stasis.{cast,loop,break}`, `force.valor.{cast,end}`, `force.drain.{loop,end}`, `force.fear.cast`, `force.rage.{cast,loop,end}`, `force.mind_trick.{cast,fail}`, `force.focus` | the Phase 0 `ModSounds` names; files in `assets/oldrepublic/sounds/force/` |
| Key | `key.oldrepublic.focus` (Focus, Left Alt, rebindable) | `client/force/ForceKeys.FOCUS` |
| Dialogue | condition `ability:knows`; actions `mind_trick:use`, `fear:use` | `mindtrick/PowerDialogues` |
| Commands | `/oldrepublic force grant <id> [player]` gains the six powers, their twelve branches and `wayfarers_stride` (a tree node is granted through `Skills.grant`, as the story does it); `/oldrepublic force use <id> start|release|cancel` presses one of the six for the command's player | `ForceM5Feature.forceCommand`, merged into the lead's `force` command |
| Mixins | common `LivingEntityStasisMixin` (`isImmobile` true in stasis), `MobPacifyMixin` (`setTarget` refused while pacified); client `LocalPlayerFearMixin` (no sprint start while afraid), and the M2 `HumanoidModelMixin`, `AvatarRendererMixin`, `LivingEntityRendererLiftMixin` extended (the five new poses, Rage's posture, Fear's shiver and Mind Trick's calm) | `oldrepublic-force.mixins.json` |

## 2. Classes

- `ForceM5Feature`: registration (the effects, the six abilities, the dialogue hooks, the confused test of
  `Allegiances`), the duration rules of the new kinds (`durationMultiplier`, one `ControlEffects.DurationModifier`), the
  `DamageModifiers` of Nightmare and Rage, a `ForceReactions` handler (a staggered caster lets go of Drain; a drained
  player pays more), the server tick (the three control runtimes, the Drain channels, and Valor's and Rage's modifier
  sync for every player), every end path (leave, death, level change, server stop), the damage, load and unload hooks,
  the three particle types and the `grant` and `use` subcommands.
- `ForceMobEffects`: the six effects, `apply(target, effect, ticks, source)` (particles off, icon on) and `remaining`.
  The effects carry only the icon, the timer and the name; the effect is the state every rule reads.
- `stasis/ForceStasis`, `StasisHolds`, `StasisBalance`. `fear/ForceFear` (with `ForceFear.apply`, the shared fear),
  `Frightened`, `FearBalance`. `mindtrick/MindTrick`, `Pacified`, `PowerDialogues`, `MindTrickBalance`.
  `valor/ForceValor`, `ValorBalance`. `drain/ForceDrain`, `DrainBalance`. `rage/ForceRage`, `RageBalance`.
- Client: `ForceM5Client` (event handlers, the particle providers, the ticks, the Rage vignette layer, the level-change
  and disconnect clean-up), one `*Fx` class per power, `StasisRimeParticle` and `DrainMoteParticle` (`valor_dust` uses the
  M2 `DustMoteParticle`), and the poses in `PowerAnimations.Kind` (`STASIS`, `VALOR`, `DRAIN`, `FEAR`, `MIND_TRICK`).

## 3. The second power bar (9.1)

- **Eight slots.** Slots 1 to 4 are the first bar (R, Z, V, B, unchanged). Slots 5 to 8 are the second bar: hold Focus
  (Left Alt) and press R, Z, V or B. While Focus is held the four keys send only slots 5 to 8. `AbilityInput` chooses
  the bar when a key goes down and keeps it for that press, so a held hold-and-release or channel power keeps its slot
  until its key comes up, whatever Focus does in between. Pressing Focus plays `force.focus`, a soft click, for the
  local player only. A screen opening or death releases Focus with everything else.
- **Opening.** `SkillRules.openSlots2(level, rankFour, b)`: 2 from level 15 (`skills.secondBarLevel`), 4 at rank 4.
  "Rank 4" is `SkillService.rankFour`: `Identity.rank >= 4` (Master, Darth) or the identity choice `title.wayfarer`
  (`SkillService.WAYFARER_TITLE`, which W5's Wayfarer ceremony records; an Independent holds no rank). The first bar
  keeps the M3 rule. `SkillService.applyDerived` sets both counts on every refresh. A slot that closes is emptied and
  its power cancelled. A slot that opens takes an owned power that had no slot, in tree order. The slot rule itself
  is the lead's `SkillData.isOpen` and `Skills.isOpen`.
- **HUD** (`ForceHud`). Once a second-bar slot holds a power, its row sits above the first bar. It is dimmed until
  Focus is held. While Focus is held the first bar dims, the second lights, and its key labels read "Alt+R" and so on
  (the bound keys' names). Focus eases between the two in a few frames. The Force Sense pip never dims.
- **Skills screen** (`ForceScreen`). The footer strip shows eight sockets in one row: the first bar, the Focus caption
  ("Alt+"), then the second bar. Each socket shows its key in a corner. A closed one shows a lock, whose tooltip names
  its rule ("Opens at level 15, or at the fourth rank"; "Opens at the fourth rank (Master, Darth or Wayfarer)"). Number
  keys 1 to 8 and the eight assign buttons place the selected power; a closed slot's button is disabled.
- **Wayfarer's Grace** (`wayfarers_stride`, Exploration passive, Independent, granted only, by W5's ceremony): +10%
  maximum Force Energy (`skills.wayfarerEnergyPct`, a stat modifier of `SkillRules.statMods`) and Force Speed costs 20%
  less (`skills.wayfarerSpeedCostPct`, through `SkillNodeEffects`).
- The key-clash test is the lead's (`m2/SkillClientSteps.checkKeyDefaults` has Focus).

## 4. The control kinds and status effects (9.2)

Every length goes through `ControlEffects.diminished(target, ticks, kind)` and nothing else. On push-resistant targets
the second control of any kind within `force_push.staggerDrWindowTicks` lasts half as long, and the third does nothing.
`ForceM5Feature.durationMultiplier` adds the new rules:

- `stasis` on a boss is ×`force_stasis.bossMultiplier` (0.25), multiplied with the boss's own modifier, so Kaast (×0.6)
  takes 15%. On a `#oldrepublic:force_resistant` creature it is ×0.5.
- `fear` and `pacify` on a boss are ×0: immune.
- `stagger` on a player under Steadfast Valor is ×0.

The runtimes:

- **`stasis`** (`StasisHolds`). The effect, plus `LivingEntityStasisMixin`: `LivingEntity#isImmobile` returns true
  while the effect is on (vanilla's body is only `isDeadOrDying()`), so `aiStep` zeroes `xxa`, `zza` and `jumping` and
  skips `serverAiStep`. That means no AI and no input, but gravity and knockback stay. A creature that flies and is
  caught in the air gets `setYya(0)` and no gravity until the hold ends. Never `setNoAi`. A player (with PvP on and
  `pvpEffects`) is held through the M3 `Holds`, with the effect for its icon. Damage ends a hold: one hit of
  `breakDamage` or more at once, else `breakDelayTicks` after the first hit. Stasis Lock ignores its first hits. The
  effect is the state: when anything removes it (expiry, milk, `/effect clear`, a boss's rule such as W8's Ulvaine
  removing it after `stasisMaxTicks`), the hold ends on the next tick and the break event goes out.
  `StasisHolds.release` ends a hold at once. A creature that loads with the effect is held again.
- **`fear`** (`Frightened`, entered only through `ForceFear.apply`). The effect, plus, on a `PathfinderMob`, a
  priority-0 flee goal. It runs to `DefaultRandomPos.getPosAway(mob, 16, 7, from)` at ×1.3, and the mob drops its
  target every second. A creature without goals, or a player (PvP), gets Slowness II (hidden). A player also cannot
  sprint: `LocalPlayerFearMixin` refuses a sprint start on the client, and the server stops a running one. Bosses,
  `#oldrepublic:droids` and `#oldrepublic:fearless` are never frightened. A new fear interrupts a wind-up
  (`ForceReactions`). The goal goes on the tick after the effect ends, and comes back for a creature that loads with
  it.
- **`pacify`** (`Pacified`). The effect. The mob drops its target every tick, and `MobPacifyMixin` refuses any new one
  at the head of `Mob#setTarget`, so it wanders. Any damage from a player ends it. Bosses are never pacified.
  Confusion is the exception: the cancel lets the mob's own chosen victim through (section 9).
- **Choke's Dread moved.** `ForceChoke.dread` now calls `ForceFear.apply(mob, caster, dreadTicks, target.position())`,
  so the frightened run from the choked creature as before (same radius 6, same 40 ticks). Its own flee map is gone.
  The saber's `dread_presence` is W7's to move (section 14).
- **Effects** are applied as `new MobEffectInstance(holder, ticks, 0, false, false, true)`: particles off, icon on. A
  shorter instance never cuts a longer one short.

## 5. Force Stasis (9.3)

- A tap at the creature under the crosshair within 16 blocks, the M2 single-target aim (the ray, then an 8 degree
  aim-assist cone, in sight). 30 FE; cooldown 400 ticks. No target: a line, and nothing is spent. A cast that holds
  nobody (the diminishing returns left nothing) is spent, and "The stillness will not take hold." says why.
- `stasis` for 60 ticks. One hit of 8 or more ends it, and so does the 20th tick after the first hit.
- **Stasis Field** (`stasis_field`, group `stasis_shape`): every hostile within 4 blocks of the target, the target too,
  is held for 40 ticks.
- **Stasis Lock** (`stasis_lock`): one target for 100 ticks, and its first two hits do not count, neither as a break
  nor as the first hit.
- Mastery: +1 per creature held by a cast. Ranks add 5, 10 and 20 ticks.
- Counterplay: bosses at 25%, `force_resistant` at half, the M2 diminishing returns. Players only with PvP.

## 6. Force Valor (9.4)

- A tap. 35 FE; cooldown 900 ticks. Granted at the Master ceremony (a `granted_only` Jedi node).
- `valor` for 200 ticks on the caster and on every player within 8 blocks whom Shared Light counts as an ally
  (`ForceHeal.isShareTarget`). Each gets +20% attack damage (`ADD_MULTIPLIED_TOTAL`), +4 armour and +0.5 knockback
  resistance.
- **Rallying Light** (`valor_shared`): 16 blocks, and each ally regains 4 health at the start.
- **Steadfast** (`valor_steadfast`): the caster alone, +30% attack damage, and no `stagger` while it lasts. A plain Valor
  never weakens a running Steadfast one.
- The modifiers are transient, with fixed ids. `ForceValor.sync` runs for every player every tick. They go when the
  effect ends for any reason (expiry, death, milk, `/effect clear`), with the end event. They come back for a player
  who logs in with the effect. After a restart, a Steadfast owner whose own Valor is still cooling down is taken to
  have cast it, and gets Steadfast's numbers back. `ForceValor.forget` drops a player from the Steadfast set on
  disconnect.
- Mastery: +1 per cast that touches an ally, or that is cast in combat. Each rank adds 20 ticks.

## 7. Force Drain (9.5)

- A channel at one living target within 10 blocks, in sight (the M2 single-target aim). 10 FE to start and 2 per pulse.
  A pulse every 10 ticks, at most 6. The 300-tick cooldown runs from the end.
- Each pulse deals 2 damage (`oldrepublic:force_drain`), heals the caster 1.5 and gives back 2 FE. The target is
  `drained`, with a hidden Slowness I, while the channel holds; both outlast each pulse a little so they never blink.
- A pulse has no knockback. The hurt's own push (0.4 away from the caster, and a hop) carried a target about 3 blocks
  a pulse, out of reach after three or four, so `pulse` puts the target's motion and `hurtMarked` back as they were.
- It ends when the target leaves range or sight or dies, when the caster is staggered (`ForceReactions.interruptWindup`
  on the caster), on release, after the last pulse, and on the caster's death, disconnect or change of level.
- Refused with a line and nothing spent: `#oldrepublic:droids` and the starfighters (`#oldrepublic:ship_targets`) have
  no life to drain. Players only with PvP.
- **Drain Life** (`drain_life`): heal ×2, damage ×0.75.
- **Drain Essence** (`drain_essence`): +4 FE per pulse (6 in all), and each pulse interrupts the target's wind-up
  (`ForceReactions.interruptWindup` with the new cause `drain`; a boss's handler decides). A player drained this way
  (PvP) pays double for powers while it lasts (`drainedCostMultiplier`, through `ForceReactions.costMultiplier`).
- Mastery: +1 per pulse that lands. Each rank adds 0.25 damage per pulse.

## 8. Force Fear (9.6)

- A tap: every eligible creature in a 60 degree cone of 10 blocks, in sight, is afraid for 80 ticks. Eligible means
  hostile to the caster (or a player with PvP) and not shielded from area powers (`Attitudes.shieldedFromArea`). A cone
  with nobody in it still casts, as Push does.
- **Terror** (`fear_terror`, group `fear_shape`): all round the caster, radius 8, 60 ticks.
- **Nightmare** (`fear_dread`): one target, the crosshair pick, for 160 ticks, and it takes +15% damage while afraid
  (a `DamageModifier`). No target: a line, nothing spent.
- Bosses, droids and `#oldrepublic:fearless` are immune; the diminishing returns apply. When nobody in the shape could
  be frightened, the caster is told "They do not flinch."
- Mastery: +1 per creature frightened. Each rank adds 10 ticks.
- Dialogue: `fear:use` (section 11).

## 9. Force Rage (9.7) and Mind Trick (9.8)

**Rage.** A tap; 30 FE; cooldown 900 ticks. Granted at the Darth ceremony (a `granted_only` Sith node).

- `rage` for 200 ticks: +30% melee damage, +15% attack speed and +10% movement speed (transient modifiers, as Valor's,
  kept in step by `ForceRage.sync`).
- The price: the caster takes 10% more damage (a `DamageModifier`). It also regenerates no Force Energy:
  `SkillService.applyDerived` sets the regeneration modifier to -100% while the effect is on, and `sync` refreshes the
  skills when it comes and goes.
- **Bloodthirst** (`rage_unbound`): each saber hit heals 1 (a melee hit with a usable saber in either hand, or the
  thrown saber).
- **Cold Fury** (`rage_cold`): no damage-taken penalty, but +20% melee damage instead of +30%.
- Mastery: +1 per melee or saber hit landed on a hostile creature or a player during Rage. Each rank adds 20 ticks.

**Mind Trick.** A tap at the creature under the crosshair within 8 blocks; 25 FE; cooldown 600 ticks. Exploration
tree, any path, no teacher, no alignment opposition.

- A weak mind (`#oldrepublic:mind_trickable` and not `#oldrepublic:strong_willed`, never a boss) is pacified for 120
  ticks. Every other mob (droids, beasts, Force users, the strong-willed) refuses with "That mind is closed to you.",
  the refused event plays, and nothing is spent. The spec's line said "His"; it is neutral because the target can be
  any creature. Only mobs are aimed at, so a player is never a target.
- **Confusion** (`mind_trick_confuse`, group `mind_trick_shape`): for 80 ticks the target attacks its nearest ally
  within 12 blocks instead of wandering (a mob of its own side, else of its own kind). While it lasts
  `Allegiances.friendly` is false for it and for whatever it meets (`Pacified.isConfused`, installed through
  `Allegiances.setConfusedTest`), so it may hurt its own side and they answer.
- **Many Minds** (`mind_trick_mass`): up to 3 minds in all, for 80 ticks: the first and the nearest weak minds within 6
  blocks of it, in the caster's sight.
- Mastery: +1 per successful trick; dialogue uses count. Each rank adds 20 ticks.

## 10. Effects (15)

Every event is drawn from the event alone, with no polling of server state. The one exception is the Rage caster's
own client, which follows its own `rage` effect, so a relog keeps the loop and the vignette. Counts go through
`Vfx.count` and `Vfx.inRange`, so the presets thin them; nothing here is a tell. Nothing draws a ground circle, a halo,
a glyph or an outline. Each loop is a `PowerLoopSound`, which stops itself when its entity dies, is removed (leaves
tracking range) or leaves the client's level.

| Id | Delivery (variant, intensity) |
|---|---|
| `force_stasis` | toTracking on the target; variant = `ForceEffects.packCaster(caster id, v)`: v 0 cast, 1 held (on each creature held; intensity = seconds), 2 break (unpacked: the caster may be gone) |
| `force_valor` | toTracking; 0 cast (on the caster; intensity = seconds), 1 an ally touched (on the ally), 2 end (on any player whose valor ended) |
| `force_drain` | toTracking on the caster; pos = the target's chest, dir = from the caster's eyes to the target; variant = `packCaster(target id, v)`: v 0 start (intensity = the longest channel in seconds), 1 pulse (intensity = the distance), 2 end |
| `force_fear` | toTracking; 0 cast (on the caster; pos = the eyes; dir = the cone's axis, zero for Terror; intensity = the range or the radius), 1 a victim (on the victim; intensity = seconds) |
| `force_rage` | toTracking on the caster; 0 start (intensity = seconds), 1 end |
| `force_mind_trick` | toTracking on the target; variant = `packCaster(caster id, v)`: v 0 success (intensity = seconds, 0 for a dialogue use), 1 refused, 3 under Confusion; 2 ended (unpacked: a hit broke the calm) |

Packing the caster's or target's id in the variant extends the 15.3 table, which named only the variant numbers. The
added variants 2 and 3 of `force_mind_trick` are new too.

Phase by phase:

- **Stasis.** *Anticipation*: the caster's palm goes out, fingers spread, and holds (`STASIS`). *Activation*:
  `force.stasis.cast`, a glassy chime, at the target. *Travel*: none; the hold is instant. *Impact*: 16 `stasis_rime`
  motes drift in, settle on the target and hang perfectly still. Two small patches of bent air on its body
  (`air_ripple`) stop moving: the frozen heat haze. `force.stasis.loop` hums on it. *Aftermath*: on the break the
  motes lose their frost, turn grey and fall as grit, with `force.stasis.break`. The caster's pose holds 20 ticks.
- **Valor.** *Anticipation*: the saber hand rises, the other hand opens at the chest (`VALOR`, 26 ticks). *Activation*:
  `force.valor.cast`, a low swell, and a warm gust: 20 `valor_dust` lifted round the caster in three uneven puffs.
  *Travel*: the same puffs round each ally touched (the swell is the caster's alone). *Lasting*: one mote rises from
  each affected player every 10 ticks. *Aftermath*: `force.valor.end`, and the motes stop.
- **Drain.** *Anticipation*: the free hand reaches out, fingers curled, and draws slowly back (`DRAIN`).
  *Activation*: `force.drain.loop` on the caster. *Travel*: every tick 3 dark-red `drain_mote` leave the target and
  steer for the caster's hand: a thin stream. *Impact*: each pulse thickens the stream for a few ticks. *Aftermath*:
  the loop stops, `force.drain.end` sighs, and the pose drops.
- **Fear.** *Anticipation*: a cupped hand thrust out and twisted (`FEAR`, 14 ticks). *Activation*: `force.fear.cast`, a
  whispering rush. *Travel*: one `air_ripple` sweep down the cone. Terror's goes out all round in up to 6 broken,
  uneven arcs. *Impact*: 6 `shadow_wisp` curl up at each victim's feet, and the victim shivers (a small tremor added
  to its pose). *Lasting*: one wisp per 10 ticks while it is afraid.
- **Rage.** *Activation*: `force.rage.cast`, a growl in the Force, for everyone near. *Lasting*, seen by everyone: one
  `flare_ember` a tick trailing a lit blade, a heat haze of one `air_ripple` every 5 ticks, and the hunched posture
  (shoulders up, saber arm low). The posture is an offset added to the idle and walk pose, never over a power pose, and
  it fades out through a swing. For the caster only: `force.rage.loop` and a dark-red edge vignette that pulses
  slowly (steady with flashing effects off). *Aftermath*: `force.rage.end`, and everything stops.
- **Mind Trick.** *Anticipation*: two fingers raised beside the face, a small wave (`MIND_TRICK`, 18 ticks). *Activation*:
  `force.mind_trick.cast`, a soft low tone, at the target. *Impact*: one `air_ripple` at its head, and the calm: it
  lowers its weapon and bows its head a little while the pacify lasts. Under Confusion there is no calm. A refusal
  plays the gesture and `force.mind_trick.fail`, and the air does not bend. *Aftermath*: when a hit breaks the calm
  (variant 2), the guard comes back up.

Budgets at Standard (15.1), all met: Stasis 16 rime held, the same 16 falling. Valor 20 dust per player at the cast,
then 1 per 10 ticks. Drain 3 motes a tick. Fear one ripple sweep, then 6 wisps per victim and 1 per 10 ticks. Rage 1
ember a tick and 1 ripple per 5 ticks. Mind Trick 1 ripple. Two small additions beyond the table: Stasis's frozen haze
is 2 `air_ripple` per held creature, and Terror's sweep is up to 6 arcs instead of one ripple.

Loop stop paths (15.4):

- `force.stasis.loop` (on the target) stops on the break event, whatever ended the hold on the server (a hit, its time,
  milk, `/effect clear`, death, unload). It also stops on the target's death or removal, a level change, a disconnect,
  and a watchdog: the length the server sent, plus 20 ticks.
- `force.drain.loop` (on the caster) stops on the end event, whatever ended the channel. It also stops on the caster's
  death or removal, a level change, a disconnect, and a watchdog: the longest channel plus 30 ticks.
- `force.rage.loop` plays for the caster only, and follows the caster's own `rage` effect. It stops the tick the effect
  goes for any reason, and on death, a level change and a disconnect.

## 11. Dialogue hooks (6.11)

- Condition `ability:knows <id>`: the player knows the power, it is off cooldown, and they can afford its base cost now.
  A failure names the power.
- Action `mind_trick:use`: spends Mind Trick's cost and starts its cooldown as if cast. It plays the gesture, the tone and
  the ripple at the speaker, counts a successful use for mastery, and the conversation moves on.
- Action `fear:use`: the same for Fear, with its cast event along the player's look (intensity 4, a short sweep).
- W4's Saltgate ways use them (`quest/story/SaltgateWays`: the counting-house door, Mind Trick or Fear), through
  `DialogueConditions.get` and `DialogueActions.get` with the ability id as the condition's argument.

## 12. API status

`[U]` means unverified; each one is marked `// API-CHECK:` in the code.

- `[U]` `LocalPlayer#canStartSprinting` (private, boolean): the target of `LocalPlayerFearMixin`. The name comes from the
  M5 follow-up probe's notes, not a dump.
- `[U]` `Mob#setTarget(LivingEntity)`: that every target change goes through it (the head-cancel of
  `MobPacifyMixin`). Its body was not probed.
- `[U]` `net.minecraft.world.entity.ai.navigation.FlyingPathNavigation`: its package (`StasisHolds`, to tell a flier).
- `[U]` The effect icon path: `textures/mob_effect/<id>.png`, read through the GUI atlas as `<ns>:mob_effect/<id>`
  (`ForceMobEffects`). If it lives elsewhere, only the six PNGs move.
- `[U]` `io.netty.channel.embedded.EmbeddedChannel` on the gametest compile classpath (`m5/ForceClientSteps`, the mock
  second player; `GameTestHelper`'s own recipe uses it).
- `[U]` Fabric's `@GameTest(padding = 16)` meaning a 16-block margin round the structure (`valorTouchesAllies`).
- `[U]` `Player#isImmobile`'s override was not probed. Nothing depends on it: players in stasis go through `Holds`.
- Everything else `[V]`.

## 13. Known issues

- Two details of the look are not drawn. Drain's "target's skin greying slightly" would need a tint on every entity
  renderer. Mind Trick's "blink slowly" has no eyelid to move on a vanilla humanoid model; the calm (weapon lowered,
  head bowed) carries the read instead.
- Rage's embers follow an approximate blade line from the hand and the look. They are not taken from the rendered item
  transform.
- Other players' clients learn of a rage only through its events. After the raging player relogs, they see the
  posture and the embers again only at the next cast. The raging player's own client is right throughout.
- W7's `HumanoidModelSaberMixin` (the forms' stances) and Rage's posture both inject at the tail of
  `HumanoidModel#setupAnim`. If the stance sets the arms outright and runs after, Rage's arm offsets are lost while a
  stance shows. The two have not been seen together yet.
- Several rules are tested only where their types exist. Until W8's, W4's and W2's types and tags land, the
  `force_resistant`, `fearless`, `strong_willed` and `ship_targets` parts of the tests are skipped.
- The client steps' mock second player leaves a playerdata file in the test world.

## 14. Requests to other owners

- Lead: add `{"id": "oldrepublic:force_drain", "required": false}` to `data/oldrepublic/tags/damage_type/force_harm.json`.
- Lead: register `com.oldrepublic.gametest.ForceM5GameTests` in `src/gametest/resources/fabric.mod.json`.
- W7: `dread_presence` in `saber/SaberTechniques` calls `ForceFear.apply(target, player, fear, player.position())`
  instead of its own `FEAR` map, and drops the map, the `Fear` record and `tickFear`.
- W8: nothing needed. Ulvaine removes the `oldrepublic:stasis` effect after `stasisMaxTicks`, and because the effect is
  the state, the hold ends on the next tick with the break event. `StasisHolds.release(entity)` does the same at once.
- W2 (or the lead, as Phase 0 creates new tag files): the `#oldrepublic:ship_targets` entity tag, which Drain refuses.
  It does not exist yet; until it does, nothing is refused as a starfighter.

## 15. Balance (16.4)

`force_stasis`: `cost` 30, `cooldownTicks` 400, `range` 16, `aimAssistHalfAngle` 8, `durationTicks` 60, `breakDamage` 8,
`breakDelayTicks` 20, `fieldRadius` 4, `fieldTicks` 40, `lockTicks` 100, `lockIgnoredHits` 2, `bossMultiplier` 0.25,
`resistantMultiplier` 0.5, `masteryTicks` [5, 10, 20], `pvpEffects` false.

`force_valor`: `cost` 35, `cooldownTicks` 900, `durationTicks` 200, `radius` 8, `attackDamagePct` 20, `armor` 4,
`knockbackResistance` 0.5, `sharedRadius` 16, `sharedHeal` 4, `steadfastDamagePct` 30, `masteryTicksPerRank` 20,
`pvpEffects` false.

`force_drain`: `startCost` 10, `pulseCost` 2, `pulseTicks` 10, `maxPulses` 6, `cooldownTicks` 300, `range` 10,
`aimAssistHalfAngle` 8, `pulseDamage` 2, `pulseHeal` 1.5, `pulseEnergy` 2, `lifeHealMultiplier` 2, `lifeDamageMultiplier`
0.75, `essenceEnergy` 4, `drainedCostMultiplier` 2, `masteryDamagePerRank` 0.25, `pvpEffects` false.

`force_fear`: `cost` 30, `cooldownTicks` 600, `range` 10, `coneDegrees` 60, `durationTicks` 80, `terrorRadius` 8,
`terrorTicks` 60, `nightmareTicks` 160, `nightmareDamagePct` 15, `aimAssistHalfAngle` 8, `fleeHorizontal` 16,
`fleeVertical` 7, `fleeSpeed` 1.3, `slownessAmplifier` 1, `masteryTicksPerRank` 10, `pvpEffects` false.

`force_rage`: `cost` 30, `cooldownTicks` 900, `durationTicks` 200, `meleeDamagePct` 30, `attackSpeedPct` 15,
`moveSpeedPct` 10, `damageTakenPct` 10, `unboundHeal` 1, `coldDamagePct` 20, `masteryTicksPerRank` 20, `pvpEffects` false.

`force_mind_trick`: `cost` 25, `cooldownTicks` 600, `range` 8, `aimAssistHalfAngle` 8, `durationTicks` 120,
`confuseTicks` 80, `confuseSearchRadius` 12, `massTargets` 3, `massRadius` 6, `massTicks` 80, `masteryTicksPerRank` 20,
`pvpEffects` false.

`skills` additions: `secondBarLevel` 15, `wayfarerEnergyPct` 10, `wayfarerSpeedCostPct` 20. The control kinds use the M2
key `force_push.staggerDrWindowTicks` (200).

## 16. Trees and points (9.9)

| Node | Tree | Kind | SP | Level | Notes |
|---|---|---|---|---|---|
| `force_stasis` | jedi | ability | 2 | 15 | `taught_by: ["jedi"]` (Saelen's session) |
| `stasis_field`, `stasis_lock` | jedi | branch `stasis_shape` | 1 | 16 | |
| `force_valor` | jedi | ability | granted | | `path: jedi`, `granted_only`; the Master ceremony |
| `valor_shared`, `valor_steadfast` | jedi | branch `valor_shape` | 1 | 20 | |
| `force_drain` | sith | ability | 2 | 15 | `taught_by: ["sith"]` |
| `drain_life`, `drain_essence` | sith | branch `drain_shape` | 1 | 16 | |
| `force_fear` | sith | ability | 2 | 16 | `taught_by: ["sith"]` |
| `fear_terror`, `fear_dread` | sith | branch `fear_shape` | 1 | 17 | |
| `force_rage` | sith | ability | granted | | `path: sith`, `granted_only`; the Darth ceremony |
| `rage_unbound`, `rage_cold` | sith | branch `rage_shape` | 1 | 20 | |
| `force_mind_trick` | exploration | ability | 2 | 14 | any path, no teacher |
| `mind_trick_confuse`, `mind_trick_mass` | exploration | branch `mind_trick_shape` | 1 | 15 | |
| `wayfarers_stride` | exploration | passive | granted | | `path: independent`; the Wayfarer ceremony |
| `form_makashi`, `form_djem_so`, `form_jar_kai` | saber | form | 2 each | 15, 16, 17 | W7's mechanics and numbers (`info_source: saber`) |

New purchasable points: Jedi 3 (Stasis) + 3 (Mind Trick) + 1 (a Valor branch) = 7. Sith 3 (Drain) + 3 (Fear) + 3 (Mind
Trick) + 1 (a Rage branch) = 10. The forms add 6. The trees' layouts were compacted to fit the new rows. Every
node's tooltip shows its numbers from balance (`info` keys, `NodeInfo`). Alignment opposition (the M3 `AbilityCosts`
rule) applies to the Jedi and Sith powers as to every power in those trees.

## 17. Tests (19.1, 19.2)

`ForceM5GameTests`, the spec's 16 names:

- `secondBarSlotsOpenAtLevelAndRank`
- `stasisHoldsAndBreaks`
- `stasisOnBossesQuarter`
- `diminishingReturnsOnResistantTargets`
- `fearFleesAndChokeDreadMigrated`
- `pacifyStopsTargetingUntilHit`
- `mindTrickTagsAndDialogueAction`
- `valorTouchesAllies`
- `drainPulsesHealAndRefund`
- `drainRefusedOnDroidsAndFighters`
- `rageModifiersAndNoRegeneration`
- `valorAndRageModifiersFollowTheEffect`
- `branchesExclusive`
- `masteryThresholds`
- `newNodesVisibleAndPurchasable`
- `wayfarersStrideBonuses`

How they run:

- In the lead's `arena_24x7x24`, or with no structure.
- Casters are Fabric fake players: never ticked, so energy and cooldowns stay where a cast put them.
- Where a rule reads the player list (Valor's allies, regeneration, the per-tick sync), the tests use listed mock
  players. They are ticked by hand where an effect must expire.
- Every test undoes what it starts on every path (`Cleanup`).
- Other workers' creature types are only created, never added to the level, and only when registered.

`m5/ForceClientSteps` (run by the lead's `M5ClientTest`) builds a stage 40 blocks above the player and takes 165
`second_bar_focus_held`, 166 `skills_screen_eight_sockets`, 167 `stasis_on_trooper`, 168 `drain_stream`, 169
`fear_victims`, 170 `mind_trick_calm`, 171 `rage_embers`, 172 `rage_vignette` and 173 `valor_two_players` (174 is free).
The client waits for each power's event, and the server confirms each state.

Everything the steps change is put back: data, held item, effects, camera, position and difficulty. Valor's shot needs a
second player, a mock player on an embedded connection; it goes last, and its join line is waited out of the chat.

Manual checks:

- `/oldrepublic force grant <id>`, then put the power in a slot on the Force screen.
- Hold Left Alt and press R: the second bar lights.
- Stasis a trooper and hit it once: it breaks 20 ticks later.
- Drain a poacher and walk out of range: the stream and the loop stop at once.
- Fear a group: they run, and a frightened player cannot sprint.
- Rage, then drink milk: the loop, the vignette and the modifiers go.
- Mind Trick a trooper, then hit it: it fights again.
