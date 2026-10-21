# Saber, crafting and gear assets, Milestone 5: legendary parts, mods and ship parts, the materials, the rank 4 robes, the Cantor's Mask, blade lengths and the hum profiles

Everything below is original art and sound made by our own scripts. Nothing is copied from, or imitates, a Star Wars
game or film prop, icon or sound: the hilts are generic cylinders with original detailing, and the robes are our own
cuts in the M3 robe style. The palette is the mod's muted, worn Old Republic one (weathered durasteel greys, sand,
deep blue and red accents), with four material colours of our own: warm sunstone amber, blued clan iron with a red clan
mark, pale stormglass blue and violet-grey hymnstone.

Textures are **placeholders** made by PIL, deterministically (re-running any generator leaves the files byte-identical),
in the generators 17.2 names:

- `python3 tools/assets/gen_textures_saber.py`: the legendary hilt parts and lens, the legendary hilt swatches and
  the regenerated `items/lightsaber*.json` and `items/training_saber*.json`.
- `python3 tools/assets/gen_textures_crafting.py --m5`: `hymnstone_shard`, `clan_iron`, `spindle_capacitor`,
  `hymn_tuned_drive`.
- `python3 tools/assets/gen_textures_equipment.py --m5`: the four legendary mods, the three rank 4 pieces and the
  Cantor's Mask (icons, robe model textures, plain humanoid layers, equipment assets).

Sounds are **designed stand-ins** from `tools/assets/sounds/saber_m5.py`, rendered by
`python3 tools/assets/gen_sounds.py --m5 --feature saber_m5`. Their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (lead files) do not change.

The design is in `docs/design/sabers_m5.md`. All paths are under `src/main/resources/assets/oldrepublic/`.

## Textures

### Item icons

16 × 16, drawn at the item-icon angle with a dark outline, like the M3 and M4 parts and mods.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/item/emitter_cantor.png` | The Cantor's Emitter (legendary emitter) | placeholder generated | A slim steel emitter throat under a flared crown of violet-grey hymnstone, with a thin resonator fin down one side and a dull ember-red band at the waist. It should read as old, sung-over craft, not a weapon: no spikes. |
| `textures/item/grip_sunforged.png` | Sunforged Grip (legendary grip) | placeholder generated | A dark grip core banded three times with warm sunstone amber, a thin inlaid strip running the length. The amber should look like stone that holds the sun, not gold plating. |
| `textures/item/pommel_hask.png` | Hask Pommel (legendary pommel) | placeholder generated | A heavy, squared block of blued, hammer-marked clan iron with a cap and an ember-red notch cut into each side: a warrior clan's smithing, blunt and plain, with no borrowed emblems. |
| `textures/item/lens_stormglass.png` | Stormglass Lens (legendary lens) | placeholder generated | A round lens of pale storm-blue glass in a clan-iron bezel with a brass rim, a forked white streak of frozen lightning inside. |
| `textures/item/hymnstone_shard.png` | `hymnstone_shard` (material) | placeholder generated | A tall faceted shard of pale lilac-grey stone with a smaller shard leaning on it; two faint wavering pale lines run through the large one (the stone that sings). Not glowing. |
| `textures/item/clan_iron.png` | `clan_iron` (material) | placeholder generated | A blackened iron bar, hammer-dented, stamped with a red clan notch on its top face and a seam of red along one side. |
| `textures/item/spindle_capacitor.png` | Spindle Capacitor (legendary ship part) | placeholder generated | A squat capacitor drum: brass end caps, a blackened body wound with three coils, a cold blue charge window and a small lilac spark at its heart. |
| `textures/item/hymn_tuned_drive.png` | Hymn-Tuned Drive (legendary ship part) | placeholder generated | A drive-motivator block with two lilac resonance tines on top like a tuning fork, and a pale stormglass seam along its side. |
| `textures/item/mod_clan_iron_plating.png` | Clan-Iron Plating (legendary armour mod) | placeholder generated | A heavy blackened plate, riveted at the corners, crossed by a band of clan red, in the style of the M4 mod icons. |
| `textures/item/mod_hymnweave_lining.png` | Hymnweave Lining (legendary armour mod) | placeholder generated | A square of lilac cloth woven with a silver-thread lattice, hemmed in silver. |
| `textures/item/mod_storm_cell.png` | Storm Cell (legendary blaster cell) | placeholder generated | A cell canister with a brass cap and a stormglass window, a white fork of lightning caught inside, in the style of the M4 cell icons. |
| `textures/item/mod_sunstone_edge.png` | Sunstone Edge (legendary vibro edge) | placeholder generated | A short blade edge forged round a seam of sunstone that runs amber to white along the cutting line. |
| `textures/item/jedi_master_robe.png` | Master's Robe | placeholder generated | The Knight's robe icon in a deeper brown, a pale inner tunic, and a long cloak behind it with gold-thread borders. |
| `textures/item/sith_darth_robe.png` | Darth's Robe | placeholder generated | A black robe with a red-lined high collar standing up behind the neck, a red sash and a short cape. |
| `textures/item/wayfarer_mantle.png` | Wayfarer's Mantle | placeholder generated | A sand-coloured capelet over a brown travelling jacket, a teal mantle hanging behind, and a small brass compass clasp at the throat. |
| `textures/item/cantors_mask.png` | Cantor's Mask | placeholder generated | A pale bone face plate: almond eye slits, an open round singing mouth, faint stave lines across the brow, a lilac hymnstone in the forehead and a small tuning-fork crest above. Eerie and still, not a skull. |

### Hilt swatches

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/item/lightsaber_hilt_legendary.png` | Material bands of the three legendary hilt parts' 3D models, 16 × 16 | placeholder generated | Bands (UV) like the M1 `lightsaber_hilt.png`: v 0-4 sunstone (warm amber with brighter veins), v 4-8 clan iron (blued, hammered, a rust edge on the last row), v 8-12 stormglass (pale glass with a white streak), v 12-16 hymnstone (u 0-8, violet-grey with pale flecks) and ember inlay (u 8-16, a dull red-orange). The part models sample the top-left of each band, sized to each face. A final artist may unwrap each part and change the face UVs in the generator. |

