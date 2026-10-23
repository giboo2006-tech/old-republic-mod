# Gear feature design: blasters, vibroweapons, M4 armour, mods, jet pack (Milestone 4)

Owner: W4 (gear). Packages `com.oldrepublic.equipment` (common: `blaster`, `melee`, `mod`, `jet` and the
armour classes) and `com.oldrepublic.client.equipment` (client), plus the `mixin/equipment` packages of both source
sets. Spec: `docs/MILESTONE_4.md` sections 5.1 to 5.4 and 5.8, 6 (all), 11.1 and 11.2 (the jump key, the HUD), 13.2
and 13.3 (`steam_vent`, `flame_jet`, the six gear effect events), 14 (`blasters`, `melee`, `armour_m4`, `mods`; the
14.2 weapon reference), 16 (ids, the `jet_burst` payload) and 17 (`GearGameTests`, screenshots 40 to 49). The M3 era
armour this builds on is described in `docs/design/equipment.md`; assets are in `docs/assets/gear.md`.

No feature was cut: the electrostaff (cut item 2), the jet pack (3) and spacer's gear (4) are all in.

API status legend: **[V]** verified against the 26.2 dumps (`api/m4_items_digest.md`, `api/m4_followup_digest.md`,
`api/m3_tests.txt` and the others) or the Fabric 26.2 sources; **[U]** unverified, marked `// API-CHECK:` in the code
and listed in section 14 with its probe line in `tools/api-probe/m4_w4.txt`.

## 1. Content and ids

| Kind | Id | Where |
|---|---|---|
| Weapons (new, 4) | `heavy_repeater`, `vibrosword`, `electrostaff`, `kaast_longshot` (named Masterwork rifle, fixed heavy barrel) | `GearItems` |
| Weapons (reused ids, 3) | `blaster_pistol`, `blaster_rifle`, `vibroblade`: registered in place by the lead's `enemy/EnemyItems` with W4's classes | `BlasterItem`, `VibroWeaponItem` |
| Armour (18) | `watch_battle_{helmet,chest,legs,boots}`, `sith_war_{...}`, `mandalorian_{...}`, `spacer_{cap,jacket,trousers,boots}`, `kaast_helmet`, `kaast_chest` | `EquipmentItems.M4_ARMOUR` |
| Armour sets (appended) | `WATCH_BATTLE`, `SITH_WAR`, `MANDALORIAN`, `SPACER` (ordinals 4 to 7) | `ArmourSet` |
| Armour materials (5) | Watch battle, Sith war, Mandalorian, Kaast (Mandalorian stats, own asset), spacer | `EquipmentMaterials` |
| Equipment assets (5) | `oldrepublic:{watch_battle,sith_war,mandalorian,kaast,spacer}` | `assets/oldrepublic/equipment/*.json` |
| Mod items | the lead's `M4Items.MODS` (11 mods) and `M4Items.JET_PACK`; W4 gives them their effects, slots, models and icons | `mod/Mods`, `mod/ModEffects` |
| Particles (2) | `oldrepublic:steam_vent`, `oldrepublic:flame_jet` | `EquipmentParticles` |
| Effect events (6) | `player_blaster_fire`, `blaster_heat`, `repeater_spin`, `vibro_hit`, `electrostaff`, `jet_pack` | `GearEffects` |
| Payload (C2S) | `oldrepublic:jet_burst` (no fields) | `jet/JetBurstPayload` |
| Attachments (synced to the owner only, not persistent) | `oldrepublic:vibro_guard` (`GuardView`), `oldrepublic:jet_pack` (`JetView`) | `MeleeCombat.GUARD`, `JetPack.JET` |
| Attribute modifier ids | `oldrepublic:melee.reach`, `oldrepublic:armour.mod.plating`, `oldrepublic:armour.set.terrain` | `MeleeCombat`, `ArmourAttributes` |
| Item tags | `oldrepublic:repairs/{watch_battle,sith_war,mandalorian,spacer}` | `data/oldrepublic/tags/item/repairs/` |
| Balance sections (4) | `blasters`, `melee`, `armour_m4`, `mods` | `BlasterBalance`, `MeleeBalance`, `EquipmentM4Balance` (+ `.ModNumbers`) |
| Commands | `/oldrepublic armour give <player> <set> [rank3]` (now any of the 8 sets), `/oldrepublic gear fit|quality|heat` | `EquipmentCommands` |
| Lang | `src/main/lang-parts/equipment.json` (M3 keys plus the M4 items, sets, bonuses, mods, tooltips, commands) | |

Frozen and append-only: `ArmourSet` (ordinal is the `armour_set` variant and the `Worn` index; id saved in the
component), `ModSlot` (lead file), every effect variant number below.

## 2. Classes

Common (`src/main/java/com/oldrepublic/equipment/`):

