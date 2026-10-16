# Saber feature design (Milestone 1)

Owner: saber worker. Covers the kyber crystal item, the six hilt components, the lightsaber, the Lightsaber Workbench, guard/parry/deflection combat, saber audio, blade rendering and the swing trail.
Spec sources: `docs/MILESTONE_1.md` (Lightsaber, journey steps 9-10, shared id table) and `docs/VFX_SFX_SPEC.md` (Lightsaber, Workbench).

Verification legend: **[V]** verified against the 26.2 probe dumps or Fabric 26.2 source/testmods; **[U]** unverified, a probe in `tools/api-probe/saber.txt` covers it; **[D]** a design choice (no API question).

---

## 1. Packages and classes

### Common (`src/main/java/com/oldrepublic/saber`)
| Class | Role |
|---|---|
| `SaberFeature` | Entry point: registers everything below, events, payload, projectile guard, commands. |
| `SaberBalance` | Balance section `"saber"` (public fields, see section 8). Read via `SaberToggle.balance()` (`BalanceConfig.section`) on the server only. |
| `SaberItems` | Registers `kyber_crystal`, the six parts, `lightsaber`, `lightsaber_workbench` (block + item) through `ModRegistry`; `partItem(part)`, `buildSaber(...)`. Creative tab: two pre-built training sabers (crystal owned by nobody). |
| `HiltParts` | `Part` interface and the enums `Emitter {GUARDIAN, DUELIST}`, `Grip {WRAPPED, RIDGED}`, `Pommel {WEIGHTED, BALANCED}` with codecs and item ids. |
| `BladeColor` | The eight-colour palette (see 4.3); index, name key, RGB; `nearest(int rgb)` for the default pick from the crystal's natural colour. |
| `SaberComponents` | Feature-private data components (section 2). |
| `SaberStats` | Pure computation: parts + balance -> damage, attack speed, reach bonus, max guard stamina, parry ticks; builds the `ItemAttributeModifiers` for ignited/retracted. Used by the item, the workbench preview, the guard and the game tests. |
| `LightsaberItem` | Use (sneak = toggle, else start guard), use animation BLOCK, use duration, tooltip, `inventoryTick` retract when not in a hand, `allowComponentsUpdateAnimation` = false. |
| `KyberCrystalItem`, `HiltPartItem` | Tooltips (owner and natural hue; stat change of the part). |
| `SaberToggle` | Server-side ignite/retract: flips state, rewrites attribute modifiers, plays `saber.ignite` / `saber.retract`. Shared by the payload, sneak-use and forced retracts. |
| `network/ToggleSaberPayload` | C2S, empty record payload; the server picks the saber in the main hand, else the off hand. |
| `SaberGuard` | Guard stamina (server map + synced attachment `oldrepublic:saber_guard` holding `GuardSnapshot`), melee blocking and parries, projectile guard, guard break, stagger. |
| `SaberAssembly` | Workbench rules independent of the menu: slot rules, status, atomic `assemble` / `disassemble`, stat preview. Covered by `SaberGameTests`. |
| `SaberEffects` | Effect event ids (section 6.2). |
| `SaberParticles` | Registers `oldrepublic:saber_spark` and `oldrepublic:contact_flash`. |
| `workbench/LightsaberWorkbenchBlock` | Horizontal facing block; `useWithoutItem` opens the menu like `CraftingTableBlock` (no block entity: the bench stores nothing). |
| `workbench/LightsaberWorkbenchMenu` | Menu type registration; transient 5-slot container (crystal, emitter, grip, pommel, saber) + player inventory; inner `BenchSlot` with `mayPlace` from `SaberAssembly`; 8 data slots; `clickMenuButton` actions (section 4). |
| `SaberCommands` | `/oldrepublic saber give [<colour> <emitter> <grip> <pommel>]`, `crystal [<colour>]`, `parts`, `stamina [refill]`. |

### Client (`src/client/java/com/oldrepublic/client/saber`)
| Class | Role |
|---|---|
| `SaberClient` | Entry point: registration, ticking, level-change and disconnect resets. |
| `SaberModelProperties` | `BladeExtension` (range property `oldrepublic:blade_extension`), `HiltPart` (select property `oldrepublic:hilt_part` with a `part` field), `BladeColorTint` (`oldrepublic:blade_color`), `CrystalColorTint` (`oldrepublic:crystal_color`), and their registration in the vanilla `ID_MAPPER`s. |
| `SaberKeys` | `Toggle Lightsaber` key (default G, category `ForceKeys.CATEGORY`), sends `ToggleSaberPayload` on `consumeClick()` while a saber is in hand. |
| `SaberAudio` | Per-player hum loops (`SaberHumSound`, within 32 blocks, once the blade is fully out), client-detected swing sounds (rising edge of `swinging`). |
| `SaberHumSound` | `AbstractTickableSoundInstance`, follows one player, stops itself. |
| `SaberFx` | Effect-event handlers: sparks, contact flash, sounds (clash/deflect rate-limited), camera kick, workbench assemble burst. |
| `SaberParticleProviders` | `Spark` and `Flash` (`SingleQuadParticle`, full-bright, tintable); `SaberFx` creates them through `particleEngine.createParticle` and tints them. |
| `GuardHud` | Stamina bar under the crosshair. |
| `SwingTrailRenderer` | Third-person blade-colour ribbon (section 6.4). |
| `workbench/LightsaberWorkbenchScreen` | Colour swatches, stat preview, Construct and Disassemble buttons. |

### Mixins
`mixin/saber/LivingEntityGuardMixin` (common): `@ModifyVariable` at the head of `LivingEntity#hurtServer` on the damage amount calls `SaberGuard.modifyIncomingDamage`. This replaces the earlier plan of cancelling in `ALLOW_DAMAGE` and re-applying a reduced hit (that would have scaled player damage by difficulty twice, since `Player#hurtServer` scales before calling the `LivingEntity` method). A parry returns 0 and marks the hit; `ALLOW_DAMAGE` then cancels it so there is no knockback, hurt flash or invulnerability window.

