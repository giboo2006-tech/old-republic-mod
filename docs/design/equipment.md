# Equipment feature design: era armour (Milestone 3)

Owner: W4 (troopers and armour). Packages `com.oldrepublic.equipment` (common) and `com.oldrepublic.client.equipment`
(client). Spec: `docs/MILESTONE_3.md` section 9 (all), 11.2 (the set-bonus toast), 13.3 (`armour_set`), 14
(`equipment`), 16 (ids) and 17 (`EquipmentGameTests`, screenshots 47 to 49).

API status legend: **[V]** verified against the 26.2 dumps (`api/m3_equipment.txt` and the others) or the Fabric 26.2
sources; **[U]** unverified, marked `// API-CHECK:` in the code. Nothing in this feature's code is [U]; the open points
are listed in section 15.

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Items (18) | `republic_trooper_{helmet,chestplate,leggings,boots}`, `sith_trooper_{...}`, `jedi_hood`, `jedi_robe`, `jedi_knight_robe`, `jedi_trousers`, `jedi_boots`, `sith_hood`, `sith_robe`, `sith_lord_robe`, `sith_trousers`, `sith_boots` | `EquipmentItems` (class `ArmourItem`) |
| Armour materials (6) | Republic trooper, Sith trooper, Jedi robes, Sith robes, plus the rank 3 Knight's and Lord's robes (same stats, own asset) | `EquipmentMaterials` |
| Equipment assets (6) | `oldrepublic:{republic_trooper,sith_trooper,jedi_robes,sith_robes,jedi_knight,sith_lord}` | `assets/oldrepublic/equipment/*.json` |
| Data component | `oldrepublic:equipment_traits` (existing Phase 0 `ModComponents.EQUIPMENT_TRAITS`) | `EquipmentTraits` (frozen record) |
| Item tags | `oldrepublic:repairs/{trooper_armour,jedi_robes,sith_robes}`, `oldrepublic:{jedi_robe_cloth,sith_robe_cloth}` | `data/oldrepublic/tags/item/` |
| Recipes (16) | one per piece, except the two rank 3 robes (9.3 gives them no recipe) | `data/oldrepublic/recipe/` |
| Effect event | `oldrepublic:armour_set` | `EquipmentEffects.ARMOUR_SET` |
| Sounds | `armour.equip_trooper`, `armour.equip_robe`, `armour.set_bonus` (Phase 0 `ModSounds` constants) | `tools/assets/sounds/equipment.py` |
| Balance section | `equipment` | `EquipmentBalance` |
| Command | `/oldrepublic armour give <player> <set> [rank3]` | `EquipmentCommands` |

The enum `ArmourSet` (`REPUBLIC_TROOPER`, `SITH_TROOPER`, `JEDI_ROBES`, `SITH_ROBES`) is frozen and append only: its
ordinal is the `armour_set` variant and its lower-case id is saved in the component.

## 2. Classes

Common (`src/main/java/com/oldrepublic/equipment/`):

- `EquipmentFeature.init()`: loads the items, registers `EquipmentModifiers` with `ForceModifiers`, `DamageModifiers`
  and `ControlEffects.registerDurationModifier`, then `SetBonuses`, `EquipmentEvents` and `EquipmentCommands`. It reads
  no balance values.
- `EquipmentBalance`: the `equipment` section (section 13 below).
- `EquipmentMaterials`: the six `ArmorMaterial`s, the repair and cloth tag keys, the six equipment asset keys.
- `EquipmentItems`: the 18 items, `ALL` and `ROBES` (the ten items the robe renderer draws), `defaultTraits(set)`.
- `ArmourItem`: the item class; knows its set, slot type and whether it is a rank 3 robe; writes the tooltip.
- `ArmourAttributes`: the merged `ItemAttributeModifiers` of a piece and the per-slot modifier ids.
- `ArmourSets`: the frozen query API for other features (`count`, `fullSet`, `piece`, `disguise`) plus `rankRobe`,
  `boltGuardDrainMultiplier`, `worn` and `wearer`.
- `SetBonusRules`: the pure arithmetic of every trait and bonus (no world access), used by the server, the client
  tooltip and toast, and the tests.