- `blaster/BlasterBalance`: the `blasters` section; arrays hold one value per `BlasterItem.Kind` (pistol, rifle,
  repeater; Milestone 7 appended the scatter blaster and the carbine, `docs/BALANCE.md`). `of(values, kind)` reads
  short hand-edited arrays safely.
- `blaster/BlasterRules`: pure arithmetic: heat after a shot, linear bleed during a lock, cooling, overheat,
  `ventResult` and `vent`, spread, `drawDelay`, `chargeLoaded`, `spunUp`, `suppressed`, `boltDamage`.
- `blaster/BlasterItem`: the item class (`Kind` PISTOL, RIFLE, REPEATER, and since Milestone 7 SCATTER and CARBINE);
  use fires from the main hand (`use`, `onUseTick`, `releaseUsing`), use duration 72000, `ItemUseAnimation.NONE`; the
  bash; components (durability, `use_effects` for the movement while firing, `weapon` for one durability per bash);
  returns false from `allowComponentsUpdateAnimation` so heat changes do not bob the hand.
- `blaster/Blasters`: the server side (section 3): per-player `Hand` state while a blaster is in the main hand, the
  fired `Shot`s (weak map), suppression counts; test hooks `tryFire`, `vent`, `action`, `chargeSince`, `tickFor`,
  `drawnLongAgo`, `spinStart`, `loaded`, `forget`.
- `blaster/PlayerBolts`: `passesThrough(shooter, target)` and `isPlayerBlaster(source)`, the two rules the lead's
  `BlasterBolt` and the Force refund code ask for (section 14, requests a and b).
- `blaster/BlasterHeat`: Phase 0 component record, untouched.
- `melee/MeleeBalance`, `melee/MeleeRules` (pure: flurry chain, guard share and drain, cleave damage, staff charge and
  window, droid shock), `melee/VibroWeaponItem` (`Kind` BLADE, SWORD, STAFF, and since Milestone 7 AXE and PIKE),
  `melee/ElectrostaffItem`, `melee/MeleeCombat` (section 4).
- `mod/Mods` (slots, `slotOf`, `fits`, `fit`, `remove`, `fittedQuality`, `has`), `mod/ModEffects` (each mod as a
  multiplier, section 7). `mod/ModSlot` and `mod/FittedMods` are the lead's.
- `jet/JetBurstPayload`, `jet/JetPack` (section 8).
- `EquipmentM4Balance` (`armour_m4`) with the nested `ModNumbers` (`mods`).
- `GearItems`, `GearEffects`, `EquipmentParticles`.
- M3 classes extended: `ArmourSet` (appends), `SetBonusRules` (`Worn` per ordinal; the M4 rules at the end),
  `EquipmentTraits` (`Extra`), `EquipmentMaterials`, `ArmourAttributes` (quality-scaled build; the plating and
  terrain modifier ids), `EquipmentItems`, `ArmourItem` (M4 tooltip lines), `ArmourSets` (M4 pieces, `m4Pieces`,
  `extraControl`), `SetBonuses` (Warden's window, Wrath, `forceSpeedDurationMultiplier`, blaster kills never feed
  Hunger), `EquipmentEvents` (plating and terrain modifiers), `EquipmentModifiers` (rewritten: M3 traits, M4 traits,
  mods, set bonuses, the vibro guard, fall damage, the attacker side), `EquipmentCommands`, `EquipmentFeature`
  (wiring).
- `mixin/equipment/LivingEntityAttackStrengthAccessor` (main): `attackStrengthTicker` get and set, so a flurry hit
  leaves the attack cooldown charged.

Client (`src/client/java/com/oldrepublic/client/equipment/`):

- `GearEffectsClient`: the six effect events (section 10), `sinceFire` for the poses, the reticle's markers.
- `GearParticleProviders`: `steam_vent` and `flame_jet` providers and `spawn(...)` for tinted or resized instances.
- `HeldSound`: a sound bound to what an entity holds; stops on death, removal, level change, item change, `cut()` or
  its tick cap.
- `BlasterHud` (reticle, hit and deflect markers, heat gauge, jet pips), `VibroGuardHud` (guard bar).
- `BlasterPose` (record), `BlasterPoses` (arm poses and first-person recoil).
- `JetPackInput` (jump in the air sends `jet_burst`).
- `ClothedPlateRenderer` (Watch tabard and Sith war cape over the plate), `RobeModel.PLATE_CLOTH` layer.
- `GearTooltips` (mod lines), and the M4 cases in `ArmourTooltips`, `SetBonusToast`, `EquipmentEffectsClient`.
- `EquipmentClient`: registers all of the above.

Client mixins (`src/client/java/com/oldrepublic/mixin/equipment/`, config `oldrepublic-equipment.mixins.json`):
`AvatarRendererBlasterMixin` (copies the pose into the render state), `HumanoidModelBlasterMixin` (applies it after
`setupAnim(HumanoidRenderState)` [V]), `ItemInHandRendererBlasterMixin` (the first-person kick around
`submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int)` [V]).