---

## 2. Data components

All registered in `SaberComponents` with `DataComponentType.builder()` **[V]** (`persistent`, `networkSynchronized`, `ignoreSwapAnimation` exist on `DataComponentType$Builder` in core.txt).

### `oldrepublic:saber_config` (persistent, synced) - `SaberConfig`
```
record SaberConfig(CrystalAttunement crystal, BladeColor color, Emitter emitter, Grip grip, Pommel pommel)
```
- `crystal` reuses `ModComponents.CrystalAttunement` (owner UUID, owner name, natural colour) so disassembly gives back the **same** attuned crystal (no loss, no forging). The saber's "owner" is `crystal.owner()/ownerName()`.
- `color` is the cosmetic blade colour (palette entry, stored by its serialized name). No stat effect.
- Codec: `RecordCodecBuilder` with `CrystalAttunement.CODEC`, `StringRepresentable.fromEnum` codecs **[U: `StringRepresentable.fromEnum` exists in 26.2; very likely]**. Stream codec: `StreamCodec.composite` (as in `ModComponents`) **[V]**.

### `oldrepublic:saber_state` (persistent, synced, `ignoreSwapAnimation`) - `SaberState`
```
record SaberState(boolean ignited, long toggledAt)
```
- `toggledAt` = server `level.getGameTime()` at the toggle. The client compares with `ClientLevel#getGameTime()` (game time is synced) to animate the blade.
- Persistent so a saber stays ignited across relog only while held; `inventoryTick` and the item-entity hook retract it otherwise (section 5.6).

### Attribute modifiers
The stack's `DataComponents.ATTRIBUTE_MODIFIERS` is rewritten on construct and on every toggle by `SaberStats.modifiers(config, ignited, balance)` **[V: `ItemAttributeModifiers.builder()`, `Item.BASE_ATTACK_DAMAGE_ID`, `Item.BASE_ATTACK_SPEED_ID`, `Attributes.ATTACK_DAMAGE/ATTACK_SPEED/ENTITY_INTERACTION_RANGE`, `EquipmentSlotGroup`]**. Builder `add(Holder, AttributeModifier, EquipmentSlotGroup)` and `AttributeModifier(Identifier, double, Operation)` **[U, probe `ItemAttributeModifiers$Builder`, `AttributeModifier$Operation`]**.
- Ignited: damage modifier = `7 + emitter + pommel - 1` (player base 1); speed modifier = `1.6 + deltas - 4.0` (player base 4); reach `+0.25` for Guardian on `ENTITY_INTERACTION_RANGE` (id `oldrepublic:saber_reach`). 26.2 also has an `AttackRange` item component; if probes show melee reach is read from it instead of the attribute, reach goes there **[U]**.
- Retracted: damage modifier `0` (hilt does 1), same speed.

### Re-equip bob
Two layers so toggling never bobs the hand:
1. `saber_state` is built with `ignoreSwapAnimation()` **[V exists; U semantics: probe `ItemInHandRenderer#shouldInstantlyReplaceVisibleItem`]**.
2. `LightsaberItem` overrides `FabricItem#allowComponentsUpdateAnimation(...)` to return false when old and new stacks are both lightsabers **[V: Fabric `ItemInHandRendererMixin` replaces the held stack instantly when this returns false]**. This also covers the attribute-modifier rewrite.
3. Item definition `"hand_animation_on_swap": false` **[U: field name, probe `ClientItem$Properties`]** (only for real item switches; kept true if that would hide genuine swaps, decided after probes).

---

## 3. Item model plan (26.2 client item definitions)

