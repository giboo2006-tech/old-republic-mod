# Crafting feature design (Milestone 4, W5)

Spec: `docs/MILESTONE_4.md` 7.1 to 7.6 (the Engineering Bench, disciplines, schematics, quality, salvage, consumables
and tools), 8.10 (the trainers' conditions and actions), 11.2 and 11.3 (toasts, screens, the journal's Crafting tab),
12.1 (the `crafting` attachment), 13.3 and 13.4 (effects and sounds), 14.3 (`crafting` balance), 16 (ids and payloads)
and 17 (tests). The saberstaff, lenses and the training saber are in `docs/design/saber.md` (Milestone 4 section).
Nothing in 7.1 to 7.6 is cut. Crystal tuning is cut by the spec (sections 1 and 5.7).

## 1. Packages and classes

### Common (`src/main/java/com/oldrepublic/crafting`)

| Class | Role |
|---|---|
| `Crafting` | The frozen contract (`has`, `level`, `knows`, `teach`, `roll`, `quality`, `withQuality`) and everything that changes a player's crafting state: `craftRefusal`, `craftable`, `applyQuality`, `awardCraft` (discipline XP, level-ups, first-craft player XP, `QuestHooks.onCrafted`), `addDisciplineXp`, `choose` (`ChoiceResult`), `secondOpen`, `unlearn`, `isMasterworkOnly` |
| `CraftingRules` | Pure rules with fixed numbers for the tests: `levelFor`, `xpToNext`, `fineChance`, `masterworkChance`, `pick`, `pityDue`, `pityAfter`, `craftsToGuarantee`, `craftXp`, `firstCraftXp`, `secondOpen`, `salvageCount`, `commissionFee` |
| `CraftingData` (Phase 0, filled) | The immutable attachment record (section 3) with `with*` methods |
| `CraftingAttachments` | `oldrepublic:crafting`: persistent, `copyOnDeath`, synced to its owner only |
| `CraftingBalance` | Section `crafting` of `balance.json` (section 7); `applyQuality` hands the multipliers to `Quality` on server start |
| `Discipline`, `Quality`, `Schematic`, `Schematics` (Phase 0) | Unchanged contracts; `Schematic.Input` matches an item or a tag |
| `SchematicLoader` | The `schematic` reload listener, the unknown-item skip and the sync text (section 4) |
| `Salvage` | `schematicFor`, `canSalvage`, `returns`, `xp` (section 6); common, so the screen previews what the server gives |
| `CraftingDialogue` | `discipline:has`, `discipline:level`, `discipline:unlearn`, `trainer:commission` (section 9) |
| `CraftingCommands` | `/oldrepublic crafting ...` (section 10) |
| `CraftingEffects` | Effect ids and variants (section 8) |
| `CraftingItems`, `CraftingBlocks` | Registration of the items, the bench block and block entity, and the `stun_grenade` entity |
| `CraftingFeature` | Init: payloads, reload listener, sync on join and `/reload`, tick, the dialogue hooks, server stop cleanup |
| `bench.EngineeringBenchBlock`, `bench.EngineeringBenchBlockEntity` | The block (horizontal facing) and its saved 9-slot parts tray |
| `bench.EngineeringBenchMenu` | Tray, output, gear, mod and salvage slots, data slots; also the trainer's commission menu |
| `bench.BenchService` | Every bench action, the running crafts, salvage, the discipline choice and commissions |
| `bench.Ingredients` | Finding and taking inputs: tray first, then inventory; all or nothing |
| `bench.BenchResult` | The status line's outcome (append only) |
| `item.HealItem`, `item.StimItem`, `item.Consumables` | Medpac and kolto; stims and their crash; heal-over-time and the medpac interrupt |
| `item.ScannerItem`, `item.FusionCutterItem`, `item.RepairKitItem`, `item.SchematicItem` | The tools and the schematic item |
| `item.GrenadeItem`, `item.StunGrenade` | The player's frag and stun grenades |
| `network.BenchActionPayload`, `network.SchematicSyncPayload` | `bench_action` (C2S) and `schematic_sync` (S2C) |