## 3. Blasters (5.1)

**Trigger.** Use fires from the main hand. The first bolt leaves on the tick of the press (`Blasters.pressTrigger`), so
a tap is one shot; holding fires again at the rate (`holdTrigger` from `onUseTick`). `tryFire` refuses while locked,
inside the draw delay, before the rate, before the repeater is spun up, and while a rifle charge is held.

**Bolts.** `BlasterBolt.create(level, player, eye − 0.1, damage, life, Style.PLAYER, heavy)`, shot along a cone sample
(`cone(player, degrees, random)`) at the kind's speed. Damage is `damage[kind] × Quality.weapon()`; the mod part (heavy
barrel, overcharged cell) is remembered in the `Shot` and applied by `EquipmentModifiers` on the attacker side, so it
goes through the `DamageModifiers` clamp. Each shot costs one durability.

**Aim.** `BlasterRules.spread(hip, steadied, fast, airborne, modMult, b)`: sneaking ×0.35, faster than walking (0.24
blocks per tick horizontal) ×2, airborne ×3, focused barrel ×(1 − 20/30/40%).

**Heat.** `oldrepublic:blaster_heat` stores the heat as of the last shot, the lock end and the free shots. Each shot adds
`heatPerShot × ModEffects.heat` (free shots add nothing and count down). After `coolDelay` (16) ticks without a shot the
heat falls by `cooling[kind]` per tick. Reaching 100 overheats: the lock is `overheatLock[kind]`, the heat bleeds
linearly to 0 over it, the trigger is let go (`stopUsingItem`) and the repeater's barrel stops.

**Vent (Weapon Action, default N).** Pistol and repeater: a press vents. Rifle: a press starts the charge; letting go
before `chargedTicks` is a tap and vents. Below `ventMin` (30) nothing happens. A vent sets heat 0 with
`ventLock[kind]`; inside the perfect band (70 to 90 inclusive) the lock is halved and `perfectVentFreeShots` (3) shots
add no heat. A vent stops the repeater's spin (its hiss covers the wind-down).

**Rifle charged shot.** Holding Weapon Action for `chargedTicks` (30) loads one heavy bolt (`CHARGE_READY`, shooter
only); the trigger is blocked while the key is down. The next shot is the heavy bolt: `chargedDamage × quality` (12 at
Standard), `heavy` true (only a parry turns it), `chargedHeat` (35) times the cell multiplier. Dropping the charge
(tap, swap, death, logout, level change) stops the whine (`CHARGE_START` with intensity 0).

**Repeater.** The first pull starts the barrel (`SPIN_UP`) and fires nothing; it fires from `spinUp` (12) ticks on.
The spin is kept `spinHoldTicks` (40) after the trigger is let go, then winds down audibly. Bolts pierce
`repeaterPierce` (1) target: on a hit that was not blocked, a fresh bolt continues on the same line beyond the target
with the same damage and half the life. **Suppression:** 4 hits on one mob within 40 ticks give Slowness II for
`ControlEffects.diminished(target, 30, SLOW)` ticks; players are never suppressed.

**Draw delay.** Drawing a stack starts a new `Hand`; the first shot waits one rate (the pistol's quick draw:
`rate − quickDrawTicks`, 4 ticks sooner).

**Bash.** Attacking with a blaster: 2 damage (a player-only `getAttackDamageBonus`, so troopers keep their numbers),
attack speed 1.0, one durability, and `ControlEffects.stagger(player, target, 6, 1)`.

**Movement while firing.** `use_effects`: the rifle −10%, the repeater −30% and no sprinting.

**Allies and refunds.** `PlayerBolts.passesThrough`: never the shooter; the shooter's side (`Allegiances.friendly`),
vanilla team alliance (`isAlliedTo`), the shooter's tamed pets, and mobs whose attitude to the shooter is `ALLIED`.
Players are otherwise left to the PvP rules. `isPlayerBlaster(source)` marks damage from a `PLAYER` style bolt; Hunger
of the Tomb ignores such kills here, and the lead's refund code must too (section 14, request b).

**Hit feedback.** The shooter alone gets `HIT` (intensity 2 on a kill) and `DEFLECTED` (once per bolt, from the tick
scan) for the reticle.

**State lifetime.** `Hand` is dropped when the main hand changes, on death, logout, respawn and level change, and at
server stop; heat stays on the stack, so swapping guns is a choice.

## 4. Vibroweapons and the electrostaff (5.2, 5.3)

| | Vibroblade | Vibrosword | Electrostaff |
|---|---|---|---|
| Damage / speed | 6 / 1.8 | 8.5 / 1.2 | 6.5 / 1.1 |
| Reach | vanilla | +0.5 | +1.0 |
| Guard (front melee taken) | −50% | −65% | −60% |
| Special | flurry | cleave | sweep, charge, blocks sabers |
| Durability | 500 | 700 | 600 |