### 3.1 Registration of custom properties and tints (client init)
- `RangeSelectItemModelProperties.ID_MAPPER.put(OldRepublicMod.id("blade_extension"), BladeExtensionProperty.MAP_CODEC)` **[V: field is public in javap and made transitive-accessible by Fabric; Fabric's own testmod uses `SpecialModelRenderers.ID_MAPPER.put(id, codec)` the same way]**.
- `ItemTintSources.ID_MAPPER.put(OldRepublicMod.id("blade_color"), BladeColorTint.MAP_CODEC)` and `crystal_color` **[V same]**.
- `SelectItemModelProperties.ID_MAPPER.put(OldRepublicMod.id("hilt_part"), HiltPartProperty.TYPE)` **[U: the mapped value is a `SelectItemModelProperty$Type`, probe its factory]**.
- Interfaces **[V]**: `RangeSelectItemModelProperty#get(ItemStack, ClientLevel, ItemOwner, int seed) -> float`, `#type() -> MapCodec`; `ItemTintSource#calculate(ItemStack, ClientLevel, LivingEntity) -> int` (ARGB), `#type()`.
- Registration happens in `SaberClient.init()`, i.e. before resource loading.

### 3.2 `BladeExtensionProperty`
`get` returns 0 when the stack has no `saber_state`; otherwise `t = clamp((gameTime - toggledAt) / 6, 0, 1)` and the result is `ignited ? t : 1 - t`. Level may be null (GUI): then `ignited ? 1 : 0`. Six ticks give the spec's 6-tick extend/retract in 7 discrete steps (no partial tick in this API; acceptable at 20 TPS, each step is 1/6 of the blade).

### 3.3 `assets/oldrepublic/items/lightsaber.json`
As implemented (generated by `tools/assets/gen_textures_saber.py`): `gui`, `fixed` and `on_shelf` show the flat icon, itself a `range_dispatch` on `oldrepublic:blade_extension` (lit icon above 0.5); every other context (hands, ground, head) uses the 3D composite. Blade thresholds are `(k - 0.5) / 6` for `blade_k`, k = 1..6, with fallback `blade_0`, an empty model (`minecraft:empty` was not verified). `hand_animation_on_swap` is not written: its JSON key was not verified, and the two other layers already prevent the bob. The original plan follows.
```json
{
  "hand_animation_on_swap": false,
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:display_context",
    "cases": [
      { "when": ["gui", "ground", "fixed"], "model": { "type": "minecraft:model", "model": "oldrepublic:item/lightsaber_icon" } }
    ],
    "fallback": {
      "type": "minecraft:composite",
      "models": [
        { "type": "minecraft:select", "property": "oldrepublic:hilt_part", "part": "emitter",
          "cases": [ { "when": "guardian", "model": { "type": "minecraft:model", "model": "oldrepublic:item/lightsaber/emitter_guardian" } },
                     { "when": "duelist",  "model": { "type": "minecraft:model", "model": "oldrepublic:item/lightsaber/emitter_duelist" } } ],
          "fallback": { "type": "minecraft:model", "model": "oldrepublic:item/lightsaber/emitter_guardian" } },
        { "...": "same for grip (wrapped/ridged) and pommel (weighted/balanced)" },
        { "type": "minecraft:range_dispatch", "property": "oldrepublic:blade_extension",
          "entries": [
            { "threshold": 0.16, "model": { "type": "minecraft:model", "model": "oldrepublic:item/lightsaber/blade_1", "tints": [ { "type": "oldrepublic:blade_color" } ] } },
            { "threshold": 0.33, "model": { "...": "blade_2" } },
            { "threshold": 0.50, "model": { "...": "blade_3" } },
            { "threshold": 0.66, "model": { "...": "blade_4" } },
            { "threshold": 0.83, "model": { "...": "blade_5" } },
            { "threshold": 0.99, "model": { "...": "blade_6" } }
          ],
          "fallback": { "type": "minecraft:empty" } }
      ]
    }
  }
}
```
Verified pieces **[V]**: `minecraft:select` + `cases`/`when`/`fallback`, `minecraft:range_dispatch` + `entries`/`threshold`/`fallback`/`property`, `minecraft:model` + `tints` (bow, crossbow, leather_horse_armor dumps). **[U]**: `minecraft:display_context` select with a list in `when`, `minecraft:composite` + `models`, `minecraft:empty`, `hand_animation_on_swap` (probes: `spyglass.json`, `trident.json`, `ClientItem$Properties`, `CompositeModel`, `find:client/renderer/item/...`). The item definition's top-level `"transformation"` (seen in `shield.json` **[V]**) is available if the blade needs extra scale.

### 3.4 Fallback if a custom select property is awkward
Write the parts into vanilla `minecraft:custom_model_data` strings on construct (`strings: [emitter, grip, pommel]`) and select with `"property": "minecraft:custom_model_data", "index": 0..2` **[U: probe select `CustomModelDataProperty`]**. Data stays authoritative in `saber_config`; CMD is a derived render hint written at the same time.

### 3.5 Models (`assets/oldrepublic/models/item/...`)
- Hilt part models: elements only (no `parent: generated`), 16x16 texture `oldrepublic:item/lightsaber_hilt` (atlas strip of durasteel, grip wrap, ridges, pommel cap), common `display` block copied from `handheld.json` **[V]** and tuned so the hilt sits in the fist.
- Coordinates: hilt along +Y from y=-6 to y=6 (emitter top at y 6), blade from y 6 upward. Element bounds are limited to -16..32 **[V: vanilla limit, unchanged as far as known; U for 26.2]**, so the full blade is y 6..32 (26 units) and display `scale` 1.0-1.2 gives about a 1.7-1.9 block total length in third person.
- Blade models `blade_1`..`blade_6`: two elements each, length `26 * n / 6`:
  - core: 1x1 cross-section, white texture, **no tint**, `"light_emission": 15`, `"shade": false`.
  - glow: 2x2 cross-section around the core, semi-transparent white texture with `"tintindex": 0` on all faces, `"light_emission": 15`, `"shade": false`. Tint index 0 maps to `oldrepublic:blade_color` in the item definition.
  - Translucency: item models render translucent if the texture has partial alpha **[U: 26.2 item render type selection; if the glow renders cutout, use a solid tinted glow with a slightly darker edge texture instead]**.
  - `light_emission` element field **[U, probe `BlockElement$Deserializer` + vanilla models that use it]**.
- `lightsaber_icon`: `parent: item/generated` with `layer0` hilt sprite and `layer1` blade sprite tinted by layer index 1 **[V: generated layers + tints by index]**; uses `tints: [ {constant white}, {oldrepublic:blade_color} ]`.
- `kyber_crystal`: custom elements model (two crossed planes like `cross.json` **[V]**) with `light_emission` 15 and `tintindex 0` -> `oldrepublic:crystal_color` (natural colour from `CRYSTAL_ATTUNEMENT`, white if absent). GUI uses the same model with a `gui` display transform.
- Part items (`emitter_guardian` ...): `parent: item/generated` flat icons.

### 3.6 Workbench block
`blockstates/lightsaber_workbench.json` with `facing=north|east|south|west` variants and `y` rotations (format as `amethyst_cluster.json` **[V]**). `models/block/lightsaber_workbench.json`: a table with elements (top slab, legs, a vice and a small lamp element with `light_emission` 10). Block properties: `strength(3.5f)`, `SoundType.METAL`, `noOcclusion()` **[V: BlockBehaviour$Properties]**. Loot table `data/oldrepublic/loot_table/blocks/lightsaber_workbench.json` (self drop).

---

## 4. Workbench

### 4.1 Block
`LightsaberWorkbenchBlock extends HorizontalDirectionalBlock` (facing placed opposite the player) **[U: probe class, `codec()` requirement]**. `useWithoutItem(state, level, pos, player, hit)` -> server: `player.openMenu(state.getMenuProvider(level, pos))`, return `InteractionResult.SUCCESS` **[V: Fabric menu testmod `BoxBlock`]**. `getMenuProvider` returns `new SimpleMenuProvider((id, inv, p) -> new LightsaberWorkbenchMenu(id, inv, ContainerLevelAccess.create(level, pos)), title)` **[V: SimpleMenuProvider, ContainerLevelAccess]**.

### 4.2 Menu
- `MenuType` registered with `new MenuType<>(LightsaberWorkbenchMenu::new, FeatureFlags.VANILLA_SET)` in `BuiltInRegistries.MENU` **[V: Fabric menu testmod]**. No extra opening data needed.
- Container: `SimpleContainer(5)` owned by the menu (transient, like the crafting table). Slots: 0 crystal, 1 emitter, 2 grip, 3 pommel, 4 saber; then `addStandardInventorySlots(playerInventory, 8, 84)` **[V]**.
- Slot rules (`mayPlace`): crystal slot accepts only `kyber_crystal` whose `CRYSTAL_ATTUNEMENT.owner` equals the menu player's UUID (an unattuned crystal is refused too); emitter/grip/pommel only matching part items; saber slot only `lightsaber`. All max stack 1.
- `ContainerData` (`SimpleContainerData` + `addDataSlots`) **[U: probe]**: [0] selected colour index, [1] damage x100, [2] attack speed x100, [3] reach bonus x100, [4] max guard stamina, [5] parry window ticks x10, [6] status code (ready / missing parts / not your crystal / saber present). Server recomputes in `slotsChanged` from `SaberBalance`, so the preview shows exact server numbers (clients never read balance).
- Buttons via `clickMenuButton(Player, int)` **[V]**, sent by the screen with `minecraft.gameMode.handleInventoryButtonClick(containerId, id)` **[U: probe `MultiPlayerGameMode`]**:
  - `0..7` select colour (if a saber sits in slot 4 and the part slots are empty, this recolours it in place; only the crystal's owner may recolour).
  - `100` Construct: server checks crystal owned by the player, all three parts present, saber slot empty. Consumes the four inputs, builds the saber stack (`saber_config`, retracted `saber_state`, modifiers) into slot 4, plays the assemble effect event, calls `QuestHooks.onSaberConstructed(player)`.
  - `101` Disassemble: saber in slot 4 whose crystal owner is the player, the four input slots empty -> removes the saber, puts the attuned crystal and the three parts into slots 0-3, colour index set from the saber. Swap parts, Construct again = reconfigure.
- Duplication safety: every change happens server side inside one `clickMenuButton` call; slots are filled/emptied atomically; `removed(player)` calls `clearContainer(player, container)` **[V signature; U body, probe `AbstractContainerMenu#clearContainer`/`CraftingMenu#removed`]**, which returns items to the inventory or drops them; disconnect closes the menu through the same path. `quickMoveStack` moves single items into the first matching input slot.
- `stillValid`: `stillValid(access, player, SaberItems.WORKBENCH_BLOCK)` **[V]**.
- Sounds: `workbench.slot` played by the server (`level.playSound(null, pos, ...)`) when a part slot receives an item (`Slot#setChanged` override on the server side only).

### 4.3 Palette (index, name, RGB)
0 blue `#2F6BFF`, 1 green `#36E05A`, 2 yellow `#FFD43B`, 3 violet `#A24DFF`, 4 cyan `#3BE3E8`, 5 orange `#FF8C2E`, 6 red `#FF2E2E`, 7 white-silver `#E6EDF5`. Default selection when a crystal is placed: nearest to its natural colour.

### 4.4 Screen
`LightsaberWorkbenchScreen extends AbstractContainerScreen<LightsaberWorkbenchMenu>`, registered with `MenuScreens.register(type, LightsaberWorkbenchScreen::new)` **[V]**. Rendering follows the Fabric testmod `PositionedScreen`: override `extractBackground(GuiGraphicsExtractor, mouseX, mouseY, delta)` and blit our own `textures/gui/container/lightsaber_workbench.png` with `RenderPipelines.GUI_TEXTURED` **[V]**; `extractLabels` for text **[V]**.
- Colour swatches: eight 12x12 `fill` squares with an outline on the selected one; clicks handled in `mouseClicked(MouseButtonEvent, boolean)` **[V signature; U: `MouseButtonEvent` accessors x()/y()/button(), probe]**.
- Buttons: `Button.builder(Component, onPress).bounds(...).build()` + `addRenderableWidget` **[V]**; Construct active only when the status data slot says ready; Disassemble when a saber is present.
- Stat preview panel (right side): Damage `7.00 -> 7.50`, Attack speed, Reach bonus, Guard stamina, Parry window (ticks), each in exact numbers from the data slots; status line explains what is missing (e.g. "This crystal does not answer to you").
- The saber in slot 4 renders through the item model (blade retracted).

---

## 5. Combat: ignite, guard, parry, deflection

### 5.1 Ignite / retract
- Key `key.oldrepublic.toggle_saber`, default G (`GLFW_KEY_G` = 71), registered with `KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, 71, category))` **[V]**.
- Category: `com.oldrepublic.client.force.ForceKeys.CATEGORY` (owned and registered by the force feature, per lead decision). The saber feature never calls `KeyMapping.Category.register`.
- Sneak + use: `LightsaberItem#use` when `player.isShiftKeyDown()` -> server toggles, returns `InteractionResult.SUCCESS` (no guard).
- `ToggleSaberPayload` C2S registered with `PayloadTypeRegistry.serverboundPlay().register(TYPE, StreamCodec.unit(INSTANCE))` and `ServerPlayNetworking.registerGlobalReceiver` **[U: probe `PayloadTypeRegistry` method names in 26.2 (`playC2S` vs `serverboundPlay`); foundation uses `clientboundPlay()` so `serverboundPlay()` is expected]**. Server validates and prefers the main hand.
- Sounds: server `level.playSound(null, x, y, z, ModSounds.SABER_IGNITE, SoundSource.PLAYERS, 1, pitch)` (vanilla sound packet to nearby players).

### 5.2 Guard
- `LightsaberItem#use` (not sneaking, ignited, not on guard cooldown): `player.startUsingItem(hand)`, return `InteractionResult.CONSUME` **[V methods]**; `getUseAnimation` -> `ItemUseAnimation.BLOCK` **[V]**; `getUseDuration` -> 72000 **[V signature]**. Retracted hilt: `PASS`.
- No `BlocksAttacks` component on purpose, so vanilla `isBlocking()` stays false and vanilla shield logic does not interfere **[U: probe `LivingEntity#isBlocking` body]**. Third-person pose: vanilla uses `ArmPose.BLOCK` for BLOCK use animation **[U: probe `AvatarRenderer#getArmPose`]**.
- Guard start tick = `player.getTicksUsingItem()` **[V]**; parry window if `ticksUsing < parryTicks` where `parryTicks = 6 * (1 + 0.10 for Balanced)` = 6.6 (so ticks 0..6 for Balanced, 0..5 otherwise).
- Front check: attacker/projectile position within 100 degrees (dot of look and direction > cos 50 deg... uses `guardArcDegrees` from balance) of the player's horizontal look.

### 5.3 `oldrepublic:saber_guard` attachment
As implemented: the server keeps the full state (stamina, max, last drain tick, broken-until tick) in a map in `SaberGuard`, and the attachment only carries `GuardSnapshot(stamina, max, broken)` for the HUD, synced to the owner on every drain and break, and every 5 ticks while regenerating. The original plan follows.
`AttachmentRegistry.create(id, b -> b.initializer(GuardState::new).syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()))` **[V pattern from `ForceAttachments`]**; not persistent (refills on login), not copied on death. Fields: `stamina` (float), `max` (float, from grip), `lastDrainTick`, `brokenUntilTick`. Synced only when stamina changes by >= 1, on break/recover, or every 10 ticks while regenerating (keeps traffic small). Regen `guardRegenPerSecond` after `guardRegenDelayTicks` without drain; not while guarding.

### 5.4 Melee blocking (implemented with `LivingEntityGuardMixin`, see section 1 "Mixins"; the original plan below cancelled and re-applied in `ALLOW_DAMAGE`)
For a player guarding with an ignited saber, a source with a causing entity in front, and not `DamageTypeTags.BYPASSES_SHIELD` **[U tag name]**:
- Parry window: cancel (return false), stamina untouched, attacker staggered: knockback 0.6 away, `MobEffects.SLOWNESS` amp 3 + `WEAKNESS` for `staggerTicks` (20) **[U: 26.2 effect names, probe `MobEffects`]**; player attackers also get `resetAttackStrengthTicker()` **[V]**. Effect `saber_parry`.
- Otherwise: drain `meleeDrainBase + meleeDrainPerDamage * amount`. If stamina stays > 0: cancel and re-apply `amount * (1 - 0.70)` via `player.hurtServer(level, source, reduced)` guarded by a thread-local re-entrancy flag so the second call passes through; effect `saber_clash`. If stamina hits 0: guard break (5.6) and the full hit lands (return true).
- Contact point: `lerp(defenderEye + look * 0.9, attacker bounding-box centre, 0.35)`, clamped to the blade line.

### 5.5 Projectiles (`CombatHooks.setProjectileGuard`) **[V contract]**
For `DeflectableProjectile` hits on a guarding player with an ignited saber and the projectile coming from the front:
- Parry window -> `redirect(shooterEyeDirection * |velocity|, player, true)`, return `PARRIED`, `QuestHooks.onBoltDeflected(player, true)`, effect `saber_parry`. Without a shooter: aim back along the incoming direction.
- Otherwise drain `boltDrain`; if stamina remains -> `redirect(randomForwardArc(look, deflectArcDegrees) * |velocity|, player, false)`, return `DEFLECTED`, `onBoltDeflected(player, false)`, effect `saber_deflect`.
- If the drain empties stamina -> guard break, return `BLOCKED` (bolt absorbed by the breaking guard, no damage).
- Not guarding / behind / retracted -> `NONE`.
- Contact point = projectile position.
Non-deflectable projectiles (arrows) go through 5.4 as ordinary blocked hits.

### 5.6 Guard break, stagger and forced retracts
- Guard break: `stopUsingItem()`, `player.getCooldowns().addCooldown(stack, guardBreakTicks)` (20) **[U: `ItemCooldowns#addCooldown(ItemStack,int)` signature]**, slowness for 20 ticks, stamina 0 then regen after delay, effect `saber_guard_break` (loud sound, sparks, camera kick for the victim).
- Retract when not held: `inventoryTick(stack, level, entity, slot)` retracts if ignited and `slot` is not a hand slot **[V signature; U: slot value for non-equipment inventory slots, probably null]**. `ServerEntityEvents.ENTITY_LOAD` retracts ignited sabers inside new `ItemEntity`s (drop, death drops) **[V event]**.

### 5.7 Saber hits
`ServerLivingEntityEvents.AFTER_DAMAGE` with a player attacker holding an ignited saber and a direct melee source -> effect `saber_hit` (sizzle sound + small sparks at the target's hit point) **[V event; U handler signature]**.

---

## 6. Client presentation

### 6.1 Audio
- Hum: `SaberAudio` on `ClientTickEvents.END_CLIENT_TICK` **[V]** scans `level.players()` within 32 blocks; for each holding an ignited saber in either hand (blade extension > 0.5) without a running `SaberHumSound`, queues one (`minecraft.getSoundManager().play(...)`) **[V]**. The instance is `looping = true`, positional, volume 0.6, follows the player each tick, and calls `stop()` when the player is removed, dead, in a different level than `minecraft.level`, or no longer holding an ignited saber (covers retract, drop, death, dimension change, item switch, leaving range). On `ClientPlayConnectionEvents.DISCONNECT` the map is cleared. One hum per player (AUDIO_BRIEF rule).
- Swing: per tracked player, remember last `swinging`/`swingTime` (public fields **[V]**); a new swing (`swinging && swingTime == 0` after not swinging, or `attackAnim` restart) with an ignited saber plays `saber.swing` locally at the player with pitch +-8%. No packets.
- Ignite/retract, clash, deflect, parry, guard break, hit: from server sounds / effect events. Clash/deflect rate-limited to one per entity per 3 ticks on the client.

### 6.2 Effect events (all `oldrepublic:`)
| Id | pos | dir | entityId | intensity | variant |
|---|---|---|---|---|---|
| `saber_clash` | contact point | defender look | defender | drain amount | blade colour index |
| `saber_deflect` | contact point | new bolt dir | defender | 1 | colour |
| `saber_parry` | contact point | return dir | defender | 1 | colour |
| `saber_guard_break` | defender chest | look | defender | 1 | colour |
| `saber_hit` | target hit point | attacker look | target | damage | colour |
| `workbench_assemble` | bench top | up | player | 1 | colour |
Handlers: `Vfx.inRange` first; sparks `Vfx.count(14)` along a cone around `dir` (deflect) or the contact normal (clash); `contact_flash` one particle for one tick only if `Vfx.flashing()`, else a 4-tick low-alpha steady glow particle; sound with variant pitch; `CameraShake.add(0.15)` for the defender on clash, `0.35` on guard break, `0.1` on parry; assemble plays `workbench.assemble` and a `crystal_glint` burst above the bench.

### 6.3 Particles
`saber_spark`: tiny elongated sparks, coloured hot white -> blade colour, gravity 0.4, life 6-10 ticks, full-bright (override `getLightCoords` to `0xF000F0`) **[U: probe GlowParticle body]**; `contact_flash`: one camera-facing quad, size 0.6, life 1 tick. Registration: `FabricParticleTypes.simple()` + `Registry.register(BuiltInRegistries.PARTICLE_TYPE, ...)` and `ParticleProviderRegistry.getInstance().register(type, FabricSpriteSet -> provider)` with `SingleQuadParticle` + `getLayer()` **[V: Fabric particles testmod]**. Particle JSONs `assets/oldrepublic/particles/*.json` **[V format]**. `crystal_glint` belongs to the world feature: the client looks it up by id in `BuiltInRegistries.PARTICLE_TYPE` and falls back to `ParticleTypes.END_ROD` if absent.

### 6.4 Swing trail (third person)
As implemented: `RenderTypes.debugFilledBox()` (verified in the Fabric testmod, position + colour quads), each segment emitted in both windings. Samples are taken in the client tick (up to 6 per swing, lingering 4 ticks).
Rendering path: `LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES` (or `COLLECT_SUBMITS`) gives a `LevelRenderContext` with `poseStack()` and `submitNodeCollector()`; `submitNodeCollector().submitCustomGeometry(poseStack, renderType, (pose, buffer) -> ...)` draws custom vertices, translated by `-camera.pos` from `levelState().cameraRenderState.pos` **[V: Fabric `LevelRenderEventsTests`]**. Render type: `RenderTypes.lightning()` (position + colour, additive-like) **[U: vertex format, probe `RenderTypes#lightning` / `<clinit>`]**; fallback `RenderTypes.debugQuads()` **[V exists]**.
Geometry: for every player except the first-person camera entity, while swinging with an ignited saber, keep the last 6 sampled blade segments (base and tip in world space, approximated from body yaw, shoulder offset and swing progress, the same curve the vanilla arm swing uses), and draw a quad strip between consecutive samples with alpha fading 0.5 -> 0 and the blade colour. Limited to `Vfx.inRange`. Documented in KNOWN_ISSUES as an approximation (it follows the swing arc, not the exact animated hand).

### 6.5 HUD
`HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("guard_stamina"), element)` with `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` **[V]**. Draws a thin 40x3 bar 10 px below the crosshair (dark backing, pale blue fill, flashes amber below 25%, red during break) only while guarding or while stamina < max, fading out 1 s after full. Reads the synced `saber_guard` attachment.

### 6.6 Tooltips
`appendHoverText(stack, context, display, consumer, flag)` **[V signature]**. Crystal: "Attuned to <name>", "Natural hue: <colour name>" (nearest palette name), "Only its bearer can build with it" (grey). Saber: owner, blade colour, emitter/grip/pommel names, ignited state; attribute lines come from vanilla.

---

## 7. Recipes, loot, lang, assets
- `data/oldrepublic/recipe/lightsaber_workbench.json` (shaped, category equipment): `III / ACA / SSS` with `I` iron ingot, `A` amethyst shard, `C` crafting table, `S` smooth stone. Format as `shield.json` **[V]** (`"key": {"I": "minecraft:iron_ingot", ...}`, `"result": {"id": "oldrepublic:lightsaber_workbench"}`).
- `grip_wrapped`: ` L / SIS / L ` style with leather, string, iron ingot. `grip_ridged`: copper ingots + iron ingot. `pommel_weighted`: iron block + iron nugget; `pommel_balanced`: copper ingot + gold nugget + iron nugget. Emitters: **no recipe** (poacher drops via the enemy loot table and the hidden chamber chest; spec balance: exploration matters). Exact patterns in `docs/assets/saber.md`.
- Recipe unlock advancements: none in M1 (recipes visible in the book by default is not needed).
- Lang part `src/main/lang-parts/saber.json`: item/block names, tooltip lines, key name `key.oldrepublic.toggle_saber`, screen texts, palette names, stat labels, status messages, command feedback.
- Textures by `tools/assets/gen_textures_saber.py`: part icons, hilt atlas, blade core/glow, crystal, workbench block faces, GUI background, particle sprites. Listed in `docs/assets/saber.md`.

## 8. Balance (`SaberBalance`, section `"saber"`)
```
baseDamage 7.0, baseAttackSpeed 1.6, retractedDamage 1.0
guardianReach 0.25, guardianSpeed -0.1
duelistSpeed 0.15, duelistDamage -0.5
wrappedStaminaMultiplier 1.2, ridgedSpeed 0.1
weightedDamage 1.0, weightedSpeed -0.15, balancedParryMultiplier 1.1
guardStamina 100, guardRegenPerSecond 15, guardRegenDelayTicks 20
parryWindowTicks 6, guardArcDegrees 100, meleeReduction 0.70
meleeDrainBase 6, meleeDrainPerDamage 3, boltDrain 10
deflectArcDegrees 50, guardBreakTicks 20, staggerTicks 20, parryKnockback 0.6
```

## 9. Persistence, multiplayer, edge cases
- Saber and crystal data live on the stacks (persistent components) -> survive save/load, death drops, chests.
- Guard attachment is transient: refilled on login/respawn; no persisted combat state.
- Workbench holds nothing; items return to the player on close/disconnect; drops if the inventory is full.
- Others see ignition (synced components drive the model), hum (client-side per player), swings (client-side detection), sparks (one effect event per moment).
- Ignited saber in a dropped item entity is retracted on load.
- Held sabers are retracted (silently) on logout, after death (kept inventory) and on respawn; the guard state is forgotten on logout and respawn.

## 10. Open questions for the lead (all resolved)
1. Key category: resolved (use `ForceKeys.CATEGORY` from the force feature).
2. `data/minecraft/tags/block/mineable/pickaxe.json`: resolved, the lead owns it and lists `oldrepublic:lightsaber_workbench`.
3. `crystal_glint` particle id is owned by world; I only look it up.

---

# Milestone 2: expanded saber combat, NPC duelling, Saber Throw

Spec: `docs/MILESTONE_2.md` 8.3 and 9.1 to 9.6. Numbers live in `SaberBalance` (section `saber`) and
`SaberThrowBalance` (section `saber_throw`); the defaults match spec section 13.

## 11. New classes

| Class | Role |
|---|---|
| `saber.SaberCombat` | Server damage pipeline for every saber hit and every hit on a saber wielder. `modifyIncomingDamage(target, level, source, amount[, explicitStrike])` runs, in order: attacker multipliers (combo step, finisher, heavy, lunge, form, riposte, Reeling Pull bonus, Ataru aerial), the player guard (`SaberGuard.onMelee`) or the NPC guard (`NpcGuard.onHit`), `guardians_resolve`, then `Spar.onDamage`. A cancelled hit returns 0 and is also refused in `ALLOW_DAMAGE`. Also owns the heavy wind-up and the lunge state per player. |
| `saber.ComboTracker` | Three-step light combo per player (window `comboWindowTicks`, only swings at >= `comboMinStrength` count). Step 3 is the overhead finisher. |
| `saber.SaberForm`, `SaberForms` | `UNFORMED`, `SORESU`, `ATARU`; change requests (switch time, combat lock), the `FormChanges` handler for W3, and the form's stat modifiers. |
| `saber.SaberPassives` | Reads the skill nodes (`steady_guard`, riposte, deflection control, form nodes) into one stats record used by guard, HUD and combat. Publishes `NodeInfo` source `saber`. |
| `saber.SaberTechniques` | Heavy attack (wind-up, arc sweep up to `heavyMaxTargets`) and lunge (validation, dash, hit). |
| `saber.ParryReactive`, `SaberInteractions`, `SaberForceReactions` | Parry and riposte bookkeeping; hooks W4 calls (lightning blocked, leap marks, Reeling Pull bonus, landing recovery); Force reactions on a saber wielder (Push on a guarding NPC: knockback x0.5, 20 stamina drain). |
| `saber.duel.NpcGuard`, `GuardState`, `DuelState` | NPC guard and parry (9.5). Stamina, guard arc `npcGuardArc`, block x`npcBlockDamageMult`, drain `6 + 3 x damage` (x3 heavy, x2 finisher), break for `npcBrokenTicks` with x`npcBrokenDamageMult` damage taken. Parry only against light hits at >= 70% stamina while `READY` or `GUARDING`. |
| `saber.duel.SaberDuelist` | Interface every saber NPC implements (Sith Acolyte, W2's mentors): duel state, synced guard state for the client pose, profile id. |
| `saber.duel.DuelistCombatGoal`, `DuelistProfile(s)` | Shared duel AI: approach, string of 2-3 swings with a wind-up tell (`npc_windup`), guard when the target winds up a heavy, back off, Force move hook. Honours W4's `ForceStatus.isDisarmed` (no swings, tries a Force move or backs off) and `isGuardSuppressed` (no guard). Profiles: `acolyte`, `rival`, `jedi_master`, `training`. |
| `saber.duel.Spar`, `SparRules`, `SparResult` | Sparring: no death risk on either side, goals `YIELD`, `PARRIES n`, `TECHNIQUE_HITS n`. The partner never takes the player below 20%; the partner yields at its profile's fraction. |
| `saber.throwing.SaberThrow`, `ThrownSaber` | Saber Throw ability (8.3) and its entity. |
| `client.saber.SaberActionsClient` | Input: Sneak + Attack sends `HEAVY`, Sprint + Attack with a lunge target sends `LUNGE`, the form key sends `FORM_CYCLE`, attacks are swallowed while the saber is thrown. Registers the Saber Throw client precheck. |
| `client.saber.SaberAnimations`, `SaberPose` | Timed saber-arm poses for any `LivingEntity` (finisher, heavy wind-up and strike, lunge, salute, throw, catch, NPC wind-up, guard, broken). |
| `client.saber.ThrownSaberFx` | Draws the spinning blade with ghost copies and plays the Doppler-shifted `saber.throw_loop`. |
| `mixin.saber.LivingEntityRendererSaberMixin`, `HumanoidModelSaberMixin` (client) | Copy the pose into the render state and apply it to the saber arm after vanilla `setupAnim`. W4's mixin poses the free arm. |

## 12. Saber stamina, combo, heavy attack, lunge (9.1 to 9.3)

- The M1 guard stamina is now the shared saber stamina (`oldrepublic:saber_guard`, still not persistent). Guarding,
  heavy attacks and lunges all spend it; `steady_guard` adds +10% max per rank.
- **Combo:** each full-strength light hit inside the window advances the step; a weak swing or a timeout resets it.
  The third step deals x`finisherDamageMult`, extra knockback and x2 guard drain, and sends `saber_combo` (the finisher pose and
  `saber.finisher`).
- **Heavy:** `SaberActionPayload(HEAVY)`. Needs 15 stamina. 10-tick wind-up (movement x0.5, `saber_heavy` variant 0, the
  blade drawn back over the shoulder); a hit of >= 4 damage cancels it. The strike sweeps a 90 degree arc with +0.5
  reach and hits up to 3 targets at x1.75. Heavy hits cannot be parried and drain guards x3.
- **Lunge:** `SaberActionPayload(LUNGE, targetId)`. Needs `saber_lunge`, distance 3.5 to 7 (Ataru 9), line of sight, 20
  stamina (Ataru 12), cooldown 60 (Ataru 40). The client picks the target in a 6 degree cone. The dash lasts up to 8
  ticks and hits within 2.5 blocks at x1.2. Never parried.
- Techniques report `QuestHooks.onSaberTechnique(player, "heavy"|"lunge"|"finisher"|"parry", target, hit)`.

## 13. Forms (9.4)

| | Unformed | Soresu | Ataru |
|---|---|---|---|
| Parry window | x1.0 | x1.5 | -2 ticks |
| Guard arc | 100 | 150 | 100 |
| Guard drain | x1.0 | x0.7 | x1.0 |
| Damage | x1.0 | x0.85 | x1.0 (x1.4 aerial) |
| Attack speed | - | - | +0.15 |
| Max stamina | x1.0 | x1.0 | x0.75 |

Soresu also regenerates stamina x1.2, aims deflected bolts within 15 degrees and adds 4 ticks to the heavy wind-up.
Ataru counts a hit as aerial when the player fell >= 1.5 blocks or W4's `ForceJump.isLeaping` is true, and makes Force
Jump cost x0.75. A change takes `formSwitchTicks` (salute pose and `saber_form`) and is refused for
`formCombatLockTicks` after combat.

## 14. NPC duelling and sparring (9.5, 9.6)

- `LivingEntityGuardMixin` routes hits on a `SaberDuelist` through `NpcGuard`, so the acolyte and the mentors use the
  same rules as each other.
- The client shows the NPC's synced `GUARDING` or `BROKEN` state as a pose; `npc_windup` shows a blade glint
  (`oldrepublic:blade_glint`) and plays `saber.npc_windup` on the first swing of a string.
- `Spar.start(player, partner, rules)` begins a spar (`spar` effect, variant begin/end). Damage from the partner is
  clamped so the player stays above 20%; damage to the partner past its yield fraction ends the spar as a win. Leaving
  the area, logging out, dying to something else or the partner disappearing ends it as interrupted.

## 15. Saber Throw (8.3)

- Ability id `saber_throw`, 20 FE, cooldown 160, needs an ignited saber in the main hand.
- **No duplication:** the saber item never leaves the inventory. Throwing stamps the `oldrepublic:saber_thrown` component
  (the thrown entity's UUID) on the stack; the item model shows `minecraft:empty` while it is set and the item cannot
  attack, guard or be retracted. A `ThrownSaber` entity flies out (speed 1.3, range 14, up to 3 targets at x0.8, damage
  type `oldrepublic:thrown_saber`) and returns (speed 1.5). The component is cleared on catch, timeout (60 ticks),
  owner death, logout, level change, server stop, when an `ItemEntity` carrying it loads, and by an orphan sweep after
  `orphanClearTicks` without a live entity.
- Branches (W3 node ids): ricochet (2 bounces within 6, -20% each), rending (range 12, speed 1.0, x1.4, stagger 15), mastery
  (+5% range per rank). A guarding saber wielder blocks the saber (drain 15 player, 20 NPC) and it comes straight back.
- Effects `saber_throw` (start, scorch), `saber_throw_catch`; sounds `saber.throw`, `saber.throw_loop`,
  `saber.throw_catch`.

## 16. Audio and trails for any `LivingEntity`

`SaberAudio` now scans living entities within 32 blocks holding a usable lit saber (hum in the `HOSTILE` source for
non-players) and feeds `SwingTrailRenderer`, which styles trails by combo step (`LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`,
`VERTICAL`, `HEAVY`) and uses the entity's main arm. Trails are third person only.

## 17. Commands

`/oldrepublic saber stamina refill|set <n>`, `form <unformed|soresu|ataru>` (grants the node, then requests the change),
`combo`, `recall`, `throw`, `lunge` (nearest valid target), `spar [goal] [count]` (with the nearest duelist).

## 18. Tests

`SaberGameTests`: combo timing and reset, heavy maths and no parry, lunge range and line of sight, form modifiers, NPC
guard block/drain/break, NPC parry only on light hits above 70%, thrown saber never duplicates, spar never kills.
Client screenshots `oldrepublic_m2_50` to `59` in `gametest.m2.SaberClientSteps`.