### Client (`src/client/java/com/oldrepublic/client/crafting`)

| Class | Role |
|---|---|
| `CraftingClient` | Client init: the sync receiver, the screen, the effect handlers, the scanner marks, toasts, the quality tooltip line, the stun grenade renderer, the journal tab |
| `EngineeringBenchScreen` | The four-tab bench screen (section 5); `selectForTest` is the client test seam |
| `CraftingFx` | Handlers for every crafting effect, including the weld loop and its watchdog |
| `ScannerMarks` | The scanner's motes and HUD-edge arrows, surveyed in slices |
| `CraftingToasts` | Schematic learned, discipline chosen or levelled, Masterwork crafted |
| `CraftingPanel` | The journal's Crafting tab (`crafting`) |
| `client.saber.SaberSparks` | The saber's tinted sparks, shared with the bench's welding arm and the stun grenade |

## 2. Disciplines and XP (7.2)

- Four disciplines (`armstech`, `armourcraft`, `artifice`, `medtech`). Two at most. The first is chosen freely at a
  bench (Disciplines tab); the second opens at level `secondDisciplineLevel` (2) in the first or player level
  `secondDisciplinePlayerLevel` (13). A third is refused (`FULL`).
- Levels 1 to 5 from total discipline XP and `levelXp` (0, 120, 360, 800, 1,600). A craft gives the schematic's `xp`,
  times `lowLevelXpMultiplier` (0.25) when the schematic is two or more levels below the crafter. Salvage gives
  `salvageXp` (a quarter) of the craft XP. Level-ups send a gold chat line and `QuestHooks.onDisciplineLevel`.
- The first craft of each schematic gives `firstCraftXpPerLevel` × schematic level player XP once
  (`CraftingData.firstCrafts`).
- Unlearning (a trainer's `discipline:unlearn <id>`): refused during a craft or without `unlearnCost` (500) credits
  (inventory and a nearby ship hold, through `Credits.take`). The discipline is dropped with its level, XP and pity and
  listed in `unlearned`; its learned schematics stay in `known` but are locked (`craftRefusal`) until the discipline is
  chosen again, at level 1.

## 3. Saved state (12.1)

`oldrepublic:crafting` (`CraftingData`): `disciplines` (≤ 2 of `{id, level, xp, pity}`), `unlearned` (≤ 4), `known`
(≤ 256 ids), `first_crafts` (≤ 256), `chosen_at` (day of the first choice, −1). Every field is `optionalFieldOf`, so an
old save loads empty; levels clamp to 1..5 and XP and pity never go negative. The record is immutable and replaced
whole on every change, so the synced copy is never shared with server code. Stims and heal-over-time are short and live
in memory (`Consumables`); a relog ends them.

## 4. Schematics (7.3)

- `data/<ns>/schematic/<id>.json` (65 files in `oldrepublic`), loaded by `SchematicLoader` on server start and
  `/reload`. The id is the file name. Fields and defaults: `discipline` (`common` or absent for none), `level` (1),
  `inputs` (each exactly one of `item` or `tag`, `count` 1), `output` (`item`, `count` 1), `quality` (false),
  `craft_ticks` (40), `xp` (0), `learned` (`auto`, `item` or `trainer`).
- A file naming an unregistered item (input or output) is skipped with a warning; so is a file that fails to parse.
  Only a full parse is loaded, never DFU's partial result (a list decode keeps the good inputs of a bad list, which
  would load a cheaper craft).
- Sync: the server keeps each loaded file's compact JSON; `schematic_sync` carries all of it on join
  (`SYNC_DATA_PACK_CONTENTS`) and after `/reload`, and the client parses it with the same loader (the skill-tree
  pattern). The client replaces its `Schematics` set; unknown items are skipped there the same way.