Damage, speed, reach and durability are fixed at registration from `new MeleeBalance()` (the M3 rule for item
attributes). Reach is an `ENTITY_INTERACTION_RANGE` main-hand modifier (`melee.reach`) [U: whether 26.2's melee reach
reads it when the item has no `attack_range` component; see section 14].

**Guard.** Holding use raises it (use animation BLOCK). `MeleeCombat.guardMultiplier` runs once per hit from the
equipment damage modifier: only for a raised vibroweapon, a `PLAYER_ATTACK`, `MOB_ATTACK` or `MOB_ATTACK_NO_AGGRO`
source whose direct entity is the attacker, from inside the M2 guard arc (`SaberGuard.inFront`). It drains the M2
guard stamina (`saber` section: `guardStamina`, `meleeDrainBase`, `meleeDrainPerDamage`, regeneration and delay), and
an empty pool breaks the guard (stop using, item cooldown and Slowness II for `guardBreakTicks`). It never blocks a
bolt and never parries. A saber blow on a raised vibroblade or vibrosword notches it (`saberNotch`, 5 durability); the
electrostaff holds the blade with no durability lost (`STAFF_BLOCK_SABER`). A saber parry of a vibroweapon's blow
(`CombatEvents.PARRY`) notches the attacker's blade too.

**Flurry (vibroblade).** `MeleeRules.nextChain`: a hit within `flurryWindow` (30) of the last continues the chain; the
third is the flurry: `+flurryBonus` (3) through the item's damage bonus, and next tick the attack ticker is restored
(the accessor), so the cooldown is left charged. The chain is advanced from Fabric's `AttackEntityCallback`, which
records the swing strength before vanilla resets it.

**Cleave (vibrosword).** A swing with strength ≥ `cleaveMinStrength` (0.9) also hits up to `cleaveTargets` (2) other
living things within `cleaveRange` (2) in a 120° arc in front, nearest first, at `cleaveMultiplier` (60%) of the
swing's attack damage. Area hits skip allies, pets and shielded bystanders (`hittable`: `isAttackable`, not
`passesThrough`, not `Attitudes.shieldedFromArea`).

**Electrostaff.** A full-strength swing on the ground and not sprinting sweeps: enemies beside the struck one take
`staffSweepDamage` (1) and a 0.4 shove. **Charge:** a Weapon Action press starts it; it loads by itself after
`staffChargeTicks` (40) and lasts `staffChargeWindow` (100). The next hit shocks (`ControlEffects.SHOCK` rule: Slowness
I for `diminished(target, 20, SHOCK)` ticks and `ForceReactions.interruptWindup`) and arcs to the nearest other enemy
within `staffArcRange` (3) for `staffArcDamage` (2, indirect magic) with its own shock. Droids (`oldrepublic:droids`
entity tag, or an entity id containing `droid`) take ×1.5 from shocks: the struck droid through the damage bonus, the
arc target directly. The charge ends unused when the window runs out or the staff leaves the hand (`STAFF_SPENT`).

**mod_shock_edge.** Any vibroweapon hit that was not a staff shock rolls the mod's chance to shock for `shockTicks`.

**State lifetime.** Per-player `State` follows the weapon in hand; a changed main hand ends a charge; logout, respawn,
level change and server stop forget it; the guard attachment is removed when the guard is full and nothing is held.

## 5. Quality (5.4, 7.4)

Quality is W5's (`Crafting.applyQuality`, `Quality.weapon()/armour()/durability()`: 1.00/1.06/1.12,
1.00/1.15/1.30, 1.0/1.25/1.5). W4 reads it where it applies: bolt and charged-bolt damage are multiplied by
`weapon()` at fire time; melee attack damage is the stack's modifier (scaled by W5 at craft); M4 armour traits are
scaled per piece by `armour()` when read (`SetBonusRules.pieceRatio`: a helpful trait grows, a drawback stays, so a
Masterwork piece is never worse); defence, toughness and durability are scaled by W5 at craft. Kaast's helmet, chest and
Longshot are built at Masterwork at registration (`NAMED`, `QUALITY` components, epic rarity), so story hand-outs need
nothing more. `/oldrepublic gear quality` remakes a held piece at a quality and keeps its mods.

## 6. The M4 armour sets (6.1, 6.2)

**Extensions of M3 (6.1).** `ArmourSet` gains four values. `SetBonusRules.Worn` holds an `int[]` sized from the enum
with `count(set)`, `full(set)`, `plus(set)`, the four M3 accessors and the four-count constructor; equals and
hashCode are by content. `EquipmentTraits` gains `Extra(speedPct, controlPct, fallPct)` as an
`optionalFieldOf("extra", Extra.NONE)` (DFU omits a default on encode, so an M3 piece writes no `extra` key) and in the
stream codec; old stacks decode with `Extra.NONE`.