- `EquipmentModifiers`: the `ForceModifier`, `DamageModifier` and `ControlEffects.DurationModifier` implementation.
- `SetBonuses`: Calm of the Watch and Hunger of the Tomb (event-driven refunds).
- `EquipmentEvents`: the `EQUIPMENT_CHANGE` listener.
- `EquipmentEffects`: the `armour_set` id.
- `EquipmentCommands`: `/oldrepublic armour give`.

Client (`src/client/java/com/oldrepublic/client/equipment/`):

- `EquipmentClient.init()`: robe model layers, the plain armour layers, the robe `ArmorRenderer`, the tooltip
  callback, the effect handler and the toast.
- `RobeModel`: the five robe model layers (hood, robe, rank 3 robe, trousers, boots).
- `RobeArmorRenderer`: the Fabric armour renderer of the ten robe items.
- `ArmourTooltips`: the live "Set n/4" line.
- `SetBonusToast`: the set-bonus toast (11.2).
- `EquipmentEffectsClient`: the `armour_set` handler (sound, toast, flourish).

No mixins: `oldrepublic-equipment.mixins.json` stays empty.

## 3. Materials and items (9.1, 9.4)

- **Materials [V]:** `new ArmorMaterial(durability, defense, enchantability, equipSound, toughness, knockbackResistance,
  repairTag, assetKey)` (`m3_equipment.txt:4`). The defense map is an explicit `EnumMap<ArmorType, Integer>`, so the
  argument order of `ArmorMaterials.makeDefense` never matters.

  | Material | Armour H/C/L/B | Toughness | Knockback | Durability x | Enchant | Repair tag | Equip sound | Asset |
  |---|---|---|---|---|---|---|---|---|
  | Republic trooper | 2/6/5/2 | 0.5 | 0 | 18 | 9 | `repairs/trooper_armour` | `armour.equip_trooper` | `republic_trooper` |
  | Sith trooper | 2/6/5/2 | 1.0 | 0.05 | 18 | 9 | `repairs/trooper_armour` | `armour.equip_trooper` | `sith_trooper` |
  | Jedi robes | 1/3/2/1 | 0 | 0 | 10 | 15 | `repairs/jedi_robes` | `armour.equip_robe` | `jedi_robes` |
  | Sith robes | 1/3/2/1 | 0 | 0 | 10 | 15 | `repairs/sith_robes` | `armour.equip_robe` | `sith_robes` |
  | Knight's robe | as Jedi robes | | | | | | | `jedi_knight` |
  | Lord's robe | as Sith robes | | | | | | | `sith_lord` |

  Equip sounds go through `ModSounds.holder(...)` **[V]**. Asset keys are
  `ResourceKey.create(EquipmentAssets.ROOT_ID, oldrepublic:<asset>)` **[V]** (never `EquipmentAssets.createId`, which
  is in the `minecraft` namespace).
- **Items [V]:** `ModRegistry.item(id, p -> new ArmourItem(p, set, type, rankRobe), new Item.Properties()
  .humanoidArmor(material, type).attributes(merged).component(EQUIPMENT_TRAITS, defaultTraits(set)))`. `humanoidArmor`
  sets the equippable component, durability, enchantability and repair tag; `.attributes(merged)` then replaces the
  attributes it set (9.2).
- **Merged attributes (9.2) [V]:** one `ItemAttributeModifiers` per piece, built in `ArmourAttributes.build`:
  - `ARMOR` = the defense value;
  - `ARMOR_TOUGHNESS` and `KNOCKBACK_RESISTANCE` = the material's values, left out when zero;
  - `MOVEMENT_SPEED` = `trooperSpeedPct / 100` (-0.015) with `ADD_MULTIPLIED_BASE`, on trooper pieces only.

  Every modifier id is per slot and attribute: `oldrepublic:armour.<head|chest|legs|feet>.<armor|toughness|knockback|speed>`
  (the slot name from `EquipmentSlot#getName()`), in the piece's own `EquipmentSlotGroup.bySlot(slot)`. Four trooper
  pieces therefore add four speed modifiers (-6% in all) instead of collapsing into one.
- **Enchanting and tags:** the lead's Phase 0 `enchantable/*` tags already list every piece as an optional entry.

