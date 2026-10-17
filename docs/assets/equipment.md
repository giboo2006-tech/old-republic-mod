# Equipment assets: era armour (Milestone 3)

Everything below is original art and sound made by our own scripts; nothing is copied from or imitates a Star Wars game
or film costume or prop. Textures are **placeholders** from `tools/assets/gen_textures_equipment.py` (PIL,
deterministic, seeded per texture; run from the repository root). The JSON files are written by the same script and are
final unless noted. Sounds are designed stand-ins from `tools/assets/sounds/equipment.py`, rendered by
`python3 tools/assets/gen_sounds.py --m3 --feature equipment`; their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (the lead's files) do not change.

`gen_textures_equipment.py` also exports `PALETTE`, `composite(...)` and the plate painters, which
`gen_textures_trooper.py` imports, so the trooper mobs wear exactly the armour the player can craft (see
`docs/assets/enemy.md`).

Palette (muted and worn): Republic off-white plate over a navy undersuit with a dull brass stripe and a blue-grey visor;
Imperial gunmetal plate over a near-black undersuit with crimson piping and a black visor; Jedi earth-brown outer robe
over an oat tunic, tan trousers, brown leather, gold-thread trim; Sith charcoal and black cloth with an oxblood sash and
lining, steel buckles. The rank 3 robes add a mantle and a diagonal sash: the Knight's in deep brown with a cream trim and
a cream sash stitched in gold; the Lord's in crimson with a steel trim and a black sash stitched in steel.

## Textures

| Path (under `src/main/resources/assets/oldrepublic/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/item/republic_trooper_{helmet,chestplate,leggings,boots}.png` | Inventory icons, 16x16 | placeholder generated | Off-white plate with dark outline and a navy undersuit: a rounded helmet with one wide blue-grey visor band; a cuirass with a brass belt buckle; plated leggings; boots with off-white shin plates. Clean military, unbranded, readable at 16 px next to iron armour. |
| `textures/item/sith_trooper_{helmet,chestplate,leggings,boots}.png` | Inventory icons, 16x16 | placeholder generated | Gunmetal plate with crimson piping: an angular helmet with a brow ridge and two narrow eye slits; a cuirass with crimson shoulder lines; leggings and boots in the same plate. The silhouettes must differ from the Republic set at a glance (angular, darker). |
| `textures/item/jedi_{hood,robe,trousers,boots}.png` | Inventory icons, 16x16 | placeholder generated | Earth-brown hood with a deep dark opening; the robe as a brown outer robe open over an oat tunic with a belt; tan trousers; wrapped brown boots. Homespun, practical. |
| `textures/item/jedi_knight_robe.png` | The Knight's robe icon, 16x16 | placeholder generated | The Jedi robe with a broad cream mantle across the shoulders and a cream sash, gold-thread trim. Must read as "the same robe, but earned". |
| `textures/item/sith_{hood,robe,trousers,boots}.png` | Inventory icons, 16x16 | placeholder generated | Charcoal-black hood with a dark opening; a black robe with an oxblood sash; dark trousers with a red waistband; black boots with steel buckles. |
| `textures/item/sith_lord_robe.png` | The Lord's robe icon, 16x16 | placeholder generated | The Sith robe with a crimson mantle and a black sash stitched in steel. |
| `textures/entity/equipment/humanoid/republic_trooper.png`, `sith_trooper.png` | Plain armour layer (helmet, chest, arms, boots), 64x32 in the vanilla layout | placeholder generated | Head 0,0 (hat 32,0 left empty), body 16,16, arms 40,16, legs 0,16 (boots only, rows 7-11). Republic: the rounded helmet and visor band, chest plates over a segmented abdomen, a brass-buckled belt, pauldrons with a brass stripe, vambraces, gloves. Imperial: the angular helmet (brow ridge, eye slits, nose ridge, angled cheeks), crimson piping, the same plate arrangement in gunmetal. Lit top-left plate edges and shadowed bottom-right edges so the plates read as thick. |
| `textures/entity/equipment/humanoid_leggings/republic_trooper.png`, `sith_trooper.png` | Plain leggings layer, 64x32 | placeholder generated | Body 16,16 (hips only, rows 9-11, a plate at the front), legs 0,16: thigh plates, a knee pad, the undersuit shin. |
| `textures/entity/equipment/humanoid/jedi_robes.png`, `sith_robes.png` | Plain robe layer, 64x32 | placeholder generated | Head: a deep hood open at the face (transparent), shadowed inside the opening. Body: the tunic with crossed lapels under the open outer robe, sash and belt. Arms: a wide outer sleeve, a folded cuff, the tunic sleeve, a glove. Legs: wrapped leather boots. Cloth shows soft vertical folds. Used when robe models are off. |
| `textures/entity/equipment/humanoid_leggings/jedi_robes.png`, `sith_robes.png` | Plain trousers layer, 64x32 | placeholder generated | Loose trousers with fold lines, tucked into the boots, and the waistband. |
| `textures/entity/equipment/humanoid/jedi_knight.png`, `sith_lord.png` | Plain layer of the rank 3 robes (chest only), 64x32 | placeholder generated | The set's robe body and sleeves with the mantle colours over the shoulders and the diagonal sash from the left shoulder to the right hip. No head, legs or leggings layer: the rank 3 items are chest pieces. |
| `textures/entity/robe/jedi_robes.png`, `sith_robes.png` | Robe model texture, 64x64 (layout below) | placeholder generated | Paint every box of the robe model: the hood with its brim and side flaps and a cowl, the torso (tunic, lapels, outer robe, sash), sleeves with bell cuffs, the back panel, the belt, the waist band, trousers and the front and back skirt panels, boots and their cloth wraps. Cloth should read as heavy woven fabric with folds and a slightly darker hem; leather as worn and creased. |
| `textures/entity/robe/jedi_knight.png`, `sith_lord.png` | Rank 3 robe model textures, 64x64 | placeholder generated | As the base robe texture, plus the mantle box (30,50) in the rank colours with a trim along its edge, and the sash box (56,26) stitched with a thread every third texel. Sleeves take the mantle colour with a trimmed cuff. |

### Robe model layout (64x64)

Mirrored in `com.oldrepublic.client.equipment.RobeModel` and `ROBE_UV` in the generator. Each box is `(u, v)` with size
`w x h x d` in the vanilla cube layout (top and bottom in the first `d` rows, then east, front, west and back). Left
limbs mirror the right ones.

| Part | UV | Box (w x h x d) | Layer |
|---|---|---|---|
| hood | 0,0 | 8x8x8 | hood |
| brim (over the brow) | 0,57 | 9x2x2 | hood |
| side flaps | 56,0 | 1x7x2 | hood |
| cowl (over the shoulders) | 0,32 | 9x2x5 | hood |
| torso | 32,0 | 8x12x4 | robe |
| sleeves | 0,16 | 4x12x4 | robe |
| bell cuffs | 24,39 | 5x4x5 | robe |
| back panel | 48,16 | 7x9x1 | robe |
| belt | 28,32 | 8x2x4 | robe |
| mantle (rank 3) | 30,50 | 10x3x6 | rank robe |
| sash (rank 3) | 56,26 | 2x15x1 | rank robe |
| waist band | 0,39 | 8x3x4 | trousers |
| legs | 16,16 | 4x12x4 | trousers |
| skirt front | 44,39 | 4x10x1 | trousers |
| skirt back | 0,46 | 4x10x1 | trousers |
| boots | 32,16 | 4x5x4 | boots |
| wraps | 10,48 | 5x2x5 | boots |

The plain layers use the vanilla armour layout (64x32): head 0,0 (hat 32,0 left empty), body 16,16, arms 40,16, legs
0,16; boots go in `humanoid`, trousers and hips in `humanoid_leggings`.

## JSON

| Path | What it is | Status |
|---|---|---|
| `equipment/republic_trooper.json`, `sith_trooper.json`, `jedi_robes.json`, `sith_robes.json` | Equipment assets: layers `humanoid` and `humanoid_leggings`, texture `oldrepublic:<asset>` | final |
| `equipment/jedi_knight.json`, `sith_lord.json` | Equipment assets of the rank 3 robes: the `humanoid` layer only | final |
| `items/<18 ids>.json` | Client item definitions (`minecraft:model`) | final |
| `models/item/<18 ids>.json` | Flat `item/generated` models with the icon | final |
| `data/oldrepublic/recipe/<16 ids>.json` | Shaped recipes (no recipe for the rank 3 robes) | final |
| `data/oldrepublic/tags/item/repairs/{trooper_armour,jedi_robes,sith_robes}.json`, `jedi_robe_cloth.json`, `sith_robe_cloth.json` | Repair and cloth tags | final |

Vanilla draws the trooper sets from the equipment assets. The ten robe items are drawn by `RobeArmorRenderer`: the robe
model with `textures/entity/robe/<asset>.png` when `ClientConfig.robeModels` is on, and vanilla armour shapes with the
plain layer texture when it is off. A final artist replacing the robe texture must keep the layout above, or update
`RobeModel` and `ROBE_UV` together.

## Sounds

`src/main/resources/assets/oldrepublic/sounds/armour/*.ogg`, placeholders synthesised by `tools/assets/sounds/equipment.py`.

| Event | File | Placeholder now | Final sound brief |
|---|---|---|---|
| `armour.equip_trooper` | `armour/equip_trooper` | A dull plate knock, two latch clicks, a strap cinched, a short rattle (0.7 s) | Hard composite plates seated against a padded undersuit: a muted knock (not a ringing metal clang), two crisp magnetic or latch clasps, a webbing strap pulled tight, a light settle of plates. Short (under 0.8 s), dry, close-miked. Played by vanilla as the material's equip sound. |
| `armour.equip_robe` | `armour/equip_robe` | A soft cloth swish, folds settling, a belt buckle closed (0.9 s) | Heavy woven cloth drawn over the shoulders: one full swish, the folds settling, and a small leather-and-metal buckle closing at the end. Warm and soft, no rustling plastic. |
| `armour.set_bonus` | `armour/set_bonus` | A last soft clasp, then a warm two-note rise ringing out under a breath of air (1.6 s) | The "set complete" sting heard only by the wearer: a final clasp or cloth settle, then a short warm rising interval (a fifth) in a plucked-metal or soft bell timbre, decaying cleanly. Not a fanfare and not magical chimes; it should sit under combat sounds. The client pitches it per set: 1.0 Republic, 0.9 Imperial, 1.06 Jedi, 0.88 Sith. |

## VFX (the `armour_set` flourish, phase by phase)

Sent to the wearer only, once per equip when a piece completes a set. About 12 particles through `Vfx.count`, using the
enemy feature's `flare_ember` sprite (glows, glints and fibres, tinted per set) and vanilla dust in the cloth colour (the
robe puffs); no ground ring, no halo.

- **Activation:** trooper sets: the shoulder clasps catch the light, a soft glow in the army colour (blue-white or
  crimson) with two steel glints per clasp. Robes: a soft puff of cloth at each shoulder as the robe settles.
- **Aftermath (6 ticks):** trooper sets: glints run down the chest straps to the belt as the plates settle. Robes: a few
  loose fibres drift down from the hood's edge.
- In first person the flourish is moved forward a little so it shows at the bottom of the view. With flashing effects
  off the clasp glow is slow and steady.
- The toast (11.2) shows at the same moment: a framed plate sliding in at the top right with the chest piece's icon, the
  bonus name and "Set bonus active" (or the unmet alignment gate), 70 ticks.
