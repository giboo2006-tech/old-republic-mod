# Sabers, crafting and gear, Milestone 5: forms, dual-wielding, blade length, crystal tuning, hum profiles, the Legendary tier and the rank 4 robes

Owner: W7 (sabers, crafting and gear). Spec: `docs/MILESTONE_5.md` sections 10 (all), 11 (all), 8.5 and 12.3 (the
robes, the mantle and the Cantor's Mask, which W5 and W8 hand out), 15 (W7's effects and sounds), 16.4 (the `saber` and
`crafting` additions), 18 (W7's ids), 19 (`SaberM5GameTests`, screenshots 175 to 184) and 22 (revision notes 16, 21, 22,
26, 28, 31, 69 to 71, 74 and 120). The M1 to M4 saber, crafting and gear designs these build on are in
`docs/design/saber.md`, `crafting.md`, `equipment.md` and `gear.md`. The assets are in `docs/assets/sabers_m5.md`.

Nothing was cut. The three W7 items on the spec's cut list are all in: hum profiles (cut order 4), blade-length variants
(cut order 5) and the Jar'Kai form (cut order 6).

API status: every vanilla and Fabric call is verified against the 26.2 dumps, the Fabric 26.2 source or committed code,
except the three marked `// API-CHECK:` and listed in section 15.

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Forms (3) | `MAKASHI("form_makashi")`, `DJEM_SO("form_djem_so")`, `JAR_KAI("form_jar_kai")` | `saber/SaberForm` (the enum and the lead's appends; W7 wrote the cases). The nodes (kind `form`, 2 SP, levels 15, 16 and 17) are in W6's `skill_tree/saber.json`, written to W7's request |
| Legendary saber parts (3) and lens | `emitter_cantor`, `grip_sunforged`, `pommel_hask`; `lens_stormglass` | `HiltParts.Emitter.CANTOR`, `Grip.SUNFORGED`, `Pommel.HASK` (lead appends), `SaberLens.Lens.STORMGLASS`; items in `saber/SaberItems` |
| Legendary mods (4) | `mod_clan_iron_plating`, `mod_hymnweave_lining`, `mod_storm_cell`, `mod_sunstone_edge` | `equipment/mod/LegendaryMods`; effects in `equipment/mod/ModEffects` |
| Legendary ship parts (2) | `spindle_capacitor`, `hymn_tuned_drive` | `crafting/LegendaryItems`, class `crafting/item/LegendaryShipPartItem` (fitted by W1's `ship:fit`; effects by W2 and W1) |
| Materials (2, items are the lead's) | `hymnstone_shard`, `clan_iron` | `registry/M5Items`; W7 made their textures and lang |
| Rank 4 pieces (3) and the mask | `jedi_master_robe`, `sith_darth_robe`, `wayfarer_mantle`; `cantors_mask` | `equipment/EquipmentItems` (`JEDI_MASTER_ROBE`, `SITH_DARTH_ROBE`, `WAYFARER_MANTLE`, `CANTORS_MASK`), `equipment/CantorsMaskItem`; `ArmourSets.rankFourRobe(Path)` for W5 |
| Equipment assets (4) | `oldrepublic:jedi_master`, `sith_darth`, `wayfarer`, `cantors_mask` | `EquipmentMaterials.*_ASSET`; `assets/oldrepublic/equipment/*.json` |
| Schematics (11) | `concussion_missile` (Armstech 3); `emitter_cantor_legendary`, `pommel_hask_legendary`, `grip_sunforged_legendary`, `lens_stormglass_legendary`, `mod_clan_iron_plating_legendary`, `mod_hymnweave_lining_legendary`, `mod_storm_cell_legendary`, `mod_sunstone_edge_legendary`, `spindle_capacitor_legendary`, `hymn_tuned_drive_legendary` (the last two `common`) | `data/oldrepublic/schematic/` |
| Payload (C2S) | `oldrepublic:offhand_strike` (`int targetId`, -1 for a swing at nothing) | `saber/network/OffhandStrikePayload`, received in `SaberM5Feature` |
| Attachment | `oldrepublic:saber_stance` (`Integer`, the chosen form's ordinal; synced to all, not saved) | `saber/SaberStances` |
| Effect events | `offhand_strike` (toTracking), `crystal_tune` (toPlayer); `bench_craft` variant `CRAFT_LEGENDARY` = 5 (around); `player_blaster_fire` variant `STORM_ARC` = 8 (around); `lesson_complete` variant 14 sent (W4 draws the banner) | `SaberEffects`, `CraftingEffects`, `GearEffects`; client `SaberM5Fx`, `CraftingFx`, `GearEffectsClient` |
| Sounds (14) | `saber.{guardian,duelist,reliquary,cantor}.{ignite,hum,retract}`, `saber.offhand_swing`, `bench.legendary`; reused `workbench.tune` | Phase 0 `ModSounds` names; recipes `tools/assets/sounds/saber_m5.py` |
| Codex unlocks | `dual_blades` (first off-hand strike), `legendary_components` (first legendary schematic learned) | `SaberDual.strike`, `Crafting.teach`, through `Journal.unlockCodex` |
| Attribute modifier id | `oldrepublic:form_speed` (transient, `ADD_MULTIPLIED_TOTAL`) | `SaberForms.FORM_SPEED_ID` |
| Node info values (saber source) | `makashi_*`, `djem_so_*`, `jar_kai_*`, `offhand_damage_pct`, `offhand_recovery_pct` | `SaberPassives.nodeInfo` (the `info` keys of W6's form nodes) |
| Balance | `saber` and `crafting` additions of 16.4, every key with the spec's default | `SaberBalance`, `CraftingBalance` |
| Lang | `saber.json` (+53: tunings, lengths, the Tune tab, the length switch, part and lens names), `crafting.json` (+15), `equipment.json` (+17) | `src/main/lang-parts/` |

## 2. Classes

New:

- `saber/SaberDual`: dual-wielding (who dual-wields, the off hand's recovery clock and damage, the `offhand_strike`
  checks and the blow). Pure overloads `offhandTicks(...)` and `offhandDamage(double, double, boolean, b)` for the tests.
- `saber/CrystalTuning`: the five tunings (stored by id, the effect sends the ordinal), their materials and the count
  of a config's crystals that carry one.
- `saber/SaberTunings`: the Tune tab's rules (check, pay, retune a loose crystal or a saber's crystal) and the Attuned
  tuning's `ForceModifier`.
- `saber/SaberHums`: which ignition, hum and retraction a saber plays.
- `saber/SaberStances`: the synced stance attachment.
- `saber/network/OffhandStrikePayload`.
- `crafting/LegendaryItems`, `crafting/item/LegendaryShipPartItem`, `equipment/mod/LegendaryMods`,
  `equipment/CantorsMaskItem`.
- Client: `client/saber/SaberDualClient` (the off-hand strike), `SaberM5Fx` (the two effects), `SaberStance` (the
  stance poses).

Changed (the larger changes): `SaberForm` (the cases and `Modifiers.Traits`), `SaberForms` (form speed, stance sync,
`selectedForm` for changes), `SaberPassives` (`form` vs `selectedForm`, the two-blade parry tick, node info),
`SaberCombat` (the forms' damage and riposte, the `OFFHAND` technique, the striking hand's lens), `SaberGuard`
(Makashi's bolt drain, Djem So's stagger and returns), `SaberStats` (legendary parts, length, tunings, the
`attack_range` component), `SaberToggle` (both blades, the emitter's sounds, `SaberStats.apply`), `SaberLens` (the
stormglass lens), `SaberItems` (the legendary parts and lens), `LightsaberWorkbenchMenu` (the length switch and the Tune
buttons), `Crafting`, `BenchService`, `EngineeringBenchMenu`, `Salvage`, `SchematicLoader`, `LegendaryDrops`,
`CraftingEffects`, `ModEffects`, `Mods`, `EquipmentModifiers`, `EquipmentEvents`, `Blasters`, `MeleeCombat`,
`EquipmentItems`, `EquipmentMaterials`, `ArmourItem`, `ArmourSets`, `ArmourAttributes`. Client: `SaberActionsClient`,
`SaberAudio`, `SaberHumSound`, `SaberSingingSound`, `SwingTrailRenderer`, `ThrownSaberFx`, `SaberModelProperties`,
`LightsaberWorkbenchScreen`, `CraftingFx`, `CraftingToasts`, `EngineeringBenchScreen`, `GearEffectsClient`,
`GearTooltips`, `RobeModel`, `RobeArmorRenderer`, `EquipmentClient`, and the two saber mixins (stance).

`SaberM5Feature.init` registers the legendary items, the payload, the stance attachment and the Attuned modifier, and
clears the off hand's clock on leave, respawn and level change. `SaberM5Client.init` registers the two effects; the
length property registers with the M1 saber model properties (`SaberModelProperties`).

## 3. The three forms (10.1)

`SaberForm.Modifiers` keeps its sixteen M2 fields and gains a seventeenth, `Traits` (the M5 forms' own numbers); the
old sixteen-argument constructor stays and passes `Traits.NONE`, so no M2 code changed. Makashi, Djem So and Jar'Kai
start from Unformed's numbers and change only what the spec names.

| Form | What it does, and where |
|---|---|
| **Makashi** | Parry window +2 ticks (flat, after the form multiplier, under the M3 cap of 11). A parry arms Makashi's riposte: the first saber strike within 20 ticks deals +40% (`SaberCombat.onParry`). With the M2 `riposte` passive too the two never stack: the larger bonus, over 30 ticks. +15% damage against any `SaberDuelist`. Blaster bolts drain the guard 20% more (deflected bolts and blocked heavy bolts; a parry drains nothing, the M2 rule). |
| **Djem So** | Heavy strikes ×1.2 (the staff's whirl is its heavy) and 2 ticks less wind-up (never under 1). A parried melee attacker is also staggered for 10 ticks (`ControlEffects.stagger`, with its diminishing returns). A deflected, unparried bolt flies back at its shooter 60% of the time (parried bolts already go back). Attack speed -5%. |
| **Jar'Kai** | Only while dual-wielding: `SaberPassives.form(p)` reports Unformed otherwise, while `selectedForm(p)` keeps the choice (form changes and the cycle key read the choice). Off-hand strikes take no penalty; attack speed +10%; guard stamina +15 (flat, after the multipliers); parry window -1 tick. |

- **Attack speed:** a transient `oldrepublic:form_speed` modifier (`ADD_MULTIPLIED_TOTAL`, the percentage over 100)
  while the form is in effect and a lit saber is in the main hand, kept current by the server tick beside the M2 Ataru
  modifier; only a change is written.
- **Changing form** keeps the M2 rules: 20 ticks to settle and refused for 60 ticks after combat.
- **Tooltips:** the form nodes' `info` lists name values that `SaberPassives.nodeInfo` adds to the `saber` source
  (W6 wrote the nodes, their names and the info labels).

### 3.1 The stances (19.2)

The skills attachment that holds the chosen form is synced to its owner only, so `SaberStances` keeps a small
attachment, `oldrepublic:saber_stance` (the chosen form's ordinal, synced to every client, not saved: the server tick
sets it again after login or respawn; only a change is sent). The client reads it through `SaberStances.stance(entity)`,
which shows Jar'Kai only with two lit blades.

`client/saber/SaberStance` poses the arms in third person, after the vanilla animation and before any technique pose
(`HumanoidModelSaberMixin`; `LivingEntityRendererSaberMixin` copies the stance into the render state):

- Makashi: the blade forward and up in one hand, the free hand tucked behind the back.
- Djem So: a high guard, the blade drawn up over the shoulder, the free hand joining it.
- Jar'Kai: the main blade high, the off blade low and out.
- Any dual-wielder: the off arm in a low guard while it is not swinging.

An arm that is swinging or guarding keeps the vanilla pose; nothing is posed while riding, gliding, swimming or asleep.
A technique pose wins over the stance (the off arm keeps its guard). The stance also gives way to W6's Force poses,
which are applied at the same point of the animation (`SaberStance.weight`): it is not drawn while a power or Push
pose, a Lift flail, a Mind Trick calm or a Fear shiver shows, and it fades out as Force Rage's hunch comes in, so it
never hides them whichever mixin runs first.

## 4. Dual-wielding (10.2)

A player dual-wields with a single lightsaber in each hand, both lit and neither thrown (`SaberDual.isDualWielding`;
training sabers count, a saberstaff never does).

- **Drawing:** the Toggle Lightsaber key (U by default since revision note 120) lights or puts out both together:
  both light when either is dark, else both go out. Each blade keeps its colour, crystal, length, tunings and hum.
- **Striking (client):** `SaberActionsClient.onPreAttack` (Fabric's `ClientPreAttackCallback`) asks
  `SaberDualClient.tryStrike` once heavy and lunge have passed. With two lit blades, the main hand still recovering
  (`getAttackStrengthScale(0.5f) < 1`) and the own off-hand clock run out, the off hand takes the press: it swings at
  once (the swing fields are set directly, so it follows the main hand's swing without waiting half a swing), picks its
  target with the off saber's own `attack_range` (`AttackRange#getClosesetHit`), and sends `offhand_strike`; the
  vanilla attack is swallowed. A press at a block is left to vanilla (mining). A press at the air still swings and
  starts the clock.
- **Striking (server, `SaberDual.strike`):** refused when not dual-wielding, dead or a spectator; while using an item,
  a technique runs or the form is changing; while the off hand's clock runs (1 tick of slack for the client running
  ahead); for a target that is not a living, attackable, visible entity other than the striker; and beyond the off
  saber's reach, `Player#isWithinAttackRange(offStack, box, 1.0)` (the off saber's `attack_range`, with 1 block of
  tolerance for movement between the client's pick and the check; vanilla allows the main hand 3). Then: the off hand
  swings for everyone (`offhand_strike`), its clock starts, and the blow goes through `SaberCombat.strike` as an
  `OFFHAND` technique with the player's attack damage source, so guards, spars, Force reactions and the striking
  saber's own lens all see which hand struck. The first strike unlocks the `dual_blades` codex entry.
- **Numbers:** the off-hand blow deals the off saber's damage (its own parts, length and tunings) plus whatever the
  player's attack damage carries beyond the main saber's (effects), times `offhandDamage` (0.7). Its recovery is 20 ticks
  over the off saber's attack speed, scaled by the ratio of the player's attack speed to the main saber's (so the form
  and effects count for both hands), times 1 + `offhandSpeed` (1.2). Jar'Kai removes both penalties.
- **Techniques:** finishers, heavies, lunges and Saber Throw use the main hand only. Guarding with two lit blades adds
  `dualParryTicks` (+1) to the parry window, under the cap.
- **Animation:** first person uses vanilla's off-hand swing. Third person: the off arm's low guard (3.1), the swing
  trail in the `OFFHAND` style on the off arm's side in the off blade's colour, and `saber.offhand_swing` instead of the
  usual swing sound.
- **Clock life:** the off hand's clock is cleared on leave, respawn and level change, and on the client on disconnect.

## 5. Blade length (10.3)

`SaberConfig.length` (lead, Phase 0): 0 standard, 1 short, 2 long. The workbench's Assemble tab has a three-way switch
in the projector's corner for a built single lightsaber of the player's own (button `BUTTON_LENGTH + length`, free); the
projected blade follows it. Staffs and training sabers stay standard.

| Length | Reach | Attack speed | Damage | Blade drawn |
|---|---|---|---|---|
| Short | -0.5 | +10% | -10% | ×0.7 |
| Standard | | | | ×1 |
| Long | +0.5 | -10% | +5% | ×1.15 |

- `SaberStats.withLength` applies the row after the parts; tunings come after the length.
- **Reach:** every saber carries an `attack_range` component (`SaberStats.attackRange`), rewritten with its attribute
  modifiers by `SaberStats.apply` whenever the config or the ignition changes (`SaberToggle`, the workbench, tunings,
  the commands): survival reach 3 plus the saber's reach bonus while lit (3 alone retracted), creative reach 2 more, no
  hitbox margin, and a mob factor that keeps a mob's reach at vanilla's default, so saber NPCs fight as before. The
  `ENTITY_INTERACTION_RANGE` modifier stays for interaction and is built for any non-zero bonus (negative for a short
  blade).
- **Drawing:** the item model selects on the `oldrepublic:blade_length` property (`SaberModelProperties.BladeLength`:
  `short`, `standard`, `long`). Short blades have shorter elements. The long blade would pass the model's y limit of 32,
  so its models use display parents stretched ×1.15 along the blade (`base_long`, `guard_base_long`), with elements
  authored so the stretched blade still starts at the emitter. The swing trail and a thrown saber's blade scale with
  `SaberModelProperties.bladeScale(stack)`.

## 6. Crystal tuning (10.4)

The workbench screen has two tabs in its header. **Assemble** is the M4 screen with the length switch. **Tune** keeps
the sockets and the inventory, and replaces the projector and the numbers with the tuning console: which crystal (the
loose crystal, the loose second crystal, or either crystal of the saber on the bench), its tuning now, the five tunings
with their effects and materials, the cost against what the player carries, and a Tune button that is lit only when the
server will accept (the client checks what the server checks).

| Tuning (id) | Effect | Material, plus 100 credits |
|---|---|---|
| Keen (`keen`) | +5% damage | 2 `sunstone` |
| Swift (`swift`) | +6% attack speed | 2 `sunstone` |
| Poised (`steady`) | +1 parry tick (flat, before the form multiplier, under the cap of 11) | 2 `stormglass_shard` |
| Warding (`warding`) | +15 guard stamina | 2 `stormglass_shard` |
| Attuned (`resonant`) | Force powers cost 5% less while the saber is lit | 1 `hymnstone_shard` |

- **Rules (`SaberTunings.check`, server side):** a crystal must be there (`NO_CRYSTAL`), attuned (`UNATTUNED`: a
  singing crystal attunes only when a saber is built around it), the player's own (`NOT_YOURS`), not already tuned so
  (`ALREADY`), and the player must carry the material (`NO_MATERIALS`) and the credits (`NO_CREDITS`). Each refusal
  has its own action-bar line. Payment takes the credits, then the material; if the material cannot be taken the
  credits come back.
- **Storage:** `CrystalAttunement.tuning` (lead, Phase 0; equality includes it, note 74). The tuning travels with the
  crystal through disassembly and reassembly. Retuning replaces the old tuning; nothing is refunded. Colours never
  change.
- **Counting:** a saberstaff's two crystals tune separately and both count (`CrystalTuning.count`). An off-hand
  saber's stat tunings count for its own strikes only (each saber's stats are its own). Attuned is one
  `ForceModifier.costMultiplier` (`SaberTunings.attunedMultiplier`): 0.95 while a lit, un-thrown saber in either hand
  carries it, counted once however many crystals do.
- **Tooltips:** a tuned crystal shows "Tuning: Keen"; a saber shows its blade length (when not standard) and each
  crystal's tuning.
- **Effect:** `crystal_tune` to the player (variant = the tuning's ordinal, intensity = the tune target): 12
  `crystal_glint` over the bench, a glint in the tuned socket of the open Tune tab, and the reserved `workbench.tune`
  sound.

## 7. Hum profiles (10.5)

`SaberHums` picks the emitter's sounds; a training saber and anything that is not a saber keep the M1 sounds.

| Emitter | Ignition, hum and retraction |
|---|---|
| Guardian | deep and steady (76 Hz, a heavier sub, a slow even swell) |
| Duelist | bright and thin (112 Hz, the low end thinned), a quick snapping ignition |
| Reliquary | old and wavering (86 Hz, uneven pitch drift and crackle); it sputters twice before it catches |
| Cantor | low (70 Hz) with a choir-like overtone: three wordless voices on a major chord through "ah" formants |

- The server plays the ignition and the retraction (`SaberToggle.playToggleSound(level, entity, ignite, stack)`; the
  old three-argument form picks the carried saber). A dropped saber that goes out plays its own retraction.
- The client plays the hums (`SaberAudio`): one per lit saber, keyed by entity and hand, so two lit blades play two
  hums; the main hand's plays at 0.6 volume and the off hand's at 0.45. Swapping to a saber with another emitter
  restarts the hum with the new profile. The singing crystal's song (M3) plays once per lit singing saber, one layer
  per hand.
- A staff's second blade has no separate retraction.

## 8. The Legendary tier (11.1)

- `Quality.LEGENDARY` (lead) has weapon ×1.18, armour ×1.40 and durability ×1.75 (`qualityWeaponDamage`,
  `qualityArmour`, `qualityDurability`, now four values; a stored three-value array keeps Legendary's default). Armour
  is floored after adding `Crafting.ARMOUR_EPSILON` (1e-4), so 5 armour at ×1.40 is 7 and not 6 (the float 1.40f is
  just under 1.4).
- A schematic whose id ends in `_legendary` is legendary (`Crafting.isLegendary`). It always makes a Legendary item:
  no quality draw and no pity count. The bench shows "Always Legendary", with a flickering gold frame on the
  schematic's cell and round the result slot. It is never commissioned (the commission list skips it and the odds
  show 0). Salvage refuses any Legendary item and any legendary component however it was made ("Too rare to break
  down", `BenchResult.TOO_RARE`).
- **Level:** a legendary schematic of a discipline needs that discipline at `legendaryLevel` (5).
  `SchematicLoader.withLegendaryLevel` writes it into the schematic's text at load, so the server and the clients it
  syncs see the same level. The two ship parts' schematics are `common` and need no level.
- **Item:** `Crafting.applyQuality` gives Masterwork and Legendary the `EPIC` rarity and Legendary a gold item name, set
  once at craft time; the tooltip has a gold "Legendary" line (`GearTooltips`).
- **First Legendary:** `Crafting.noLegendaryYet(data)` reads the first-crafts list. The first legendary craft sends
  `bench_craft` with intensity 2 and the `lesson_complete` event, variant 14 (`QuestEffects.LESSON_LEGENDARY`), which
  W4 draws as the `legendary` banner with `bench.legendary`. On that craft the client skips its own chime and toast;
  every later legendary craft has the chime, the rise of sparks and a "Legendary" toast.
- **Learning:** `Crafting.teach` unlocks the `legendary_components` codex entry with the first legendary schematic.

## 9. The ten components (11.2)

Schematics, materials and disciplines are as the spec's table. Each schematic takes 160 ticks and gives 150 discipline
XP a craft (the two `common` ones none), plus the M4 first-craft XP of its level.

| Component | Effect, as built |
|---|---|
| The Cantor's Emitter | +8% damage (`SaberStats`, on the emitter's other stats); the Cantor hum |
| Hask Pommel | +15 guard stamina, +1 parry tick (before the form multiplier) |
| Sunforged Grip | +8% attack speed, +5 guard stamina (flat, after the grip's multiplier) |
| Stormglass Lens | finishers shock the target for 20 ticks (`MeleeCombat.shock`; `lens_mastery` lengthens it); +10% damage against droids (`MeleeCombat.isDroid`). One value at every quality |
| Clan-Iron Plating | armour mod: +3 armour per piece that has it (`EquipmentEvents`), 10% less blaster damage per piece (multiplied) |
| Hymnweave Lining | armour mod: +6 maximum Force Energy and ×1.1 regeneration per piece (`EquipmentModifiers`, the M4 `ForceModifier` route) |
| Storm Cell | blaster cell: each player bolt hit arcs to the nearest other enemy within 4 blocks of the struck one (never an ally, a pet or a shielded bystander) for 30% of the bolt's damage with its mods, as indirect magic damage, so it never arcs again; `player_blaster_fire` variant `STORM_ARC` draws it |
| Sunstone Edge | vibro edge: +12% melee damage; the target burns for 40 ticks |
| Spindle Capacitor | ship part, `PART_SPINDLE_CAPACITOR` (`1 << 19`): W2 builds the shield effect; W1's `ship:fit` fits it for 500 credits and uses up the item |
| Hymn-Tuned Drive | ship part, `PART_HYMN_DRIVE` (`1 << 20`): W1 builds the jump effect and fits it |

- The three hilt parts have their own models in the single saber and its guard pose; on a saberstaff (one long grip of
  its own, no pommel) only the Cantor's Emitter shows, at both ends. The lens sits in the M4 lens socket.
- The legendary hilt parts' numbers are in `CraftingBalance` (`crafting` section) and reach `SaberStats` through
  `SaberStats.configureLegendary`, called with `Quality.configure` at server start.
- The legendary mods fit and come out through the M4 `Mods` rules (each fits one slot); their numbers are fixed, not by
  quality. The ship parts are `EPIC`, stack to one and resist fire.
- `/oldrepublic saber parts` gives the legendary parts with their Legendary quality.

## 10. Materials (11.3)

`hymnstone_shard` and `clan_iron` are the lead's plain items with W7's textures and names. Their sources (the raid,
Ulvaine's echo, the fence, bounties, Clan Hask warriors, the armourer, the Ulvaine helper's roll) belong to the workers
who own them; `sunstone` and `stormglass_shard` are W3's. The Tune tab spends sunstone, stormglass shards and hymnstone
shards (section 6).

## 11. Guaranteed progress (11.4)

`LegendaryDrops.roll(ServerPlayer p, Identifier schematic, float chance, int pity)`:

- A schematic the player knows is never rolled, and an old counter for it is dropped.
- Otherwise the try that is the `pity`th without it (a positive `pity`) always teaches it, as does a draw under
  `chance`. A miss adds one to `CraftingData.misses(id)` (`legendary_misses`); a hit clears it.
- Learning goes through `Crafting.teach` (so the first unlocks the codex entry), with a gold chat line
  ("Legendary schematic found: …") and a chime. The caller shows the rest of its scene.
- The rule itself is `LegendaryDrops.hits(misses, chance, pity, draw)` (pure, for the tests); a seeded overload takes
  the random source.

Callers (each the owner's code): W2 the Spindle's vault (pity 8), W4 the space bounties (pity 25), W3 the freighter
holds (pity 30). The raid's counter is `RaidData.pity` (W8). Honoured vendors sell their schematics outright (W4).

## 12. The rank 4 robes and the Cantor's Mask (8.5, 12.3)

| Item | Counts as | Armour | Look |
|---|---|---|---|
| `jedi_master_robe` (Master's Robe) | the Jedi robe of `jedi_robes` | 4 (the rank 3 robe's 3, +1) | the Knight's cut in a deeper brown under a long cloak with gold-thread borders |
| `sith_darth_robe` (Darth's Robe) | the Sith robe of `sith_robes` | 4 | black, with a red-lined high collar and a short cape |
| `wayfarer_mantle` (Wayfarer's Mantle) | the spacer's jacket of `spacer` | 5 (the jacket's 4, +1), with the jacket's speed | a dusty capelet over a travelling jacket, a teal mantle and a brass compass clasp |
| `cantors_mask` (Cantor's Mask) | nothing: cosmetic | none, no durability | a pale singer's face plate with almond eye slits, an open round mouth, stave lines, a hymnstone on the brow and a tuning-fork crest |

- The three chest pieces are `ArmourItem`s of rank 4 with their set's equipment traits, so the M3 set rules (and their
  alignment gates) count them; each has a rank line in its tooltip. All four are `named` (never crafted, salvaged or
  sold) and `EPIC`.
- The mask is `Equippable` on the head with its own asset and the robe's equip sound.
- `RobeModel` builds their outer layers (128×64 for the two robes, 64×64 for the mantle, 64×32 for the mask) and
  `RobeArmorRenderer` (Fabric's armour renderer, so on every humanoid wearer) draws them while the `robeModels` client
  option is on; with it off, it draws vanilla's armour shapes with the plain humanoid layer.
- W5 gives and reissues the robes (`ArmourSets.rankFourRobe(path)`); W8's raid gives the mask.

## 13. Effects and sounds (15)

| Event | Phases |
|---|---|
| `offhand_strike` (toTracking; entity = the striker; intensity 1 for a hit, 0 for a miss) | Activation: the off hand swings on every other client at once. Travel: the swing trail takes the off-hand arc in the off blade's colour. Sound: `saber.offhand_swing` (a quicker, lighter cut). Impact and aftermath: the M1 saber hit effects of the blow itself. |
| `crystal_tune` (toPlayer; variant = the tuning, intensity = the target) | Activation: 12 `crystal_glint` lift off the bench (the 15.1 budget, through `Vfx.count`). Aftermath: the tuned socket glints on the open Tune tab. Sound: `workbench.tune`. |
| `bench_craft` variant 5, `CRAFT_LEGENDARY` (around) | Anticipation: the M4 weld loop and sparks of the craft. Activation: the weld stops, a puff, and `bench.legendary` (a set-down thump, a rising gold chime and a choir swell). Aftermath: a slow rise of bright `spark_shower` sparks off the finished item, one every 2 ticks for 40 ticks, under a warm gold glow that climbs with them. The crafter's first Legendary leaves the chime to the banner. |
| `player_blaster_fire` variant `STORM_ARC` (around; intensity = the arc's length) | Travel: a jagged line of `lightning_spark` from the struck enemy to the next, wandering most in its middle. Impact: a crackle of small arcs where it lands and a short snap (`ELECTROSTAFF_SHOCK`, pitched up). |
| `lesson_complete` variant 14 | W4's `legendary` banner, sent by W7 on the first legendary craft. |

Particles are culled by `Vfx.inRange` and scaled by `Vfx.count`. Nothing draws rings, sigils or glow outlines; the
legendary glow is the M4 `SaberSparks.glow` point light.

## 14. Persistence, multiplayer and edge cases

- Saved: the length (`SaberConfig`), the tunings (`CrystalAttunement`), the quality and the gold name (item
  components), the pity counters (`CraftingData`). Nothing else W7 adds is saved: the off hand's clock and the stance
  are rebuilt.
- A dropped, thrown or stored saber keeps its length and tunings; the reach component is rewritten on every change.
- The off hand's clock lives on the server; a client that runs ahead is refused (`NOT_READY`) and swings at nothing.
- Two players at once: the clock, the riposte and the stance are keyed by player.
- A saber retuned or given a new length on the bench is never lit there, so the bench writes its retracted numbers;
  igniting it later rewrites them.
- Peaceful: nothing changes.

## 15. API checks

1. `Crafting.applyQuality`: `ItemStack#getStyledHoverName` wraps the hover name in a component styled with the rarity
   colour; the name's own gold should win (a child's colour overrides its parent's, as for vanilla's coloured names).
2. `SaberStats.attackRange`: `AttackRange#effectiveMaxRange` is taken to return the survival reach times `mobFactor`
   for a non-player (so saber NPCs keep vanilla's reach).
3. `SaberM5GameTests.bladeLengthStats`: the same method is taken to return the creative reach for a creative player.

Probe lines for these are in the W7 report (`body:net.minecraft.world.item.component.AttackRange#effectiveMaxRange`,
`body:net.minecraft.world.item.ItemStack#getStyledHoverName`).

## 16. Balance (16.4)

`saber` (`SaberBalance`): `makashiParryTicks` 2, `makashiRiposteWindow` 20, `makashiPassiveWindow` 30,
`makashiRipostePct` 40, `makashiDuelistPct` 15, `makashiBoltGuardPct` 20; `djemSoHeavyPct` 20, `djemSoWindupCut` 2,
`djemSoStaggerTicks` 10, `djemSoReturnChance` 0.6, `djemSoSpeedPct` -5; `jarKaiSpeedPct` 10, `jarKaiGuard` 15,
`jarKaiParryTicks` -1; `offhandDamage` 0.7, `offhandSpeed` 0.2, `dualParryTicks` 1; `shortReach` -0.5,
`shortSpeedPct` 10, `shortDamagePct` -10, `longReach` 0.5, `longSpeedPct` -10, `longDamagePct` 5; `tuneCredits` 100,
`keenPct` 5, `swiftPct` 6, `steadyTicks` 1, `wardingGuard` 15, `resonantPct` 5; `tuneSunstone` 2, `tuneStormglass` 2,
`tuneHymnstone` 1.

`crafting` (`CraftingBalance`): `qualityWeaponDamage` [1.0, 1.06, 1.12, 1.18], `qualityArmour` [1.0, 1.15, 1.30, 1.40],
`qualityDurability` [1.0, 1.25, 1.5, 1.75]; `legendaryLevel` 5; `cantorDamagePct` 8, `haskGuard` 15,
`haskParryTicks` 1, `sunforgedSpeedPct` 8, `sunforgedGuard` 5, `stormglassShockTicks` 20, `stormglassDroidPct` 10;
`clanIronArmour` 3, `clanIronBlasterPct` 10, `hymnweaveEnergy` 6, `hymnweaveRegenPct` 10, `stormCellRange` 4,
`stormCellPct` 30, `sunstoneEdgePct` 12, `sunstoneEdgeFireTicks` 40.

Fixed in code: the off-hand reach tolerance (1 block) and clock slack (1 tick), the off-hand hum volume (0.45), the
legendary rise (40 ticks, a spark every 2) and the tune burst (12 glints, the 15.1 budget).

## 17. Tests

- `SaberM5GameTests` (19.1): `formModifiers`, `formChangeRules`, `offhandStrikeValidates` (reach, cooldown, lit,
  alive), `offhandPenaltyUnlessJarKai`, `bladeLengthStats`, `tuningSurvivesDisassembly`, `tuningEffects`,
  `resonantCountsOnce`, `legendaryNeverRolledNorSalvaged`, `legendaryMultipliers`, `pityCounters`,
  `knownSchematicNeverRolled`, `legendaryComponentEffects` (the eight W7 parts), `robesCountAsSetPieces`,
  `concussionMissileSchematic`. Each has an explicit `maxTicks`, small fixtures, listed mock players and seeded
  randoms.
- `gametest/m5/SaberClientSteps` (19.2), screenshots 175 to 184: the Makashi, Djem So and Jar'Kai stances, dual-wielding
  in first and third person (real attack presses: the main hand's blow, then the off hand's), the short and long
  blades, the Tune tab (Keen chosen with its price carried; the tuning is then made and checked on the server), a
  legendary craft (the Hask Pommel) and its banner. The saber is toggled with the real key (U). Every shortcut is
  logged, and everything the steps change is put back.

## 18. Decisions

- `bench_craft`'s legendary variant is 5 (`CraftingEffects.CRAFT_LEGENDARY`): variant 3 is the M4 `CRAFT_STOP`. The
  spec's table says 3.
- The engineering bench's odds data uses sentinels: 1000 always Masterwork, 2000 always Legendary.
- Jar'Kai is chosen like any form but acts only with two lit blades (`form` vs `selectedForm`).
- The off-hand strike also swings at nothing and at an out-of-reach target from the client's side; the server decides
  every hit.
- The wayfarer's mantle has 5 armour (the jacket's 4, +1), matching the robes' +1.
- The training saber keeps the M1 sounds; the off hand's hum is quieter (0.45).
- The long blade is drawn with a stretched display parent instead of an item-model transformation.
- Legendary components are too rare to salvage however they were made (an operator's copy too).