### Robe and mask textures

`client/equipment/RobeArmorRenderer` (Fabric's armour renderer for these items, so it serves every humanoid wearer)
draws the robe model textures on the `RobeModel` shapes while the `robeModels` client option is on; with it off, it
draws vanilla's armour shapes with the plain humanoid layers (the equipment asset's `humanoid` layer).

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/entity/robe/jedi_master.png` | Master's Robe model texture, 128 × 64 | placeholder generated | Left 64 × 64: the rank 3 Jedi robe's layout in a deeper brown with gold-thread hems. Right half: the long cloak's boxes (deep vertical folds, the light catching the top of each, a gold border at the hem and edges), mirrored in `RobeModel.createMasterRobe`. Keep the cloak reading as heavy wool, not silk. |
| `textures/entity/robe/sith_darth.png` | Darth's Robe model texture, 128 × 64 | placeholder generated | Left 64 × 64: the rank 3 Sith robe's layout in near-black with red trim. Right half: the high collar (rounded crown curving down to the shoulders, red lining on the inner face), the pauldrons, and a short red-trimmed cape, mirrored in `RobeModel.createDarthRobe`. |
| `textures/entity/robe/wayfarer.png` | Wayfarer's Mantle model texture, 64 × 64 | placeholder generated | A dusty sand capelet whose seams run out from the neck, over a brown travelling jacket with a belt; sleeves; a teal mantle down the back with a faded rust-red panel; a folded hood at the neck; a brass compass-rose clasp (a dark pin in a bright ring). Mirrored in `RobeModel.createWayfarerMantle`. |
| `textures/entity/robe/cantors_mask.png` | Cantor's Mask model texture, 64 × 32 | placeholder generated | The mask's thin 9 × 9 plate (front face: pale bone with darker shading, engraved stave lines, the eye slits and mouth cut through), its dark strap round the head, and the 3 × 4 tuning-fork crest with faintly bright tips. Mirrored in `RobeModel.createCantorsMask`; transparent where the plate is cut. |
| `textures/entity/equipment/humanoid/jedi_master.png` | Master's Robe plain layer, 64 × 32 | placeholder generated | The vanilla chestplate layout: the robe's body and arms, with the cloak painted on the back face down to the hem. |
| `textures/entity/equipment/humanoid/sith_darth.png` | Darth's Robe plain layer, 64 × 32 | placeholder generated | The body and arms in black with red trim, the red-trimmed cape on the back to mid-back. |
| `textures/entity/equipment/humanoid/wayfarer.png` | Wayfarer's Mantle plain layer, 64 × 32 | placeholder generated | The capelet over the top five rows, the jacket below, the teal mantle on the back. |
| `textures/entity/equipment/humanoid/cantors_mask.png` | Cantor's Mask plain layer, 64 × 32 | placeholder generated | The vanilla helmet layout: only the front face (8 × 8) carries the mask; the rest is transparent but a thin strap. |

## Models, item definitions and equipment assets

All are generated with the textures and are final in structure (only the art they point at is a placeholder).

| Path | What it is |
|---|---|
| `items/{emitter_cantor,grip_sunforged,pommel_hask,lens_stormglass}.json`, `models/item/<same>.json` | The four legendary saber part icons (flat `item/generated`). |
| `items/{mod_clan_iron_plating,mod_hymnweave_lining,mod_storm_cell,mod_sunstone_edge}.json`, `models/item/<same>.json` | The four legendary mod icons. |
| `items/{spindle_capacitor,hymn_tuned_drive,hymnstone_shard,clan_iron}.json`, `models/item/<same>.json` | Ship parts and materials. |
| `items/{jedi_master_robe,sith_darth_robe,wayfarer_mantle,cantors_mask}.json`, `models/item/<same>.json` | The robes' and the mask's icons. |
| `models/item/lightsaber/{emitter_cantor,grip_sunforged,pommel_hask}.json`, `models/item/lightsaber/guard/<same>.json` | The legendary hilt parts' 3D sections (held and guard pose), textured from `lightsaber_hilt_legendary.png`. |
| `models/item/lightsaber/staff/emitter_cantor_{upper,lower}.json`, `models/item/lightsaber/staff_guard/<same>.json` | The Cantor's Emitter at both ends of a saberstaff. |
| `items/lightsaber.json`, `items/lightsaber_staff.json`, `items/training_saber.json`, `items/training_saber_red.json` | Regenerated: the hilt part selects gain the legendary parts. Short and long blades were stage models until October 2026; the blade is now drawn in code and scaled ×0.7 or ×1.15 (`SaberModelProperties.bladeScale`). |
| `equipment/{jedi_master,sith_darth,wayfarer,cantors_mask}.json` | Equipment assets: each a `humanoid` layer pointing at its plain-layer texture. |

## Effects

No new particle types (spec 15.2: W7 has none). The effects reuse `oldrepublic:crystal_glint` (the tuning burst),
`oldrepublic:spark_shower` (the legendary rise) and `oldrepublic:lightning_spark` (the storm arc), the M4
`SaberSparks.glow` point light (the legendary glow), and the swing trail every blade leaves (the off blade's own
arc). The phase-by-phase treatment is in the design doc, section 13. Nothing draws rings, sigils, halos or glow outlines.

## Sounds

All are synthesised with the `gen_sounds.py` helpers; nothing is sampled. Mono OGG at 44.1 kHz. Since October 2026 every
saber sound is built from one film-style hum (a warm motor layer and a hard electrical buzz a fraction of a hertz apart;
see `docs/AUDIO_BRIEF.md`, Lightsaber), each emitter with its own hum character, so an emitter's ignition, hum and
retraction sound like one blade. The hums are seamless 4 s loops: every modulation runs a whole number of cycles per loop
and every filter is circular. The hum events are listed at volume 0.6 (events_m5.json), and the client's hum has its own
volume on top of that: 0.5 for the main hand, 0.375 for the off hand, lifted by up to 30% (and its pitch by up to 8%) as
the blade moves (`SaberHumSound`, times the mod sound volume option).

| Event | File (under `sounds/`) | Length | Final sound (as rendered) | Brief for the final sound designer |
|---|---|---|---|---|
| `saber.guardian.ignite` | `saber/guardian_ignite.ogg` | 1.53 s | A soft snap, a dark hiss and a low thud, and the deep hum climbing slowly out of them from 62% of its pitch, settling heavy and even, in a larger room. | The weightiest ignition: a soldier's blade. Deep, steady, unhurried. |
| `saber.guardian.hum` | `saber/guardian_hum.ogg` | 4.00 s, loop | A deep 76 Hz hum: a dark motor with a sub-octave, little buzz and a slow, even throb. | Steady and reassuring; must loop with no seam and sit under combat. |
| `saber.guardian.retract` | `saber/guardian_retract.ogg` | 1.19 s | The hum sinks slowly and fades under a dark hiss, and ends on a soft, low thud. | |
| `saber.duelist.ignite` | `saber/duelist_ignite.ogg` | 0.96 s | A hard, quick snap and a bright hiss, the thin hum there almost at once (an 18 ms glide with a 7% overshoot), and a high zing. | Fast and precise: a duellist's blade. |
| `saber.duelist.hum` | `saber/duelist_hum.ogg` | 4.00 s, loop | A bright, thin 112 Hz hum, its low end cut, with a strong, bright buzz beating quickly against the motor. | Lighter and more nervous than the Guardian; never shrill. |
| `saber.duelist.retract` | `saber/duelist_retract.ogg` | 0.65 s | A fast, bright fall under a quick hiss, with a tiny click at 0.22 s. | |
| `saber.reliquary.ignite` | `saber/reliquary_ignite.ogg` | 1.59 s | It sputters twice (two snaps of half-lit, crackling hum) before it catches at 0.32 s with a third snap and the rush, and a crackle that settles. | An old, half-trusted crystal: unreliable at first, then it holds. |
| `saber.reliquary.hum` | `saber/reliquary_hum.ogg` | 4.00 s, loop | An 86 Hz hum with an uneven pitch waver, power that sags and catches, a faint crackle and a dirty buzz. | Old and alive; the waver must stay subtle enough to hear for minutes. |
| `saber.reliquary.retract` | `saber/reliquary_retract.ogg` | 1.14 s | A trembling fall: the hum dives with a shiver and a fizz, flickers once more and dies with a small pop. | |
| `saber.cantor.ignite` | `saber/cantor_ignite.ogg` | 2.20 s | A low 70 Hz ignition (a soft snap, a dark hiss, an unhurried glide); as the blade settles, a breath and then wordless voices swell on a major chord and its octave, in a long reverb. | The Cantors' hymn caught in a crystal. Never words, never a solo voice: a distant choir in the blade. |
| `saber.cantor.hum` | `saber/cantor_hum.ogg` | 4.00 s, loop | A low, soft 70 Hz hum under a quiet three-voice choir (the 4th, 5th and 6th harmonics) that breathes in and out twice a loop. | Haunting but calm; it should not tire over a long fight. |
| `saber.cantor.retract` | `saber/cantor_retract.ogg` | 1.72 s | The blade sinks away under a dark hiss, and the choir sighs a little lower and fades after it. | |
| `saber.offhand_swing` | `saber/offhand_swing.ogg` | 0.55 s | The off hand's cut: a shorter, quicker pass than the M1 swing, a little higher (104 Hz) and thinner (its low end cut). | Must read as the second blade between the main hand's swings, not as a repeat of it. |
| `bench.legendary` | `bench/legendary.ogg` | 3.83 s | The piece set down (a soft thump), a rising gold chime (C5, E5, G5, C6), a low choir swell and a fine high sparkle under a long reverb. | Something rare has been made. Warm and earned, not a fanfare; also plays under the `legendary` banner. |

Reused: `workbench.tune` (the M4 reserved sound, kept as it is) for a tuning.

## Data and lang

- `data/oldrepublic/schematic/`: `concussion_missile.json` and the ten `<component>_legendary.json` (final content;
  the level of the eight discipline schematics is written from `crafting.legendaryLevel` at load).
- `src/main/lang-parts/saber.json` (the tunings, lengths, Tune tab and length switch lines, part and lens names),
  `crafting.json` (materials, ship parts, legendary bench and toast lines), `equipment.json` (the mods and their
  effect lines, the robes, the mask and the rank 4 tooltip lines). Final.