- Knowing: an `auto` schematic is known at its discipline level (a common `auto` one by everyone); `item` and `trainer`
  schematics are known once taught (`Crafting.teach`: the schematic item, a trainer, a story reward).
- Masterwork-only recipes: by convention an id ending in `_masterwork` (trainer level 5 recipes) always crafts
  Masterwork without a roll and never counts for salvage.

## 5. The Engineering Bench (7.1)

- Block `engineering_bench`: horizontal facing, a block entity with the 9-slot parts tray (saved, dropped when broken,
  shared by everyone using the bench). Recipe (vanilla shaped): 3 `durasteel_plate`, a crafting table, 2 iron ingots,
  1 redstone. Loot: drops itself.
- Menu slots: tray 0–8, output 9, gear 10, mod 11, salvage 12, then the inventory. Only the current tab's slots are
  active. Data slots carry the tab, the craft progress, the selected schematic's odds, XP, pity count, first-craft XP,
  fee, the last result (with a sequence number), whether a second discipline is open and the salvage numbers.
- Screen `EngineeringBenchScreen` (272 × 204, the workbench's style), tabs:
  - **Craft:** a filter strip (all, common, each discipline), a 5 × 3 scrolling grid of craftable schematics, the
    detail (inputs have/need, output, Fine and Masterwork odds, discipline XP, "Masterwork guaranteed in N crafts",
    first-craft XP), Craft and Cancel, and "Break bundle" (one `credit_bundle` into 100 chits in the tray).
  - **Modify:** the gear slot, its sockets with fitted mods (click to remove), the mod slot and Fit (W4's `Mods`).
  - **Salvage:** the input slot, the preview of the returns and XP, Salvage.
  - **Disciplines:** four cards (level, XP rule, next unlocks) and Choose, which asks for a second click to confirm.
- A craft: `bench_action` CRAFT → `BenchService.start` checks the refusal and the inputs (nothing is taken yet) and
  starts a job for the crafter; the server ticks it (`craft_ticks`); leaving the bench screen or walking more than
  `craftCancelRange` (6) away cancels with no loss. At the end the inputs are taken all at once (tray first), the
  quality rolled, `applyQuality` applied, the result put in the output slot (or given when the slot holds something
  else), and `awardCraft` credits the XP.
- The output, gear, mod and salvage slots are per use: closing the screen gives their contents back.

## 6. Quality, pity and salvage (7.4, 7.5)

- Odds: Fine `fineBase` (0.18) + `finePerLevel` (0.06) per discipline level above the schematic, Masterwork
  `masterworkBase` (0.03) + `masterworkPerLevel` (0.03) per level; one uniform draw picks Masterwork, Fine or Standard.
- Pity per discipline: a quality craft below Masterwork adds one; at `pityCrafts` (10) the next quality craft is
  Masterwork without a draw and the count resets. Crafts without quality never move it.
- `applyQuality` writes the stack's own attack damage (`(dmg + 1) × weapon − 1`, keeping the player's base 1),
  armour (rounded down) and toughness modifiers and `MAX_DAMAGE`, and sets the name rarity (Fine rare, Masterwork epic).
  Registered values never change. The tooltip line comes from the client (`quality.oldrepublic.*`).
- Salvage: only an item that a discipline schematic makes, one at a time (`count` 1 outputs), never a named drop
  (`oldrepublic:named`) and never a Masterwork-only recipe's output. Returns `salvageReturn` (half) of each input,
  rounded down, at least one of the first input; a tag input returns its first member; fitted mods come back as they
  are; a Masterwork adds one `masterwork_fragment`. Story items, M3 robes, crystals and plain materials have no
  discipline schematic, so they are never salvageable.

## 7. Consumables and tools (7.6)

| Item | Behaviour |
|---|---|
| `medpac` | 16-tick use; then `medpacHeal` (8) over `medpacTicks` (40). Puts the medpac and the kolto injector on `healCooldown` (200). A hit of `medpacInterruptDamage` (4) or more while using stops the use (`AFTER_DAMAGE`) |
| `kolto_injector` | 10-tick use; 6 at once, removes poison and wither; its own cooldown 300, the shared 200 on the medpac |
| `combat_stim`, `focus_stim` | One stim at a time (running or crashing). Combat: +8% damage through `DamageModifiers` (not Force harm); crash Weakness I for 200. Focus: +20% Force regen through `ForceModifiers`; crash −20% for 200 |
| `scanner` | A pulse of `scannerRange` (48): the `scanner_pulse` event (the client marks resources and creatures for 200 ticks) and `MissionHooks.onScanned` (a survey counts it like Sense). Cooldown 400 |
| `fusion_cutter` | A tool (250 durability): quick on wreck salvage and durasteel blocks; held use on W1's `sealed_plating` for `cutterSealTicks` (60) cuts it, with sparks; looking away stops it |
| `frag_grenade` | Throws the M3 `frag_grenade` entity at the aimed point (up to 16 blocks) through `FragGrenade.throwAt`. Damage 7 and radius 3 need the lead's player-owned throw (section 11) |
| `stun_grenade` | A thrown entity: on its first hit, no damage; staggers every living thing within 3 for 30 ticks (droids 60) and strips invisibility; the thrower's side is spared by the M3 grenade rule |
| `repair_kit` | Restores `repairKitPct` (25%) to the item in the other hand, or else the most worn armour piece; 3 uses |
| `schematic` | One item; its component names the schematic. Use teaches it when the player has the discipline; otherwise it says which and is kept |

Both grenades share `grenadeCooldown` (60) and stack to 4.

## 8. Effects (13.3) and sounds (13.4)

| Id | Variants | Client |
|---|---|---|
| `mod_fit` | 0 fitted, 1 removed | `bench.mod_fit` / `bench.mod_remove`, a few sparks at the bench |
| `bench_craft` | 0 start (intensity = craft ticks), 1 done, 2 Masterwork, 3 stop, 4 tool cut | weld loop (`bench.weld`, keyed by crafter, watchdog), arm sparks; done puff and `bench.done`; Masterwork shimmer, `bench.masterwork` and the toast; the cutter's sparks |
| `salvage` | 0 | `bench.salvage`, a clatter of small parts |
| `consumable` | 0 medpac, 1 kolto, 2 stim, 3 crash | `medpac.use`, `kolto.inject`, `stim.inject`, `stim.crash` and small particles at the player |
| `scanner_pulse` | intensity = range, variant = ticks | `scanner.pulse`, a ring, then `ScannerMarks` (`scanner.ping` on the first marks) |
| `grenade_player` | 0 throw, 1 stun burst | `grenade.throw`; `grenade.stun` with a spark burst |

All sounds are placeholders synthesised by `tools/assets/sounds/crafting.py` under the names gen_sounds gave them in
Phase 0, so `sounds.json` does not change.

## 9. Dialogue and journal (8.10, 11.3)

- Conditions: `discipline:has <id|any>` and `discipline:level "<id> <n>"`, with reasons
  `crafting.oldrepublic.condition.*` (`key|@nestedKey|arg`).
- Actions: `discipline:unlearn <id>` and `trainer:commission [<id>]`. A commission opens the bench menu in commission
  mode: no tray, Modify or Salvage; the list limited to that discipline's schematics the player knows or carries as a
  schematic item; an instant Standard craft for the inputs plus `commissionPerLevel` × level credits.
- The journal's **Crafting** tab (`CraftingPanel`, registered through `JournalTabs`): disciplines with levels, XP and
  next unlocks, unlearned ones, when the second opens; known schematics by discipline, locked and never-crafted ones
  marked. The client reads no balance file, so it shows the default thresholds.

## 10. Commands

`/oldrepublic crafting info|choose|level|pity|unlearn|teach|schematic|reset <player> ...` (one player each,
`EntityArgument.player()`): show the data, choose a discipline (ignoring the second gate), set a level or pity count,
unlearn without the fee, teach a schematic, give a schematic item, reset everything.

## 11. Requests outside W5's files

- `enemy/trooper/FragGrenade` (lead): a player-owned throw with `crafting.playerGrenadeDamage` (7) and
  `playerGrenadeRadius` (3); `GrenadeItem` calls `FragGrenade.throwAt` meanwhile (the trooper numbers).
- `data/minecraft/tags/block/mineable/pickaxe.json` (lead): add `oldrepublic:engineering_bench`.
- The `droids` entity tag (W6, created by the lead) for the stun grenade's longer stagger.
- W2: the trainers' dialogue lines that use the conditions and actions in section 9, and vendor stock.

## 12. Balance (`crafting`, 14.3)

`levelXp` [0, 120, 360, 800, 1600], `secondDisciplineLevel` 2, `secondDisciplinePlayerLevel` 13,
`lowLevelXpMultiplier` 0.25, `firstCraftXpPerLevel` 30, `fineBase` 0.18, `masterworkBase` 0.03, `finePerLevel` 0.06,
`masterworkPerLevel` 0.03, `pityCrafts` 10, `qualityWeaponDamage` [1, 1.06, 1.12], `qualityArmour` [1, 1.15, 1.3],
`qualityDurability` [1, 1.25, 1.5], `craftCancelRange` 6, `salvageReturn` 0.5, `salvageXp` 0.25, `unlearnCost` 500,
`commissionPerLevel` 50, `medpacUseTicks` 16, `medpacHeal` 8, `medpacTicks` 40, `medpacInterruptDamage` 4,
`healCooldown` 200, `koltoUseTicks` 10, `koltoHeal` 6, `koltoCooldown` 300, `stimTicks` 1200, `combatStimPct` 8,
`focusStimPct` 20, `crashTicks` 200, `scannerRange` 48, `scannerTicks` 200, `scannerCooldown` 400,
`playerGrenadeDamage` 7, `playerGrenadeRadius` 3, `stunRadius` 3, `stunTicks` 30, `stunDroidTicks` 60,
`grenadeCooldown` 60, `cutterSealTicks` 60, `cutterDurability` 250, `repairKitPct` 25, `repairKitUses` 3, and the lens
values by quality `lensHoned` [6, 9, 12], `lensSteady` [1, 1, 2], `lensResonant` [5, 6.5, 8].

## 13. Tests

`CraftingGameTests` (17.1, all 21): `schematicJsonLoadsAndSyncs`, `craftNeedsDisciplineLevelAndInputs`,
`craftTakesFromTrayAndInventory`, `qualityOddsByLevel`, `pityGuaranteesMasterworkAt10`, `lowLevelXpQuarter`,
`firstCraftPlayerXpOnce`, `secondDisciplineGate`, `unlearnCostsAndResets`, `salvageReturnsHalfAndMods`,
`namedAndStoryItemsCannotSalvage`, `medpacSharedCooldownAndInterrupt`, `stimCrash`, `scannerCountsSurvey`,
`saberstaffBuildNeedsTwoCrystalsAndGrip`, `staffWhirlHitsAllInRadius`, `singleBladeToggle`, `lensSlotAndEffects`,
`unknownItemIdsSkippedWithWarning`, `trainingSaberNeverKillsPlayer`, `craftingDataCodecRoundTripAndCopyOnDeath`.
Test schematics are built in the test and never put in `Schematics`; mock players are listed and removed; draws are
seeded. Where a real hit cannot land on a mock player, the Fabric event the hit would fire is invoked directly.

`m4/CraftingClientSteps` (17.2): screenshots 50 to 59 (the four bench tabs, a Masterwork craft, the Crafting tab, the
saberstaff with two and one blades, a lens in the workbench, the whirl), on a stage built above the player; every
change is put back.