**Materials and items.**

| Set | Defence h/c/l/b | Toughness / knockback | Durability mult / enchantability | Equip sound | Per-piece traits | Speed (attribute) |
|---|---|---|---|---|---|---|
| Watch battle | 2/5/4/2 | 1 / 0 | 16 / 12 | trooper | cost −3%, melee taken −4% | — |
| Sith war | 3/6/5/2 | 1 / 0.05 | 17 / 10 | trooper | max energy +4, melee taken −5%, regen −3% | — |
| Mandalorian (and Kaast) | 3/7/6/3 | 2 / 0.1 | 22 / 8 | trooper | blaster taken −6%, cost +5% | −3% |
| Spacer | 1/4/3/1 | 0 / 0 | 9 / 16 | robe | regen +4%, fall taken −10% | +2% |

Traits multiply per piece (`SetBonusRules.totals`), like M3. Defence, toughness, durability and the speed attribute are
fixed at registration.

**Set bonuses (4 pieces).**

- **Warden's Resolve** (Watch battle; the light gate `jediGate`: alignment ≥ +`setAlignment` or a Balanced
  Independent): for `watchSetTicks` (80) after a parry (`CombatEvents.PARRY` → `SetBonuses.parried`), melee taken
  −15% and Force Barrier −20% cost.
- **Wrath Unbound** (Sith war; the dark gate `sithGate`): below half health, Choke and Lightning cost −20% and deal +10%
  (the attacker side of `EquipmentModifiers`, on `ForceDamage` sources).
- **Hunter's Tenacity** (Mandalorian, no gate): control durations on the wearer −30%, the jet pack one more charge,
  Force Speed +10% cost.
