# Skills: trees, ability runtime, Force screen and HUD (Milestone 2)

Owner: skills worker (W3). Spec: `docs/MILESTONE_2.md` sections 7, 10 and 14.2.

## Code map

| Area | Classes |
|---|---|
| Tree data | `skill.SkillTreeLoader` (JSON → `SkillTree`/`SkillNode`), `SkillTreeSet` (lookups, validation: unknown requirements, bad ranks, cost/level arrays, single-node groups, cycles), `SkillTrees` (server set from `data/*/skill_tree/*.json`, client set from the `skill_trees` payload) |
| Rules (pure) | `skill.SkillRules`: purchase check in spec order, group switch cost, dormant/active, visible tabs, stat modifiers, respec loss/refund/block, open slots, the push branch mirror |
| Rules applied | `skill.SkillService`: learn, grant, revoke, slots, forms, respec, refresh (derived state), the Force Push migration, alignment and path reactions, `hungering_dark` |
| Read API | `skill.Skills` (contract): rank, active, activeRank, dormant, activeForm, slots, slot, data, context |
| Passives for others | `skill.SkillPassives` (centered_mind, fury, guardians_resolve, dread_presence numbers), `skill.FormChanges` (the saber feature's form-change handler), `skill.NodeInfo` (extra live-number sources) |
| Combat lock | `skill.CombatTracker` (damage dealt or taken by a player, 60 ticks) |
| Runtime | `ability.AbilityRuntime` (slot input routing, one active power, cancels), `ability.AbilityCosts` (contract: unbound, opposed, refused), `ability.AbilityInfoKeys` |
| Payloads | C2S `ability_input`, `learn_node`, `assign_slot`, `respec_confirm`, `set_form`; S2C `skill_trees`, `ability_info`, `ability_state`, `open_skills` |
| Client | `client.force.ForceKeys`, `client.ability.AbilityInput` + `ClientAbilityInfo`, `client.skill.ForceScreen` + `TreeView` + `NodeDetail` + `NodeView`, `client.skill.SkillClient`, `client.hud.ForceHud` (sockets) |
| Commands | `/oldrepublic skills list|learn|grant|revoke|respec [force]|slot|form|combat|reset|validate` |

## Saved state

Attachment `oldrepublic:skills` (`SkillData`), persistent, copy-on-death, synced to the owner:
`nodes` (id → rank), `spent` (id → points paid; what a respec refunds), `granted` (ids),
`slots` (4 ability ids, `""` empty), `open_slots`, `active_form`, `last_respec` (game time; the
spec's `respec_day`), `migrated`.

`ForceData` is touched only through `Progression.commit`: `abilities` (ability nodes add and remove
their ability), `counters` `mod_max_energy`, `mod_max_energy_pct`, `mod_regen_pct`,
`mod_regen_delay` (absent when 0), and the `pushBranch` mirror.

## Rules as built

- **Purchase** (`learn_node`): the checks of 7.3 in order, then a switch inside a group is refused
  while a power is charging or channelling or within the combat lock. The first failure answers
  with `message.oldrepublic.skill.learn.<result>`.
- **Switch:** cost = node cost + `switchPenalty`; the old node is removed with no refund (its
  `spent` entry goes too). Push: first branch 1, switch 3, as in Milestone 1.
- **Grant** (`Skills.grant`): no cost, marked granted, siblings in its group removed, a bought node
  becomes granted with no refund (its `spent` entry is cleared).
- **Dormant:** an owned non-ability node whose path or alignment requirement is not met. No effect
  (`Skills.active` false, no stat modifiers), never refunded. The player is told when nodes fall
  dormant or wake (alignment or path change). Abilities never go dormant; `AbilityCosts` applies 3.4
  (opposed: cost ×`opposedCostMult`, no mastery; refused: the runtime refuses the start with
  "The Light does not answer" / "The dark side does not answer").
- **Respec** (`respec_confirm`, or the mentor's "Unburden" dialogue action `skills:unburden`, which
  opens the screen on the confirmation): needs a mentor of the player's path within 8 blocks (Jedi:
  `oldrepublic:jedi_mentor`, Sith: `oldrepublic:sith_mentor`) or, for Independents and before the
  Crossing, a meditation stone; not in combat; at most once per 72,000 ticks. Refund = spent − max(1,
  ⌈spent × 10%⌉). Granted nodes and slots of granted powers stay; the form falls back to Unformed
  unless it was granted.
- **Slots:** 3 open; the 4th opens at Identity rank ≥ 2 or with the `resonance:first_rank` flag
  (covers No Master). A new power takes the first free slot (a newly opened slot is filled too).
  Assigning swaps; clearing is assigning `""`. Refused within the combat lock.
- **Push migration** (once per player, on join and every second until the trees have loaded):
  Push known → `force_push` granted; `pushBranch` set and no `push_*` owned → that node at no cost,
  recorded as 1 spent point (so a respec refunds the point the player paid in Milestone 1); Push in
  slot 1.
- **Stat modifiers:** `deep_reserves` +8 max FE per rank, `quickened_flow` +10% regen and 5 ticks
  less regen delay per rank, `calm_focus` +10% max FE and 10 ticks less delay. Recomputed on every
  change and once a second (so alignment drift and dormancy apply).
- **hungering_dark** is applied here (kill of an `Enemy` restores FE, cooldown key `hungering_dark`).
  `centered_mind`, `fury`, `guardians_resolve` and `dread_presence` are applied by the force and
  saber features through `SkillPassives`.

## Ability runtime

- Keys: slot 1 R (`key.oldrepublic.use_force_power`, Milestone 1 binding id), 2 Z, 3 V, 4 B,
  Force screen K, Saber Form unbound. `ForceKeys.init()` is idempotent.
- Client (`AbilityInput`): press → `START`, release → `RELEASE` for the slot that started a hold or
  channel; a screen opening, death or spectator → `CANCEL`. Pressing another slot while a power is
  active sends `START` for that slot, which the server turns into "cancel the active power, do
  nothing else"; the client drops its prediction. A `ClientAbilityHook.precheck` can swallow a press;
  hooks get `onPress`/`onRelease`/`onCancel` for prediction. A low-energy press flashes the bar at once.
- Server (`AbilityRuntime`): START refused for an unopened slot, an empty slot, an unregistered or
  unlearned power, or alignment refusal. RELEASE/CANCEL only reach the power that the key started.
  `ability_state` tells the owner when a charge or channel really starts and stops (drives the HUD
  arc and the crosshair brackets). Cancels on death, dimension change, disconnect, slot change, node
  removal and respec.
- `ability_info`: every ability's input style and `info()` map plus the `NodeInfo` sources
  (`skills` here; `saber` from the saber feature), sent on join and after `/reload`.

Info key conventions (shared with the force and saber features): durations in seconds with `_s`
(or `_seconds`), percentages 0-100 with `_pct`, multipliers `_mult`, degrees for cones and arcs,
blocks for ranges; cost keys `cost` (tap), `cost_min`/`cost_max` (charged), `start_cost` +
`cost_per_s` (channel); `cooldown_s`, `min_charge_s`, `full_charge_s`, `charge_s`, `max_channel_s`.
The screen picks the unit from the suffix (`NodeDetail.unitKey`).

## Force screen (client/skill/ForceScreen)

Durasteel panel up to 460×260, same kit and header as the Milestone 1 screen.

- Header: emblem with the XP ring (after the Crossing tinted up to 35% toward pale gold or deep red
  by how far alignment leans), title, level and XP bar. Stat strip: Resonance (with the drifting
  notch script), Force Energy with a mini bar, skill points with pips, path and alignment. One line
  on small windows.
- Tabs (`TabStrip`): one per visible tree in order, Jedi with a pale gold cap, Sith deep red, and
  Standing after the Crossing (W1's `StandingPanel`, render and clicks forwarded).
- Tree view (about 60%): sockets from `pos`, scaled to fit; links lit along owned paths; exclusive
  groups fork from an "or" diamond; rank pips under multi-rank nodes; "Jedi path" labels under path
  nodes the player is not on; a hover tooltip with the name and the first failed requirement (or
  why it is dormant); a bright ring flashes around a node just learned.
- Detail panel (about 40%, clipped and scrollable): icon, name, kind and tree, rank pips, state with
  reason, description, live numbers (`ability_info`), cost modifier, opposed/refused note, mastery
  rank and points to the next, requirements with ✔/✘, warnings ("your mentor will teach this",
  "replaces X, not refunded"). Buttons: Learn / Switch / Next rank (n SP) with the failure reason as
  tooltip; for an owned power "Slot 1 2 3 4" (the current slot highlighted; pressing it clears);
  for a form "Set active form" / "Return to Unformed". Number keys 1-4 slot the selected power.
- Footer: four slots with their keys (locked slot 4 with a padlock and the reason), drag a power
  from the tree or a slot onto a slot, drag a slot off the bar to clear it; Respec (disabled with the
  reason as tooltip, checked twice a second) opens an in-panel confirmation card with the refund,
  the loss and what stays; Done.
- Sounds: `skill.learn` (pitch rises with the rank) from the `node_learned` effect, `skill.slot`
  when a slot changes while the screen is open, `skill.respec` from the `respec` effect, which also
  opens the screen. A node learned while the screen is closed shows a banner ("Learned" / "Taught").

## HUD (client/hud/ForceHud)

Under the energy plate, one 18×18 socket per slot that holds a power (fades and drops in when a slot
fills, positions fixed by slot): icon, key label under it, cooldown sweep with the seconds left and
the frame relighting clockwise, a ready glint, an amber pulsing rim when energy is below the cost
(base cost × mastery × skill and alignment multipliers), a charge arc (hold-and-release) or a
draining channel arc, and a red cross-hatch while alignment refuses the power. The energy notch
marks the cheapest slotted power. Crosshair brackets show the charge of any held hold-and-release
power and drain with its cooldown after release. `HudClient.clusterBottom()` gives the cluster's
lowest y for toasts.

## Effects and sounds (12)

| Moment | Effect | Treatment |
|---|---|---|
| Node learned | `node_learned` (toPlayer, intensity = rank, variant = tree order) | Client: `skill.learn` (crystal click, rising swell; +6% pitch per rank above 1). Screen: bright double ring fading around the node over 24 ticks (steady with flashing off is unchanged: no strobe). Closed screen: banner. No world particles (a menu action). |
| Slot assigned | – (synced data) | `skill.slot` (two dry clicks) while the screen is open. |
| Respec | `respec` (toPlayer, intensity = refund) | `skill.respec` (falling chimes over a fading pad), Force screen opens on the tree. |

## Balance (`skills` section)

`switchPenalty` 2, `respecLoss` 0.10, `respecCooldownTicks` 72000, `combatLockTicks` 60,
`baseSlots`/`maxSlots` 3/4, `respecAnchorRadius` 8, `deepReservesPerRank` 8,
`quickenedRegenPctPerRank`/`quickenedDelayPerRank` 10/5, `centeredParryTicksPerRank` 1,
`calmFocusDelay`/`calmFocusMaxEnergyPct` 10/10, `guardiansResolveReduction`/`Radius` 0.10/6,
`furyPerRank`/`furyHealthFraction` 0.06/0.5, `hungeringEnergy`/`hungeringCooldownTicks` 6/40,
`dreadFleeTicks` 40, `unboundCostMult`/`unboundCooldownMult` 0.85/0.9, `opposedCostMult` 1.5.

## Tests

- `SkillGameTests`: tree data and registered abilities, cycle/unknown requirement detection, codec
  round trip, purchase matrix, group switch, dormant and recovery (plus opposition bands), respec
  rounding/cooldown/granted kept, slots and combat lock and slot 4, Push migration, stat modifiers in
  `Progression`.
- `m2/SkillClientSteps`: mod key default clash check; screenshots `oldrepublic_m2_30..37`.

## Manual checks

- `/oldrepublic skills validate` after editing tree JSON; `/reload` re-sends the trees.
- Relog with a Milestone 1 save that chose a Push branch: branch node owned, Push in slot 1.
- Hold slot keys while opening a screen, dying, changing dimension: nothing keeps charging.

---

# Milestone 3: the Exploration tree, the granted holocron nodes and a slotless Sense

Owner: W3 (Part A). Spec: `docs/MILESTONE_3.md` sections 10.1 to 10.5, 11.2 and 14. Everything above still holds; this part only adds. The powers themselves (Force Sense, Telekinetic Lift, the dispatch order) are described in `docs/design/force.md` sections 19 to 28.

## Code map (Milestone 3)

| Area | Classes |
|---|---|
| Tree data | `data/oldrepublic/skill_tree/exploration.json` (new; order 4, tint `#C9B88A`, visibility `sense`); `jedi.json` and `sith.json` gain row 3 with four granted nodes each |
| Rules | `SkillRules.slotless(abilityId)`: Force Sense never takes a slot. `SkillService.nodeAdded` skips slotless powers when it fills the first free slot, the derived-state refresh does too, and `assignSlot` refuses them with the overlay `message.oldrepublic.skill.slot.slotless` (naming the Sense key) |
| Attribute passives | `SkillAttributes`: `trailwise` and `pathfinders_poise` (fall damage), `hardy` (max health), `pathfinders_poise` (speed out of combat). Re-applied with the derived state every 20 ticks, after a node change, and when the player enters combat |
| Combat node effects | `SkillNodeEffects`: `watchers_insight` (a `ForceModifier`), `tomb_hunger`, `crackling_grasp`, `archivists_hunger`, `scavenger` |
| Energy refunds | `ability.EnergyRefunds`: every kill and parry refund (`hungering_dark`, `tomb_hunger`, armour set bonuses) goes through it, capped per kill (`progression.killRefundCap`) and per parry (`progression.parryRefundCap`) |
| Passives for others | `SkillPassives.sprintExhaustion` (`trailwise`, through `mixin/skill/PlayerExhaustionMixin` on `Player#causeFoodExhaustion`); `quiet_step` for W4 and W5: `sentinelWakeRadius(player, base)`, `waryWarnRange(player, base)`, `waryAttackTicks(player, base)` |
| Live numbers | `NodeInfo.explorationValues` (info source `skills`): every Exploration and granted-node number shown in the node detail |
| Cost preview | `AbilityRuntime.infoPayload(player)` adds the `cost_modifiers` entry (per ability, `ForceModifiers.cost × ForceReactions.costMultiplier`, rounded to thousandths); `refreshCostModifiers` re-sends it when it changed, checked every 20 ticks by `SkillFeature` |
| Client | `ForceScreen` (slotless Sense: the node shows its own key as a button, no drag, no number key; lang `screen.oldrepublic.skills.sense_key`), `ForceHud` (Sense pip), `ClientAbilityInfo` (reads the `cost_modifiers` entry) |

## The Exploration tree

Shown once the player knows Force Sense (`TreeVisibility.SENSE`), as the fourth tab. Its colour is weathered brass and sand.

| Node | Kind | Cost / level | Requires | Effect |
|---|---|---|---|---|
| `force_sense` | ability, granted only (hint `force_sense`) | – | – | Force Sense, on its own key (H) |
| `trailwise` | passive ×2 | 1, 1 / 7, 10 | `force_sense` | Fall damage −20% / −40%; sprint hunger −15% / −30% |
| `sense_danger` | branch `sense_shape` | 1 / 8 | `force_sense` | Danger Sense: a prickle and an edge tick for attack tells behind you (12 blocks, outside 100°, once per second) |
| `sense_seeker` | branch `sense_shape` | 1 / 8 | `force_sense` | Seeker's Sight: holocron search 512 blocks, reveal +4 s, pedestal and cache glints within 24 (W1's provider) |
| `relic_lore` | passive | 1 / 8 | `force_sense` | +25% first-attunement XP (the holocron feature, `HolocronRewards`), unsolved puzzle blocks among the Sense points (W1) |
| `hardy` | passive ×2 | 1, 2 / 9, 12 | `trailwise:1` | Max health +2 / +4 (saved modifier) |
| `scavenger` | passive | 1 / 9 | `trailwise:1` | 35% chance of one extra material from a trooper (`durasteel_plate`) or sentinel (`lumen_thread` or `grave_silk`) kill |
| `quiet_step` | passive | 2 / 11 | `force_sense` | Dormant sentinels wake at 4 blocks; wary troopers warn at 10 and hold fire at least 10 s (applied by W4 and W5 through `SkillPassives`) |
| `force_lift` | ability, granted only (hint `force_lift`), path `independent` | – | – | Telekinetic Lift (a slot power) |
| `lift_suspend` | branch `lift_shape` | 1 / 9 | `force_lift` | Hold 5 s; allies deal +20% to the held creature |
| `lift_hurl` | branch `lift_shape` | 1 / 9 | `force_lift` | Release throws the target 10 blocks (6 damage, 1 s stagger) |
| `archivists_road` | passive, granted only (hint `archive_kept`) | – | – | Sense cooldown −30%, Lift cost −10% |
| `pathfinders_poise` | passive, granted only (hint `pathfinder`) | – | – | +10% speed out of combat, fall damage −25% |

All fall reductions together never exceed 60% (`fallReductionCap`).

## Granted holocron nodes (row 3 of the side trees)

These are granted only: holocrons, the Archive, and the rank-three story beat. Like every side node, they are dormant while the alignment is outside their band.

| Node | Tree | Band | Effect | Applied by |
|---|---|---|---|---|
| `watchers_insight` | Jedi | ≥ +15 | Force Heal costs 10% less | W3 (`ForceModifier`) |
| `mending_breath` | Jedi | ≥ +15 | Each heal tick draws Slowness, Weakness and Poison out of the caster and Shared Light allies (`force_heal` variant 3, grey-green wisps) | W3 (`ForceHeal`) |
| `archivists_ward` | Jedi | ≥ +15 | Archive Keep reward (static text) | W5 (`SaberNodes`) |
| `knights_vigil` | Jedi, path `jedi` | ≥ +15 | Knight rank (static text) | W5 (`SaberNodes`) |
| `tomb_hunger` | Sith | ≤ −15 | A kill within 3 s of hitting that target with a power restores 6 FE (shares the per-kill cap with `hungering_dark`) | W3 (`SkillNodeEffects`, `EnergyRefunds`) |
| `crackling_grasp` | Sith | ≤ −15 | After Force Lightning ends, everything its arcs struck is 30% slower for 2 s | W3 (`SkillNodeEffects`, called by `ForceLightning`) |
| `archivists_hunger` | Sith | ≤ −15 | Push and Lightning deal +15% to targets above 75% health | W3 (`SkillNodeEffects`, asked by `PushEffects` and `ForceLightning`) |
| `lords_dominion` | Sith, path `sith` | ≤ −15 | Lord rank (static text) | W5 (`SaberNodes`) |

## Sense on its own key

- The `force_sense` node teaches the ability but never fills a slot: a new ability node takes the first free slot unless it is slotless, and a manual assignment answers `message.oldrepublic.skill.slot.slotless`.
- The H key (`key.oldrepublic.force_sense`) sends the ability input for `AbilityRuntime.SENSE_SLOT` (8). The server routes a press to `force_sense` and ignores the release. Unlike a slot key, it does not cancel an active power; Sense refuses instead.
- **HUD:** the Sense pip sits right of the sockets (`skill/sense_pip`), with the key's name under it. A cooldown sweep relights it from the bottom, it glints briefly when ready, and it pulses amber when energy is below the cost. It appears only once Sense is known.
- **Force screen:** the Sense node's detail shows "Own key: H" (the bound key, `screen.oldrepublic.skills.sense_key`) instead of slot buttons.

## Effects and sounds (Milestone 3)

The skills feature adds no events or sounds of its own. Node learning uses the existing `node_learned`, `skill.learn` and `skill.slot`. The new node icons are listed in `docs/assets/skill.md`. `mending_breath`'s wisps belong to Force Heal (`docs/design/force.md` 22.3).

## Balance (`skills` section, Milestone 3 keys)

- Exploration:
  - `trailwiseFall` [0.2, 0.4], `trailwiseHunger` [0.15, 0.3];
  - `hardyHealth` [2, 4];
  - `scavengerChance` 0.35;
  - `quietStepWake` 4, `quietStepWarn` 10, `quietStepWaryTicks` 200;
  - `pathfinderSpeed` 0.10, `pathfinderFall` 0.25, `fallReductionCap` 0.6.
- Granted nodes:
  - `watchersInsightHealCost` 0.10;
  - `tombHungerFe` 6, `tombHungerWindowTicks` 60;
  - `cracklingSlow` 0.3, `cracklingTicks` 40;
  - `archivistsHungerBonus` 0.15, `archivistsHungerThreshold` 0.75;
  - `archivistsRoadSenseCooldown` 0.3, `archivistsRoadLiftCost` 0.1.
- `relic_lore`'s 25% is `holocron.relicLoreXpBonus`, applied by the holocron feature (`HolocronRewards`).
- The per-kill and per-parry refund caps are `progression.killRefundCap` (12) and `parryRefundCap` (6), the lead's `ProgressionBalance`.

## Tests (Milestone 3)

- `SkillGameTests.explorationTreeAndSlotlessSense` checks:
  - the tree loads with 13 nodes and visibility `sense`, and the tab shows only once Sense is known;
  - Sense is a granted-only ability and is slotless, while Lift takes a slot;
  - the six granted-only passives;
  - a granted Sense is never auto-slotted, `assignSlot` refuses it, and the Exploration tab becomes visible.
- `ForceGameTests.grantedNodeEffects` checks:
  - `watchers_insight` (0.9 through `ForceModifiers` and `cost_modifiers`; dormant at alignment 0);
  - `tomb_hunger` (+6, and nothing for an unmarked kill);
  - `crackling_grasp` (speed ×0.7, ended after 2 s);
  - `archivists_hunger` (1.15, then 1.0 at half health);
  - the `trailwise` fall and sprint hunger;
  - the `mending_breath` cleanse (and none without the node);
  - the pure `EnergyRefunds.fit`, `scavengerDrop`, `tombHungerDue` and `SkillAttributes.values` (including the 60% cap).
- Screenshot `oldrepublic_m3_34_exploration_tree_tab` (`m2/ForceClientSteps.runM3`).

## Manual checks (Milestone 3)

- `/oldrepublic skills grant force_sense`: the Exploration tab appears, no slot changes, and the pip shows under the sockets. Press H to cast.
- `/oldrepublic skills grant hardy` (or learn it): the health bar gains a heart. Relog: it is still there. Revoke it: the extra health goes at the next refresh.
- Sprint with `trailwise` and compare the hunger drain. Fall 10 blocks with `trailwise` 2 and `pathfinders_poise` and check that the damage is cut by 60%, not 65%.
- Kill a trooper with `scavenger` a few times: an extra durasteel plate sometimes drops, and never commendations.

# Milestone 5: the second power bar

The full design is in `docs/design/powers_m5.md` (sections 3 and 16). In short, for this code:

- `SkillRules.openSlots2(level, rankFour, b)` gives 2 from level 15 (`skills.secondBarLevel`) and 4 at rank 4.
  `SkillService.rankFour` counts Master and Darth (`Identity.rank >= 4`) and the Wayfarer title (`title.wayfarer`).
- `SkillService.applyDerived` sets `openSlots` and `openSlots2` on every refresh. A slot that closes is emptied and its
  power cancelled. A slot that opens takes an owned power without a slot, in tree order. While Force Rage lasts, it
  also sets the regeneration modifier to -100%.
- `AbilityInput`: Focus (Left Alt) turns R, Z, V and B into slots 5 to 8, and the bar is chosen when the key goes down.
  `ForceHud` draws the second row above the first; `ForceScreen`'s footer strip has eight sockets with the Focus
  caption between the bars, and number keys 1 to 8.
- `wayfarers_stride` (Wayfarer's Grace) is in `SkillRules.statMods` (+`skills.wayfarerEnergyPct`% maximum Force Energy)
  and `SkillNodeEffects` (Force Speed costs `skills.wayfarerSpeedCostPct`% less).
- New tree nodes: 22, in `jedi`, `sith`, `exploration` and `saber` (the forms for W7). Their tooltips' numbers come
  through `NodeInfo` from the six power balance sections.
- Tests: `ForceM5GameTests.secondBarSlotsOpenAtLevelAndRank`, `branchesExclusive`, `newNodesVisibleAndPurchasable`
  and `wayfarersStrideBonuses`.