## 4. The traits component and how traits apply (9.2)

`EquipmentTraits(set, costPct, regenPct, maxEnergy, blasterResist, meleeResist)` is on every piece. It carries the
registration defaults as fractions (for the client and for other features that read a stack):

| Set | costPct | regenPct | maxEnergy | blasterResist | meleeResist |
|---|---|---|---|---|---|
| Republic trooper | 0.05 | -0.05 | 0 | 0.06 | 0 |
| Sith trooper | 0.05 | -0.05 | 0 | 0 | 0.04 |
| Jedi robes (and the Knight's robe) | -0.02 | 0.06 | 0 | 0 | 0 |
| Sith robes (and the Lord's robe) | -0.02 | 0 | 5 | 0 | 0 |

On the server the component is used only to find the **set** of each worn piece (`ArmourSets.worn` counts the four
armour slots into a `SetBonusRules.Worn`). The numbers then come from the live `EquipmentBalance.get()`, so a changed
balance file applies at once. The rank 3 robes carry their set in the component and count as that set's robe.

`EquipmentModifiers` (registered once at init):

- **Force cost** (`ForceModifier#costMultiplier`): `(1 + trooperCostPct)^trooperPieces x (1 + jediCostPct)^jediPieces x
  (1 + sithCostPct)^sithRobePieces`, then the robe bonus for that power (section 5). Percentages compound per piece:
  four pieces at +5% give x1.216.
- **Force regeneration** (`regenMultiplier`): `(1 + trooperRegenPct)^trooperPieces x (1 + jediRegenPct)^jediPieces`.
- **Max Force Energy** (`maxEnergyBonus`): `sithMaxEnergy` per Sith robe piece.
- **Blaster damage taken** (`DamageModifier#modify`, direct entity a `BlasterBolt`):
  `(1 + republicBlasterPct)^republicPieces`, then x`(1 + republicSetBlasterPct)` at 4/4.
- **Melee damage taken** (direct entity a `LivingEntity`): `(1 + sithMeleePct)^sithTrooperPieces`.
- **Control durations** (`DurationModifier#multiplier`, every kind): x`(1 + sithSetControlPct)` at 4/4 Sith trooper.

The lead's `ForceModifiers` clamps the combined cost product to 0.70-1.50 and the regen product to 0.50-1.50, and
`DamageModifiers` combines every feature's damage multipliers; these rules never clamp themselves. Nothing writes to the
`ForceData` counters.

Worked example (9.1), checked by `workedExample9_1`: a full trooper set gives cost x1.05^4 = x1.2155, regen x0.95^4 =
x0.8145 and speed 4 x -1.5% = -6%; full Jedi robes give regen x1.06^4 = x1.2625 and cost x0.98^4 = x0.9224.

## 5. Set bonuses (9.1)

`SetBonusRules.active(set, worn, wearer, balance)`: 4/4 of the set, and for the robes the alignment gate:

- Jedi robes: alignment at least `+setAlignment` (15), or an Independent in the Balanced band.
- Sith robes: alignment at most `-setAlignment`, or an Independent in the Balanced band.
- A mob is `Wearer.NOBODY` and never gets a robe bonus; the trooper bonuses need no gate.

| Bonus | What it does | How |
|---|---|---|
| **Shoulder to Shoulder** (Republic trooper) | A further -10% blaster damage; guard stamina drain from bolts -25% | The damage part is in `blasterMultiplier`. The drain part is `ArmourSets.boltGuardDrainMultiplier(e)` (0.75 at 4/4), which W5's `SaberGuard` must multiply into the bolt drain (request in the report). |
| **Iron Discipline** (Sith trooper) | Every `ControlEffects` kind lasts 30% less; +0.2 knockback resistance | The duration modifier above, and a transient `KNOCKBACK_RESISTANCE` modifier `oldrepublic:armour.set.knockback` (+0.2, `ADD_VALUE`) kept in step by `EquipmentEvents.updateKnockback` through `addOrUpdateTransientModifier`/`removeModifier` [V]. |
| **Calm of the Watch** (Jedi robes) | 5 FE per successful parry, melee or bolt; Force Heal cost -15% (Lift for an Independent) | `CombatEvents.PARRY` calls `SetBonuses.calm(defender)`. The cost part is in `costMultiplier` (`calmAbility(path)`: `force_heal`, or `force_lift` for `Path.INDEPENDENT`). |
| **Hunger of the Tomb** (Sith robes) | 8 FE for a kill within 160 ticks of hitting that target with a power; Force Lightning cost -15% (Lift for an Independent) | `CombatEvents.POWER_HIT` records (target, caster) to game time; `ServerLivingEntityEvents.AFTER_DEATH` pays out when the killer's power hit the victim within `sithSetWindowTicks`. A power that kills reports its hit after the death in the same tick, so a kill with no recorded hit is held for that tick and paid when the hit arrives. Entries older than the window are pruned once a second, and everything is cleared when the server stops. |

**Refund caps:** both bonuses pay through W3's shared ledger `com.oldrepublic.ability.EnergyRefunds`:
Calm calls `forParry(defender, gameTime, jediSetParryFe)` and Hunger `forKill(killer, victim, sithSetKillFe)`. All
refunds for one parry (keyed by the player and the parry's game tick) together restore at most
`progression.parryRefundCap` (6), and all refunds for one kill (keyed by the player and the victim) at most
`killRefundCap` (12), whichever source pays first; each is also limited to the player's max energy. So Hunger of the
Tomb with `hungering_dark` and `tomb_hunger` stops at 12 FE per kill, and Calm with any other parry refund at 6 FE per
parry. W3 clears the ledger on disconnect and server stop. Two parries in the same game tick count as one moment.

## 6. Equipment changes (9.2)

`ServerEntityEvents.EQUIPMENT_CHANGE` **[V]** (`onChange(LivingEntity, EquipmentSlot, ItemStack previous, ItemStack
current)`; there is no inventory-change event). For an armour slot where either stack has the traits component:

1. `updateKnockback(entity, worn)` adds or removes the Iron Discipline modifier (players and mobs alike, so an armour
   stand or a mob in the set gets it too).
2. For a player: `Progression.refreshModifiers(player)` recomputes max energy at once.
3. For a piece put on: `QuestHooks.onArmourEquipped(player, set)` (the codex, W2).
4. When this piece completed the set (4/4 now, and it did not replace a piece of the same set) and the player has been
   in the world for more than 5 ticks: `EffectEvents.toPlayer(player, ARMOUR_SET, pos, null, id, 1, set ordinal)`.
   The tick check stops the flourish on every join and respawn, when the worn armour is applied to a fresh entity.
   Swapping one piece of a full set for another piece of the same set (a robe for the Knight's robe) does not send
   it again. Taking a piece off and putting it back does: "once per equip". When several pieces land in the same tick
   (`/oldrepublic armour give`, a mission's `setItemSlot` calls, a datapack), the event fires once per slot after every
   slot is filled, so each would see the set complete: a per-player map of the game time of the last send lets only
   the first through in a tick. It is cleared when the player leaves and when the server stops.

## 7. `ArmourSets` (frozen API, 15.1)

- `count(e, set)`, `fullSet(e, set)`: from the components of the four armour slots; work on both sides.
- `piece(set, slot)`: a new stack of the set's piece for HEAD, CHEST, LEGS or FEET, else empty. W2 hands out the hood
  and robe with it.
- `disguise(e)`: always `Allegiance.NONE`. **9.5 (uniform disguise) is cut**, first on the cut list:
  `Allegiances.of(player)` reads `disguise`, so a live disguise would make every trooper bolt pass through the wearer
  (`BlasterBolt.canHitEntity` skips friendly entities), which 9.5 never intends. The `disguiseBreakTicks` and
  `disguiseBreakRange` keys stay in `EquipmentBalance`, documented as unused.
- Additions: `rankRobe(set)` (the Knight's or Lord's robe, else empty; W2 uses it at rank 3) and
  `boltGuardDrainMultiplier(e)` (for W5); `worn(e)` and `wearer(e)` for the client and the tests.

## 8. Recipes and tags (9.3)

All recipes are `minecraft:crafting_shaped`, `category: "equipment"`, in the vanilla armour shapes, and fill the shape
exactly (tag ingredients written as `"#oldrepublic:..."` [V]):

| Piece | Shape | Trooper (D = `durasteel_plate`, C = commendation) | Robe (W = cloth tag, T = thread or silk, L = leather) |
|---|---|---|---|
| Helmet / hood | `XCX`, `X X` | 4 D + 1 C | 4 W + 1 T |
| Chestplate / robe | `X X`, `XCX`, `XXX` | 7 D + 1 C | 6 W + 2 T (`W W`, `TWT`, `WWW`) |
| Leggings / trousers | `XCX`, `X X`, `X X` | 6 D + 1 C | 6 W + 1 T |
| Boots | `C D`, `D D` / `W T`, `L L` | 3 D + 1 C | 2 L + 1 W + 1 T |

The Republic set uses `republic_commendation`, the Sith set `imperial_commendation`; a full trooper set costs 20
durasteel and 4 commendations. The Jedi robes use `#jedi_robe_cloth` (white, light grey, brown wool) and `lumen_thread`;
the Sith robes `#sith_robe_cloth` (black, grey, red wool) and `grave_silk`. The rank 3 robes have no recipe. Troopers
never drop armour or commendations (W4 part A's loot rule).

Repair tags: `repairs/trooper_armour` = `durasteel_plate`, `repairs/jedi_robes` = `lumen_thread`,
`repairs/sith_robes` = `grave_silk`.

## 9. Tooltips (9.1)

- `ArmourItem#appendHoverText` **[V]** (the 5-argument 26.2 form, as `saber/HiltPartItem`): the set name; for a rank 3
  robe an italic line saying it counts as the set's robe; each non-zero per-piece trait with its exact signed number
  (blue when it helps the wearer, red when it costs); then "Set bonus (4/4): <name>" and the bonus lines with their
  numbers (gate, refund, window in seconds, cost change).
- The live line needs the viewer's worn armour and alignment, which `appendHoverText` does not receive, so
  `ArmourTooltips` adds it through `ItemTooltipCallback.EVENT` **[V]** right under the item name, reading
  `Minecraft.getInstance().player`: "Set n/4" (grey), "Set 4/4 · bonus active" (green), or "Set n/4 · requires Light
  (+15)" / "requires Dark (-15)" (gold) when the robe gate is not met.
- The client cannot read the server's balance file, so the tooltip and the toast show the default numbers
  (`new EquipmentBalance()`); the server always decides with the live values.

## 10. Rendering (9.4)

**Trooper sets:** vanilla draws them from their equipment assets (`equipment/<asset>.json`, layers `humanoid` and
`humanoid_leggings` [V], textures `textures/entity/equipment/humanoid[_leggings]/<asset>.png`, 64x32 in the vanilla
armour layout). Troopers (the mobs) wear composited skins made from the same layers, so a trooper and a player in the
set look alike.

**Robes:** `RobeArmorRenderer`, registered with `ArmorRenderer.register(RobeArmorRenderer::new, EquipmentItems.ROBES)`
**[V]** for the ten robe items. Fabric cancels vanilla armour rendering for every item with a renderer, and the
registration is permanent, so the renderer reads `ClientConfig.robeModels` on every draw:

- **On (default):** the slot's `RobeModel` layer with its 64x64 texture `textures/entity/robe/<asset>.png`.
- **Off:** vanilla's own armour shapes, baked into this mod's layers `oldrepublic:robe_plain#{head,chest,legs,feet}`
  from `HumanoidModel.createArmorMeshSet(0.5, 1.0)` at 64x32 **[V]**, with the plain equipment layer texture.

Both paths draw through `ArmorRenderer.submitTransformCopyingModel(contextModel, state, model, state, false, ...,
RenderTypes.armorCutoutNoCull(texture), light, NO_OVERLAY, 0, null)` **[V]**, so the robes copy the wearer's pose (the
saber and Force poses included), and a second pass with `RenderTypes.armorEntityGlint()` when the stack has foil.
`shouldRenderDefaultHeadItem` returns false, so a hood is never drawn as an item on the head.

**Deviation from 9.4's wording:** the plain path does not call `EquipmentLayerRenderer#renderLayers` (the meaning of its
last two `int` parameters is not in the dumps). It draws the same plain layer texture on vanilla's own armour shapes
through the verified transform-copy call, which looks the same.

`RobeModel` layers (`oldrepublic:robe#<name>`), each a full humanoid skeleton (so the transform copy finds every vanilla
part) with cubes only on the slot's parts. The extra parts are children named `robe_*`, so the copy leaves their own
pose alone:

| Layer | Parts |
|---|---|
| `hood` | a deep hood (8x8x8 at 1.05 inflation) with a brim over the brow, side flaps framing the face, a cowl over the shoulders |
| `robe` | the robe body, wide sleeves with bell cuffs, a belt, a back panel hanging from the waist |
| `rank_robe` | the robe plus the rank 3 mantle over the shoulders and a diagonal sash |
| `trousers` | a waist band, loose legs, and front and back skirt panels on each leg (children of the legs, so they follow them) |
| `boots` | boots with cloth wraps at the ankle |

The UV layout is in the `RobeModel` Javadoc and in `ROBE_UV` in `tools/assets/gen_textures_equipment.py` (see
`docs/assets/equipment.md`).

## 11. Set-bonus toast and flourish (11.2, 13.3)

The `armour_set` event reaches only the wearer (`toPlayer`). `EquipmentEffectsClient`:

- **Sound:** `armour.set_bonus` through the UI sound helper, pitched per set (Republic plates 1.0, Imperial plates 0.9,
  Jedi robes 1.06, Sith robes 0.88).
- **Toast (`SetBonusToast`):** a HUD element attached before `VanillaHudElements.CHAT` **[V]**. A framed plate slides in
  from the right edge at the top right (8 ticks), stays, and slides out (10 ticks), 70 ticks in all, with a timer line
  running out along its bottom. It shows the chest piece's icon, the bonus name ("Calm of the Watch") and either "Set
  bonus active" or, for a robe set whose gate is not met, "Set complete · requires Light (+15)". A newer completion
  replaces the toast. The vanilla toast manager is not used (`Minecraft#getToastManager()` is not in the dumps).
- **Flourish, phase by phase** (about 12 particles through `Vfx.count`, following the wearer):
  - *Activation:* trooper sets: the shoulder clasps catch the light, a soft glow in the army colour and two steel
    glints per clasp. Robes: a soft cloth puff at each shoulder as the robe settles.
  - *Aftermath (6 ticks, a step every 2):* trooper sets: glints run down the chest straps toward the belt as the plates
    settle. Robes: a few loose fibres drift down from the hood's edge.
  - In first person the flourish is moved a little forward so the wearer sees it at the bottom of the view. With
    flashing effects off, the clasp glow is a slow steady one. No ground ring.
- **Equip sounds:** vanilla plays the material's equip sound (`armour.equip_trooper`, `armour.equip_robe`) on equip.

## 12. Command (10.8)

`/oldrepublic armour give <player> <set> [rank3]` (the existing `OldRepublicCommands` permission pattern, a single
player argument): each piece goes into its armour slot when that slot is empty, else into the inventory, else it is
dropped at the player. `rank3` swaps the robe for the set's rank 3 robe and fails for the trooper sets. Messages:
`commands.oldrepublic.armour.*`.

## 13. Balance (`EquipmentBalance`, section `equipment`)

| Key | Default | Use |
|---|---|---|
| `trooperCostPct`, `trooperRegenPct` | 5, -5 | per trooper piece (live) |
| `trooperSpeedPct` | -1.5 | per trooper piece, speed attribute (**registration default only**) |
| `republicBlasterPct`, `republicSetBlasterPct`, `republicSetBoltDrainPct` | -6, -10, -25 | Republic per piece, Shoulder to Shoulder (live) |
| `sithMeleePct`, `sithSetControlPct`, `sithSetKnockbackResist` | -4, -30, 0.2 | Sith trooper per piece, Iron Discipline (live) |
| `jediRegenPct`, `jediCostPct`, `jediSetParryFe`, `jediSetHealCost` | 6, -2, 5, -15 | Jedi robes, Calm of the Watch (live) |
| `sithMaxEnergy`, `sithCostPct`, `sithSetKillFe`, `sithSetWindowTicks`, `sithSetLightningCost` | 5, -2, 8, 160, -15 | Sith robes, Hunger of the Tomb (live) |
| `setAlignment` | 15 | the robe gates (live; the tooltip and toast show the default) |
| `trooperDurability`, `robeDurability`, `trooperEnchant`, `robeEnchant` | 18, 10, 9, 15 | materials (**registration default only**) |
| `disguiseBreakTicks`, `disguiseBreakRange` | 2400, 24 | unused: 9.5 is cut |

Items and materials are registered at mod init, before any balance file is loaded, and calling
`BalanceConfig.section` that early would also overwrite the balance file (its root is still empty). So durability,
enchantability and the speed attribute come from `new EquipmentBalance()`, and a changed file value for those keys does
not apply (KNOWN_ISSUES). Every other key is read live on the server.

## 14. Save/load, death, multiplayer

- Everything worn lives on vanilla item stacks with a codec'd component, so saving, death drops, `keepInventory`,
  dimension changes and relogs need nothing extra. The component's `CODEC` saves the set by id and the numbers as
  optional fields; its `STREAM_CODEC` syncs the set by ordinal.
- The Iron Discipline modifier is transient: it is re-added by the equipment change a fresh entity sees when its armour
  is applied after a load, respawn or dimension change.
- `SetBonuses` keeps only short-lived maps (power hits within the window), cleared when the server stops.
- Every number is decided on the server. Other players see the armour through vanilla equipment sync; only the wearer
  gets the toast and the flourish.

## 15. Tests and open points

`EquipmentGameTests` (17.1; every test declares `maxTicks = 20`, uses the default empty structure and listed mock
players removed at the end, no global state):

| Test | Checks |
|---|---|
| `perSlotModifierIdsStack` | four trooper pieces apply four distinct speed modifiers (`armour.<slot>.speed`) the way vanilla applies them (`ItemStack#forEachModifier`), and removing one leaves three |
| `perPieceAndSetTraits` | each set's per-piece traits through `EquipmentModifiers` (cost, regen, max energy, blaster and melee damage from a real bolt and mob attack source), mixed sets, the bonuses at 4/4 and not below, the robe gates, a Balanced Independent's Lift discount, the Knight's robe counting as the robe, `boltGuardDrainMultiplier` |
| `workedExample9_1` | the 9.1 worked example (x1.216, x0.815, -6%; x1.262, x0.922) |
| `ironDisciplineShortensEveryKind` | every control kind (`stagger`, `slow`, `disarm`, `shock`, `hold`) x0.7 at 4/4 and untouched below; the transient knockback modifier comes and goes with the fourth piece |
| `calmAndHungerIncludingBoltParries` | over three ticks: Calm on a bolt parry and on a melee parry, a second refund for the same parry stopping at `parryRefundCap`, the light gate and a Balanced Independent; Hunger on a kill after a power hit, a `tomb_hunger` refund plus Hunger for one kill stopping at `killRefundCap`, the window's inclusive edge |
| `recipesFitShapesAndCostCommendations` | all 16 recipe files fill the vanilla armour shape exactly with the 9.3 costs; each loaded recipe (`level.recipeAccess()`) matches the costed grid and refuses the other army's commendation; no recipe for the rank 3 robes |
| `repairTagsResolve` | the repair tags hold `durasteel_plate`, `lumen_thread` and `grave_silk`; each cloth tag holds its three wools and none of the other's; every piece is in the vanilla `enchantable/armor`, `equippable` and `durability` tags |
| `equipmentTraitsCodecRoundTrip` | the component's codec and stream codec round trip for every set |

Client screenshots (17.2, in `m3/TrooperClientSteps`): `oldrepublic_m3_47_armour_robe_models` (the four sets and both
rank 3 robes on armour stands, and the player in the Jedi robes in third person), `oldrepublic_m3_48_armour_plain` (the
same with `robeModels = false`), `oldrepublic_m3_49_set_tooltip` (the inventory with the cursor on the worn robe).

Open points:

- Shoulder to Shoulder's drain reduction needs W5 to call `ArmourSets.boltGuardDrainMultiplier` in `SaberGuard`.
- The robe gate shown on the client uses the default `setAlignment`.
- How the rank 3 robes are handed out at rank 3 is W2's (`ArmourSets.rankRobe`).