- **Light on Your Feet** (spacer, no gate): Force Speed lasts +30% (`SetBonuses.forceSpeedDurationMultiplier`, read by
  W6's `force_speed`; section 14, request c), fall taken a further −50%, and `MOVEMENT_EFFICIENCY` +1 (`armour.set.terrain`) so
  soul sand and ash do not slow.

All control multipliers on the wearer (Iron Discipline, Hunter's Tenacity, stabilisers, `Extra.controlPct`) are bounded
by `controlMin` (0.4) in `SetBonusRules.controlTotal`; cost, regeneration and damage by the registries' clamps.

**Toast and flourish.** The `armour_set` event and toast gain the four sets: Watch and Sith war follow the robe gates in
the toast's text; each set has its own tint and pitch.

**Rendering.** All plate pieces are vanilla equipment layers (64×32, `humanoid` and `humanoid_leggings`). The Watch
battle and Sith war chests are also drawn by `ClothedPlateRenderer`: the plate as vanilla would, then (with
`ClientConfig.robeModels`) the `PLATE_CLOTH` layer (a hood and tabard, or a torn cape) with
`textures/entity/robe/<set>.png`, posed like the robes. Kaast's pieces use the `kaast` asset (gold paint, red visor).

## 7. Mods (6.3)

Slots (`Mods.slotsOf`): every era armour piece (M3 and M4) `armour`; chests also `pack` (jet pack only); blasters
`barrel` and `cell` (Kaast's Longshot only `cell`, its heavy barrel is built in and counts at the rifle's quality);
vibroweapons and the electrostaff `edge`. Each mod fits one slot (`slotOf`). `fit` refuses a taken slot and shrinks the
mod stack by one; `remove` returns the mod and drops the component when empty. Strength is the mod stack's own quality
(Standard/Fine/Masterwork index into the `mods` arrays).

| Mod | Effect (Standard / Fine / Masterwork) | Applied by |
|---|---|---|
| `mod_plating` | +1 armour (transient modifier `armour.mod.plating`, per piece); blaster taken −2/−3/−4% | `EquipmentEvents`, `EquipmentModifiers` |
| `mod_weave` | melee taken −2/−3/−4% | `EquipmentModifiers` |
| `mod_attunement` | Force cost −2/−3/−4% | `ForceModifier` |
| `mod_conduit` | regeneration +3/+4.5/+6% | `ForceModifier` |
| `mod_stabiliser` | control durations −8/−12/−16% | `DurationModifier` (bounded by `controlMin`) |
| `jet_pack` | section 8 | `JetPack` |
| `mod_focused_barrel` | spread −20/−30/−40% | `Blasters` |
| `mod_heavy_barrel` | bolt damage +6/+9/+12%, +1 tick between shots | `Blasters`, attacker side |
| `mod_cooling_cell` | heat per shot −15/−22/−30% | `Blasters` |
| `mod_overcharged_cell` | bolt damage +4/+6/+8%, heat per shot +10% | `Blasters`, attacker side |
| `mod_keen_edge` | melee damage +5/+7.5/+10% | attacker side |
| `mod_shock_edge` | 8/12/16% chance to shock for 20 ticks | `MeleeCombat` |

Worn armour mods are summed over the four armour slots. Tooltips (`GearTooltips`) show a mod's slot and effect at its
quality, and on gear the fitted mods and the open slots.

## 8. The jet pack (6.4)

- **Input.** `JetPackInput`: a jump *press* (not a held key) while airborne with a pack worn sends `jet_burst`; the
  client filters only the obvious cases (ground, water, riding, flying, gliding, a screen open).
- **Server.** `JetPack.burst` refuses silently when dead or a spectator, without a pack, on the ground, in water,
  riding, flying, fall-flying, during a Force Jump leap (`ForceJump.isLeaping`; the leap's glide keeps the key), when
  held (`Holds.isHeld`), or within `jetBurstGapTicks` (4) of the last burst. With every charge spent it clicks empty
  (`JET_EMPTY`, at most every 10 ticks).
- **Burst.** Velocity `jetUp` (0.85) up plus `jetForward` (0.3) along the horizontal look, keeping half the old
  horizontal speed (`burstVelocity`); fall distance reset; `hurtMarked`. A velocity change, not flight.
- **Charges.** `jetCharges` (2), +`mandoSetJetCharges` (1) with Hunter's Tenacity. All refill together after
  `jetRefillTicks` (100) consecutive ground ticks.
- **Landing.** A burst within `jetLandWindow` (20) ticks before a landing makes that fall `jetFallPct` (−50%).
- **HUD.** `JetView(charges, max)` synced to the wearer (`AttachmentSyncPredicate.targetOnly()`), removed when the pack
  comes off; spent charges still refill on the ground before the state goes.
- **Floating kick.** The worst-case chained airtime is far under the server's 80-tick limit; `GearClientSteps.jetChain`
  checks it on a dedicated server when the lead calls it (section 14, request g).

## 9. Poses, reticle and HUD (5.8, 11.2)

- **Poses** (`BlasterPoses`, every player in view, driven by `player_blaster_fire`, nothing extra sent): pistol, the
  gun arm raised along the look while the trigger is held and 20 ticks after a shot; rifle and repeater, the vanilla
  two-handed hold while firing, 20 ticks after and while sneaking, off-hand item hidden in third person; a small kick on
  each shot; first person, a short view-model recoil.
- **Reticle** (`BlasterHud`, replaces `VanillaHudElements.CROSSHAIR` while a blaster is held in first person and
  `ClientConfig.blasterReticle` is on): four ticks whose gap is the eased current spread; a rifle charge draws them in;
  a loaded heavy bolt adds corner brackets; red-orange while locked.
- **Markers:** an X on a hit (larger, red-orange on a kill), an amber diamond on a deflect.
- **Heat gauge** (attached after the crosshair): a thin arc right of it, holo blue to amber, two notches at 70 and 90
  with the band glowing white while inside it, red-orange from 90, flashing during a lock (steady with flashing effects
  off), white dots for free shots; shown while held and 40 ticks after.
- **Jet pips** under the gauge while a pack is worn and the player is airborne or refilling.
- **Vibro guard bar** (`VibroGuardHud`): the saber guard bar's frame in steel, amber below 25%, red with hazard stripes
  while broken; no parry chevrons.
- The client never reads the server's balance file: spread, cooling and tooltip numbers use the defaults.

## 10. Effect events (13.3, 16.3)

Variants up to the ones 13.3 lists keep their numbers; the rest are W4's own (personal feedback and loop stops).

| Event | Variant | Sent | What the client does |
|---|---|---|---|
| `player_blaster_fire` | 0 `FIRE_PISTOL`, 1 `FIRE_RIFLE`, 2 `FIRE_REPEATER`, 3 `FIRE_CHARGED` | toTracking; pos muzzle, dir shot, entity shooter, intensity heat/100 | the crack (`blaster.pistol_fire`, `blaster.fire`, `blaster.repeater.fire`, `blaster.rifle.charged`), a green-white muzzle flash, ≤4 sparks; the pose kick; stops the charge whine |
| | 4 `HIT` (W4) | shooter only; pos target, intensity 2 on a kill | reticle hit marker |
| | 5 `DEFLECTED` (W4) | shooter only; once per bolt | reticle deflect marker |
| | 6 `CHARGE_READY` (W4) | shooter only | a latch click (`workbench.slot` for now; section 14, request f) and a steady muzzle glow |
| | 7 `CHARGE_START` (W4) | toTracking; intensity 1 start, 0 dropped | starts or cuts the `blaster.charge` whine (`HeldSound`) |
| `blaster_heat` | 0 `HEAT_OVERHEAT`, 1 `HEAT_VENT`, 2 `HEAT_PERFECT` | toTracking; pos muzzle, entity holder | overheat: `blaster.overheat`, 12 steam puffs over 20 ticks that follow the gun; vent: `blaster.vent` and a burst of steam; perfect: `blaster.vent_perfect`, one bright clean puff |
| `repeater_spin` | 0 `SPIN_UP`, 1 `SPIN_DOWN` | toTracking | the spin sounds; while spinning a faint ring of steel motes at the muzzle |
| `vibro_hit` | 0 `VIBRO_FLURRY`, 1 `VIBRO_CLEAVE` | around; pos impact, entity wielder (cleave: intensity = targets hit) | flurry whine and grit flick; cleave's arc of sparks and thud |
| | 2 `VIBRO_GUARD` (W4), 3 `VIBRO_NOTCH` (W4) | around; guard intensity = stamina left; notch intensity 0 = a guard break | a guard clang; orange notch sparks or the break |
| `electrostaff` | 0 `STAFF_CHARGE`, 1 `STAFF_SHOCK`, 2 `STAFF_BLOCK_SABER` | toTracking | the rising crackle (`HeldSound`); the shock's snap and arcs on the target; clash sparks |
| | 3 `STAFF_CHARGED`, 4 `STAFF_SPENT`, 5 `STAFF_SWEEP` (W4) | toTracking | arcs crawling at both emitters until the shock or `SPENT`; `SPENT` cuts the crackle; the sweep's dust |
| `jet_pack` | 0 `JET_BURST`, 1 `JET_EMPTY` | toTracking; pos the pack, dir the exhaust | 14 `flame_jet` and smoke over 10 ticks following the wearer, `jetpack.burst`; the empty click and a wisp |

Particle counts go through `Vfx`. Every loop and follower stops on its end event, when its entity dies or is removed,
the level changes or the item leaves the hand, with a hard tick cap as a watchdog. No ground circles, halos or glows
on the ground.

## 11. Balance (14.3)

All defaults are the spec's. Sections and their live/registration split:

- `blasters` (`BlasterBalance`): `damage`, `rate`, `spinUp`, `spinHoldTicks`, `speed`, `life`, `hipSpread`,
  `steadyMultiplier`, `moveMultiplier`, `airMultiplier`, `heatPerShot`, `cooling`, `coolDelay`, `overheatLock`,
  `ventLock`, `ventMin`, `perfectVentBand`, `perfectVentFreeShots`, `chargedTicks`, `chargedDamage`, `chargedHeat`,
  `repeaterPierce`, `suppressHits`, `suppressWindow`, `suppressTicks`, `bashStagger`, `quickDrawTicks` are live;
  `durability`, `rifleSlowPct`, `repeaterSlowPct`, `bashDamage` are registration defaults.
- `melee` (`MeleeBalance`): damage, speed, reach and durability per weapon are registration defaults; flurry, cleave,
  guard shares, `saberNotch`, the staff's charge, window, shock, arc, sweep and `droidShockMultiplier` are live. The
  guard pool is the `saber` section's.
- `armour_m4` (`EquipmentM4Balance`): per-piece traits, set-bonus numbers, `controlMin`; speed per piece is a
  registration default.
- `mods` (`EquipmentM4Balance.ModNumbers`): per-quality arrays for each mod, `heavyRateTicks`, `overchargedHeatPct`,
  `shockTicks`, and the jet pack (`jetCharges`, `jetRefillTicks`, `jetUp`, `jetForward`, `jetFallPct`,
  `jetLandWindow`, `jetBurstGapTicks`).

## 12. Weapon reference (14.2)

Simulated with the default balance and the 14.2 model (perfect vent each time heat reaches the band, half lock, three
free shots, no cooling while firing, next shot at the later of rate and lock, the repeater's spin-up after each vent):

| Weapon | Burst DPS | Sustained, perfect vents | Sustained, plain vents | Spec |
|---|---|---|---|---|
| Blaster pistol | 10.0 | 9.56 | 7.56 | 10.0 / ≈9.7 |
| Blaster rifle | 10.0 | 9.65 | 7.58 | 10.0 / ≈9.8 |
| Heavy repeater | 16.67 | 10.16 | 7.15 | 16.7 / ≈10.3 |
| Vibroblade | 10.8 | ≈12.6 with flurry on one target | | 10.8 / ≈12 |
| Vibrosword | 10.2 | +60% to up to two more on a full swing | | 10.2 / ≈10 |
| Electrostaff | 7.15 | + sweep 1, shock and arc every charge | | 7.2 / ≈8 |

The blaster figures are within 0.15 of the spec's (the simulation counts whole ticks). A blaster stays behind an
Unformed saber (11.2) over a fight, as intended.

## 13. Commands (testing)

- `/oldrepublic armour give <player> <set> [rank3]`: any of the eight sets (`watch_battle`, `sith_war`,
  `mandalorian`, `spacer` added).
- `/oldrepublic gear fit <player> <mainhand|head|chest|legs|feet> <mod> [standard|fine|masterwork]`: fits a new mod,
  returning the one it replaces.
- `/oldrepublic gear quality <player> <quality>`: remakes the weapon or armour piece in the main hand at that
  quality (as a craft would), keeping its mods.
- `/oldrepublic gear heat <player> <0..100>`: sets the held blaster's heat (no lock).

## 14. Tests, API status and open points

**Server game tests** (`GearGameTests`, 19 methods, seeded randoms, `M3TestPlayers.listed`/`remove`, maxTicks ≤ 300):
`heatRisesLocksAndCools`, `ventBelow30DoesNothing`, `perfectVentHalvesLockAndFreeShots`, `chargedShotIsHeavy`,
`boltStylePlayerAndAllyPass`, `steadySpreadAndMoveMultipliers`, `repeaterSpinUpAndSuppression`,
`vibroGuardReducesFrontMeleeOnly`, `flurryEveryThirdHit`, `cleaveHitsTwoMore`,
`electrostaffBlocksSaberWithoutDurability`, `qualityMultipliesDamage`, `setBonusWornCountsEveryOrdinal`,
`newSetsTraitsAndBonuses`, `extraTraitsDecodeAbsent`, `modSlotsPerGear`, `modsRespectClamps`,
`jetChargesRefillOnGround`, `jetDefersToForceJumpGlide`. Pure rules are driven with explicit ticks; in-world parts use
the public hooks listed in sections 2 to 8. `EquipmentGameTests` (M3) skips the M4 sets in its recipe loop.

**Client steps** (`m4/GearClientSteps`, run by `M4ClientTest`): screenshots `oldrepublic_m4_40_pistol_first_person`,
`41_pistol_third_person`, `42/43` rifle, `44/45` repeater, `46_perfect_vent`, `47_armour_sets`,
`48_jet_pack_burst`, `49_electrostaff_charged`; each asserts on the server that it did what the picture claims. Public
`jetChain(context, server)` chains all three jet charges and asserts the player stays connected, for the lead's
dedicated-server leg.

**Requests to other owners** (made in the W4 report; until they land, the parts named stay inactive):

- a. Lead, `enemy/BlasterBolt#canHitEntity`: a `PLAYER` style bolt passes through `PlayerBolts.passesThrough(shooter,
  target)`.
- b. Lead (skill refunds and Force kill hooks): skip damage for which `PlayerBolts.isPlayerBlaster(source)` holds.
- c. W6, `force_speed`: multiply the duration by `SetBonuses.forceSpeedDurationMultiplier(player)`.
- d. Lead: the entity tag `data/oldrepublic/tags/entity_type/droids.json` (the id fallback covers W6's droids
  meanwhile).
- e. None needed now: the `gear quality` feedback names W5's `quality.oldrepublic.*` keys, already in `crafting.json`.
- f. Lead (sounds): optionally a dedicated `blaster.charge_ready` latch sound.
- g. Lead: call `GearClientSteps.jetChain(context, server)` in the dedicated-server leg of the travel client test.
- h. Lead: register `com.oldrepublic.gametest.GearGameTests` in the game-test entrypoints.

**[U] API-CHECKs** (probe lines in `tools/api-probe/m4_w4.txt`):

- `VibroWeaponItem`: whether 26.2's melee reach reads `ENTITY_INTERACTION_RANGE` for an item without an
  `attack_range` component (the spec's own `DataComponents` field name is unverified, so the attribute is used).
- `PlayerBolts`: `OwnableEntity#getOwner()` as a default method returning the loaded owner.
- `mod/FittedMods` (lead file): `ByteBufCodecs#fromCodecWithRegistries`.

**Open points and known issues.**

- `EquipmentModifiers` keeps the M3 rule that damage whose direct entity is a living attacker counts as melee, so melee
  resistance (traits, weave, Warden's Resolve) also reduces Force damage whose source names the caster as its direct
  entity. The vibro guard itself is limited to real melee damage types.
- The guard drains stamina on hits that land inside the target's invulnerability frames (the drain is computed before
  vanilla discards the hit).
- A raised guard plus heavy resistances can reach the shared damage clamp (0.25); the clamp is what stops them.
- The rifle and repeater hide the off-hand item in third person only; the off-hand still shows and stays usable in first
  person.
- The vibro guard bar and the saber guard bar share a spot under the crosshair (in third person beside the head, on the side away from the sword arm, since October 2026: `Ui.crosshairBar`); they never show together for long.
- `electrostaff.charge` is a 2-second rise, not a loop; the visual arcs carry the loaded state.
- The flurry test and the client steps depend on runtime details that cannot be checked here (the mock player's attack
  path, key timing in the client harness).
- The HUD and tooltips use default balance numbers (the balance file is server-only).
