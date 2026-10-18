# Gear assets: blasters, vibroweapons, M4 armour, mods, jet pack (Milestone 4)

Everything below is original art and sound made by our own scripts; nothing is copied from or imitates a Star Wars game
or film costume, prop or sound. The Mandalorian helmet is our own T-visor shape (a rounded dome, a flat T slot, a
range-finder stub), not a copy of any film helmet. Textures are **placeholders** from
`tools/assets/gen_textures_equipment.py --m4` (PIL, deterministic, seeded per texture; run from the repository root; the
M4 section sits after the M3 one and reuses its helpers). The JSON files are written by the same script and are final
unless noted. Sounds are designed stand-ins from `tools/assets/sounds/gear.py`, rendered by
`python3 tools/assets/gen_sounds.py --m4 --feature gear`; their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (the lead's files) do not change. The design is in `docs/design/gear.md`.

Palette (worn, muted, readable at 16 px next to vanilla gear):

- **Watch battle armour:** Watch green plate with lighter edges, bone-white trim and collar, brass buckles, a dark
  leather undersuit; the tabard and hood in oat cloth with a green edge and a green leaf device.
- **Sith war armour:** near-black plate with bronze edging, crimson accents and belt, a horned half-helm; the cape red
  outside with a black lining, darkening toward a torn hem.
- **Mandalorian armour:** blue-grey hunter's plate with worn rust-orange paint, a black T-visor, a red range-finder
  light, an olive-brown kama.
- **Kaast's pieces:** the Mandalorian cut in dark bronze-grey with gold paint and a deep red visor, a red kama.
- **Spacer's gear:** a tan padded jacket over an oat shirt, olive trousers, belt pouches, a brown flight cap with
  blue-glass goggles, brown buckled boots.
- **Weapons:** gunmetal and steel with brown grips; the repeater's drum cell red-orange, the Longshot's fittings gold;
  vibro edges pale steel; the electrostaff black haft with blue-white emitters.
- **Mods:** each reads by its slot: armour mods as plates, mesh and lenses, barrels as tubes, cells as canisters (blue
  cooling, red-orange overcharged), edges as blades (the shock edge with blue sparks).

## Textures

| Path (under `src/main/resources/assets/oldrepublic/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/entity/equipment/humanoid/watch_battle.png`, `humanoid_leggings/watch_battle.png` | Watch battle plate layers, 64x32, vanilla layout | placeholder generated | A face-open helm with a high bone collar; chest plates in Watch green over a leather undersuit; leaf-cut pauldrons; plated legs and boots. Plate edges lit top-left and shadowed bottom-right so they read thick. Must feel like the Jedi's compromise: plated where a blade lands, open enough to cast. |
| `textures/entity/equipment/humanoid/sith_war.png`, `humanoid_leggings/sith_war.png` | Sith war plate layers, 64x32 | placeholder generated | A horned half-helm, black plate with bronze edging, crimson piping and belt, angular pauldrons. Heavier and more aggressive than the M3 Sith trooper. |
| `textures/entity/equipment/humanoid/mandalorian.png`, `humanoid_leggings/mandalorian.png` | Mandalorian plate layers, 64x32 | placeholder generated | Our own T-visor helmet (dome, flat T slot, a range-finder stub on one side with a red light), chest plates with worn rust paint and chips, segmented abdomen, gauntlets, a short kama at the belt. Hunter's kit: scuffed, practical, not shiny. |
| `textures/entity/equipment/humanoid/kaast.png` | Kaast's helmet and chest, 64x32 (no leggings layer: he gives only two pieces) | placeholder generated | The Mandalorian cut, unmistakably his: dark bronze-grey plate, gold paint in a single clan stripe, a deep red visor, a red kama. |
| `textures/entity/equipment/humanoid/spacer.png`, `humanoid_leggings/spacer.png` | Spacer's gear layers, 64x32 | placeholder generated | A flight cap with goggles pushed up, a padded tan jacket with quilting and a high collar over an oat shirt, belt pouches, olive trousers, buckled brown boots. Light, travelled, clearly not armour. |
| `textures/entity/robe/watch_battle.png` | The Watch tabard cloth over the plate, 64x64, `RobeModel.PLATE_CLOTH` layout (`PLATE_CLOTH_UV`: back panel 0,0 9x20x1; front tabard 24,0 6x10x1; lowered hood 0,24 8x3x2) | placeholder generated | Oat cloth with a Watch green edge, a leaf device on the front, a short ragged hem; the lowered hood lies on the shoulders. Keep the layout or update `RobeModel.createPlateCloth` and `PLATE_CLOTH_UV` together. |
| `textures/entity/robe/sith_war.png` | The Sith war cape, 64x64, same layout (no hood) | placeholder generated | A long cape, red outside and black inside, bronze at the shoulder line, darker and torn toward the hem (transparent notches); a short front fall below the collar. |
| `textures/item/watch_battle_{helmet,chest,legs,boots}.png` | Icons, 16x16 | placeholder generated | Green plate with bone trim: the open helm (a dark face opening under a bone brow), a cuirass, plated legs, boots. |
| `textures/item/sith_war_{helmet,chest,legs,boots}.png` | Icons, 16x16 | placeholder generated | Black plate with bronze: the horned half-helm with a red gem, a cuirass with a red belt, legs with a red waist, boots. |
| `textures/item/mandalorian_{helmet,chest,legs,boots}.png` | Icons, 16x16 | placeholder generated | Blue-grey plate with rust paint: the T-visor helmet with a red light, a cuirass, legs, boots. |
| `textures/item/spacer_{cap,jacket,trousers,boots}.png` | Icons, 16x16 | placeholder generated | The flight cap with blue goggles, the open tan jacket over a shirt, olive trousers, buckled brown boots. |
| `textures/item/kaast_{helmet,chest}.png` | Icons, 16x16 | placeholder generated | Kaast's helmet (red visor, gold marks) and chest (gold paint). Must read as "his" at a glance next to the plain Mandalorian icons. |
| `textures/item/heavy_repeater.png`, `kaast_longshot.png` | Inventory icons, 16x16 | placeholder generated | The repeater: a bulky gun with a triple barrel, a carry handle and a red drum cell. The Longshot: a long rifle with a scope and gold fittings. |
| `textures/item/heavy_repeater_model.png`, `kaast_longshot_model.png` | Swatch sheets for the 3D in-hand models, 16x16 (4x4 material swatches) | placeholder generated | Body metal, light metal, grip, accent or cell, barrel, glass, dark grip, muzzle. A final artist may repaint the swatches or UV the model properly. |
| `textures/item/vibrosword.png`, `electrostaff.png` | Icons and the handheld texture, 16x16 | placeholder generated | The vibrosword: a long straight pale blade with a brass guard. The electrostaff: a black haft with blue-white emitter heads at both ends. |
| `textures/item/mod_{plating,weave,attunement,conduit,stabiliser}.png` | Armour mod icons, 16x16 | placeholder generated | Plating: a riveted steel plate. Weave: a dark mesh square. Attunement: a blue lens in a gold ring. Conduit: a gold-mesh cross of conductors. Stabiliser: concentric steel rings round a bright core. |
| `textures/item/jet_pack.png` | The jet pack icon, 16x16 | placeholder generated | Twin grey canisters on a harness with flame-orange nozzles. |
| `textures/item/mod_{focused_barrel,heavy_barrel,cooling_cell,overcharged_cell}.png` | Blaster mod icons, 16x16 | placeholder generated | Focused barrel: a slim tube with brass collars and a blue lens. Heavy barrel: a thick ribbed tube. Cooling cell: a canister with blue frost bands. Overcharged cell: a red canister with a lightning mark. |
| `textures/item/mod_{keen_edge,shock_edge}.png` | Edge mod icons, 16x16 | placeholder generated | Keen edge: a honed blade insert. Shock edge: the same with blue sparks. |
| `textures/particle/steam_vent_{0..3}.png` | `steam_vent` frames, 8x8, white (tinted in code) | placeholder generated | A soft lumpy puff that swells and thins over four frames. White so the code can tint it (warm grey for an overheat, clean white for a perfect vent). |
| `textures/particle/flame_jet_{0..3}.png` | `flame_jet` frames, 8x8 | placeholder generated | A short hot tongue of flame (white core, yellow-orange edge) thinning into a ragged wisp; the code darkens late frames toward smoke. Also used by W6's hunter flamer and jet hop. |

The HUD (reticle, heat arc, markers, jet pips, the vibro guard bar) is drawn with fills by `BlasterHud` and
`VibroGuardHud` through the shared `Ui` helpers, so `textures/gui/sprites/gear/` is empty; no sprite is needed.

## JSON

| Path | What it is | Status |
|---|---|---|
| `equipment/{watch_battle,sith_war,mandalorian,spacer}.json` | Equipment assets: `humanoid` and `humanoid_leggings` layers, texture `oldrepublic:<asset>` | final |
| `equipment/kaast.json` | Kaast's equipment asset: the `humanoid` layer only | final |
| `items/<18 armour ids>.json`, `models/item/<18 armour ids>.json` | Client item definitions and flat `item/generated` models | final |
| `items/{heavy_repeater,kaast_longshot}.json` | `minecraft:select` on `display_context`: the flat icon in the GUI, on the ground, in frames and on shelves; the 3D model in hand | final |
| `models/item/{heavy_repeater,kaast_longshot}.json`, `{...}_in_hand.json` | The flat icon model, and the 3D in-hand model (cuboids on the swatch sheet; the M1 rifle's display transforms so all long guns sit alike) | placeholder (geometry is ours, final artist may refine) |
| `items/{vibrosword,electrostaff}.json`, `models/item/{vibrosword,electrostaff}.json` | `item/handheld` models | final |
| `items/<11 mod ids and jet_pack>.json`, `models/item/<...>.json` | Flat `item/generated` models | final |
| `particles/steam_vent.json`, `particles/flame_jet.json` | Four sprite frames each | final |
| `data/oldrepublic/tags/item/repairs/{watch_battle,sith_war,mandalorian,spacer}.json` | Repair tags (alloy plate for the plate sets; leather for the spacer's gear) | final |

The plate pieces are drawn by vanilla from the equipment assets. The Watch battle and Sith war chests are also drawn by
`ClothedPlateRenderer`: the plate exactly as vanilla, then the cloth layer (`RobeModel.PLATE_CLOTH`,
`textures/entity/robe/<set>.png`) when `ClientConfig.robeModels` is on.

## Sounds

Placeholders synthesised by `tools/assets/sounds/gear.py` (mono, 44.1 kHz Ogg Vorbis). The player's pistol and rifle
reuse `blaster.pistol_fire` and `blaster.fire` (M2), the rifle's charge whine reuses `blaster.charge`, the charged-shot
"ready" click reuses `workbench.slot`, and the vibro hits and swings reuse the M2 `vibroblade.*` and saber sounds.

| Event | File | Placeholder now | Final sound brief |
|---|---|---|---|
| `blaster.repeater.fire` | `blaster/repeater_fire{1,2}` | A short, dry, low chirp, lighter than the rifle's (0.4 s; two pitches) | One bolt of a heavy rotary blaster: short, punchy and low, with a mechanical tick of the barrel cluster. A stream at 6–7 shots per second must read as a rattle, not a row of single shots; no long tail. |
| `blaster.rifle.charged` | `blaster/rifle_charged` | A deeper, longer chirp with a hard crack and a tail (1.3 s) | The heavy bolt: the rifle's report an octave down with a hard transient crack and a rolling tail. Clearly bigger than a normal shot, still an energy weapon, not a firearm. |
| `blaster.overheat` | `blaster/overheat` | A sharp double warning beep, a metallic clank and a long scream of steam that falls away (2.3 s) | The gun choking: a short two-tone warning, a latch or heat-sink clank, then a pressurised steam scream that decays over two seconds. Alarming but not shrill. |
| `blaster.vent` | `blaster/vent` | A short hiss of steam with a latch clack (0.8 s) | A deliberate manual vent: a latch thrown, a quick burst of steam. Satisfying, short. |
| `blaster.vent_perfect` | `blaster/vent_perfect` | A clean, bright hiss and a small rising chime (1.45 s) | The same vent done right: cleaner and brighter, ending in a small rising two-note chime that says "good timing" without sounding magical. |
| `blaster.repeater.spin_up` | `blaster/repeater_spin_up` | The barrel cluster whirring up to speed (1.1 s) | An electric motor and bearings spinning up over about half a second, reaching a steady whir. Must be audible from a few blocks as a warning. |
| `blaster.repeater.spin_down` | `blaster/repeater_spin_down` | The barrel coasting down with a few ratchet clicks (1.0 s) | The motor winding down, the barrels coasting to a stop with a few soft ratchet clicks. |
| `vibro.flurry` | `vibro/flurry` | A quick double whip of air over a high buzzing edge (0.6 s) | Two fast cuts in a row over the vibroblade's high ultrasonic buzz. Light, quick, sharp. |
| `vibro.cleave` | `vibro/cleave` | A broad heavy sweep with a low buzz and a meaty thud (0.9 s) | A wide two-handed sweep: a heavy whoosh, the sword's lower buzz, a thud as it carries through more than one body. |
| `electrostaff.charge` | `electrostaff/charge` | 2.45 s of rising electric crackle and hum (not a loop) | Emitters charging: a rising hum with crackles growing denser. Not a loop; the visual arcs carry the loaded state afterwards. A final version could add a quiet crackle loop while loaded. |
| `electrostaff.shock` | `electrostaff/shock` | A snapping discharge with a buzzing tail (0.9 s) | The charged hit: a hard electric snap and a short buzzing tail. |
| `electrostaff.clash` | `electrostaff/clash` | A blade held on the emitters: a contact snap, a sizzle and a ring (1.5 s) | A saber stopped by the staff's emitters: a sharp contact, an energy sizzle and a short metallic ring. Distinct from a saber clash. |
| `jetpack.burst` | `jetpack/burst` | A short roar of thrust: an ignition pop, a rushing body, a fading rumble (1.4 s) | A short rocket burst: a hard ignition pop, a roaring rush of flame, a rumble that fades as the wearer rises. Loud and physical. |
| `jetpack.empty` | `jetpack/empty` | A dry click and a little sputter (0.45 s) | An igniter clicking with no fuel: a dry click and a weak sputter. |

## VFX (phase by phase)

All counts go through `Vfx` (13.1 budgets at Standard). No ground circles, halos or glows on the ground. Sparks use the
enemy feature's `bolt_impact` and `flare_ember` sprites, arcs the Force feature's `lightning_spark`, the saber clash the
saber feature's `contact_flash` and `saber_spark`, grit the world feature's `vent_grit`.

- **Blaster shot (`player_blaster_fire` 0 to 3).** Activation: a green-white muzzle glow at the barrel for 3 ticks
  (larger and white-cored for the charged shot) and 2 short sparks thrown along the shot (3 for the charged shot); the
  bolt itself is the M1 renderer with the `PLAYER` palette. The shooter's view model and the arms kick (stronger for the
  charged shot, lighter for the repeater).
- **Rifle charge.** A rising whine (`blaster.charge`) for everyone near, cut when the charge is dropped, swapped away or
  fired; when loaded, a latch click for the shooter, and the reticle draws in, then shows corner brackets.
- **Overheat (`blaster_heat` 0).** Impact: 4 hard `steam_vent` jets thrown sideways from the vents. Aftermath: 8 more
  over the next ticks that follow the gun (12 in all). The gauge flashes (steady with flashing effects off).
- **Vent (1).** 5 puffs of steam. **Perfect vent (2):** one large, bright puff that swells as it rises, and 2 small
  ones; the gauge's band glows.
- **Repeater spin.** While spinning, a few faint steel motes circling the muzzle; the spin sounds.
- **Vibro flurry.** 3 steel sparks and 2 flicks of grit at the impact. **Cleave:** 5 sparks spread in an arc across the
  sweep. **Guard:** 3 steel sparks back toward the attacker and a clang pitched by the stamina left. **Notch:** 4
  hot-orange sparks; a guard break adds grit and the guard-break sound.
- **Electrostaff.** Charge: the rising crackle. Loaded: single arcs crawling at both emitter ends until the shock or the
  charge ends. Shock: a snap of 8 arc sparks on the target (and on the arc target). A saber held off: a contact flash
  and 6 saber sparks. Sweep: a low swing sound and 3 flicks of grit beside the struck enemy. Spent: a weak fizz.
- **Jet pack burst.** 6 `flame_jet` tongues from behind the shoulders along the exhaust at once, then 8 more over the
  next 8 ticks from wherever the pack now is (14 in all), and the roar. **Empty:** the click and one grey wisp.
- **Set bonus (`armour_set`, the M3 flourish extended).** The plate sets use the trooper clasp glints, spacer's gear the
  robe's cloth puff, each with its own tint (Watch green-white, Sith war red, Mandalorian gold, spacer tan) and toast
  colour.
