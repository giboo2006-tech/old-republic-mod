# Asset manifest (Milestone 1)

Every texture, sound and structure in the mod is an original **placeholder** produced by a script in `tools/assets/`. Nothing is copied from films, games or other mods. This file lists every asset in one place; the per-feature files hold the full briefs for the final artists and sound designers:

- [Force Push](assets/force.md)
- [Quest and HUD](assets/quest.md)
- [Lightsaber and workbench](assets/saber.md)
- [Crystal Poacher and blaster](assets/enemy.md)
- [Crystal caves and the Trial of Reflection](assets/world.md)
- [HUD and screens (UI kit)](assets/ui.md)
- [Audio](AUDIO_BRIEF.md)

Paths below are relative to `src/main/resources/`; `A/` stands for `assets/oldrepublic/` and `D/` for `data/oldrepublic/`. Every listed path was checked against the repository: **no listed asset is missing.**

## Summary

| Category | Count | Status | Generator |
|---|---|---|---|
| Textures, Force Push particles | 16 PNG | placeholder | `gen_textures_force.py` |
| Textures, quest and HUD | 12 PNG | placeholder | `gen_textures_quest.py` |
| Textures, lightsaber and workbench | 21 PNG | placeholder | `gen_textures_saber.py` |
| Textures, Crystal Poacher and blaster | 11 PNG | placeholder | `gen_textures_enemy.py` |
| Textures, crystal caves and trial | 21 PNG | placeholder | `gen_textures_world.py` |
| **Textures total** | **81 PNG** | placeholder | |
| Sounds | 41 events, 55 OGG files | placeholder | `gen_sounds.py` |
| Structure templates | 3 NBT | placeholder | `gen_structures.py` |
| Lightsaber and workbench models, item definitions, blockstate | 35 JSON | 3D models placeholder, definitions final | `gen_textures_saber.py` |
| Particle sprite lists | 16 JSON | final | force and saber scripts write theirs; the rest are hand-written |
| Other models, item definitions, blockstates | JSON | final (blaster in-hand model placeholder) | hand-written |
| Recipes, loot tables, tags, damage types, worldgen | JSON | final (tuning may change) | hand-written |

## Generator scripts

All scripts are deterministic (fixed seeds) and write straight into `src/main/resources/` (and, for sounds, a few other files). Run them from the repository root:

| Script | Writes | Needs |
|---|---|---|
| `python3 tools/assets/gen_textures_force.py` | Force Push particle sprites and their 5 particle JSONs | Python 3, numpy, Pillow |
| `python3 tools/assets/gen_textures_quest.py` | Journal item, journal screen, HUD icons, meditation and Calling particles | Pillow |
| `python3 tools/assets/gen_textures_saber.py` | Saber, crystal, part and workbench textures, plus every saber/workbench model, item definition, blockstate and its 2 particle JSONs | Pillow |
| `python3 tools/assets/gen_textures_enemy.py` | Poacher skin, blaster sprites, spawn egg, blaster particles | Pillow |
| `python3 tools/assets/gen_textures_world.py` | Cave block textures, cave and trial particles, Shadow Echo fallback skin | Pillow |
| `python3 tools/assets/gen_structures.py` | `D/structure/crystal_cave/{camp,descent,heart}.nbt` (byte-identical on every run) | Python 3 only |
| `python3 tools/assets/gen_sounds.py` | All OGG files, `A/sounds.json`, subtitles in `src/main/lang-parts/sounds.json` | numpy, `ffmpeg` with libvorbis on PATH |

`gen_sounds.py` options: `--java` also rewrites `src/main/java/com/oldrepublic/registry/ModSounds.java` (use it after adding or renaming a sound event); `--only saber/hum ...` renders only the named files; `--keep-wav DIR` keeps the intermediate WAVs; `--no-audio` writes only the JSON, lang and Java files.

To replace a placeholder with final art, keep the file name and format (and, for sounds, the rules in `AUDIO_BRIEF.md`). Once a file is final, stop rerunning its script, or remove that file from the script, so it is not overwritten.

---

## Force Push

Brief: [assets/force.md](assets/force.md). Sprites are near-white greyscale, tinted in code.

| Path | What it is | Status |
|---|---|---|
| `A/textures/particle/air_ripple_0..3.png` | Bent-air shell, 4 frames (32x32) | placeholder |
| `A/textures/particle/push_streak_0..2.png` | Directional dust streak, 3 variants (16x16) | placeholder |
| `A/textures/particle/push_impact_0..3.png` | Dust puff where a push lands, 4 frames (32x32) | placeholder |
| `A/textures/particle/gather_dust_0..2.png` | Dust mote for charge and aftermath (8x8) | placeholder |
| `A/textures/particle/fail_mote_0..1.png` | Dull mote on energy failure (8x8) | placeholder |
| `A/particles/air_ripple.json`, `push_streak.json`, `push_impact.json`, `gather_dust.json`, `fail_mote.json` | Particle sprite lists | final |
| `D/tags/entity_type/push_resistant.json` | `#oldrepublic:push_resistant` entity tag | final |

## Quest and HUD

Brief: [assets/quest.md](assets/quest.md). The energy meter, XP bar, focus ring and banners are drawn in code and need no texture.

| Path | What it is | Status |
|---|---|---|
| `A/textures/item/wanderers_journal.png` | Wanderer's Journal item (16x16) | placeholder |
| `A/textures/gui/journal.png` | Journal screen background (320x200) | placeholder |
| `A/textures/gui/hud/resonance.png` | Resonance glyph (9x9) | placeholder |
| `A/textures/gui/hud/force_push.png` | Force Push cooldown icon (16x16) | placeholder |
| `A/textures/particle/force_mote_0..3.png` | Meditation motes (8x8) | placeholder |
| `A/textures/particle/resonance_shimmer_0..3.png` | Calling glints (8x8) | placeholder |
| `A/items/wanderers_journal.json`, `A/models/item/wanderers_journal.json` | Item definition and model | final |
| `A/particles/force_mote.json`, `resonance_shimmer.json` | Particle sprite lists | final |
| `D/recipe/wanderers_journal.json` | Replacement journal: book + leather + iron nugget | final |

## Lightsaber and workbench

Brief: [assets/saber.md](assets/saber.md). Blade and crystal textures are white and tinted in code.

| Path | What it is | Status |
|---|---|---|
| `A/textures/item/lightsaber_icon_hilt.png` | Inventory hilt (16x16) | placeholder |
| `A/textures/item/lightsaber_icon_blade.png` | Inventory blade layer (16x16) | placeholder |
| `A/textures/item/lightsaber_hilt.png` | Material swatches for the 3D hilt | placeholder |
| `A/textures/item/lightsaber_blade.png` | Blade core and glow bands | placeholder |
| `A/textures/item/kyber_crystal.png` | Crystal body (16x16) | placeholder |
| `A/textures/item/kyber_crystal_glint.png` | Crystal highlight layer | placeholder |
| `A/textures/item/emitter_guardian.png`, `emitter_duelist.png` | Emitter icons | placeholder |
| `A/textures/item/grip_wrapped.png`, `grip_ridged.png` | Grip icons | placeholder |
| `A/textures/item/pommel_weighted.png`, `pommel_balanced.png` | Pommel icons | placeholder |
| `A/textures/block/lightsaber_workbench_{top,side,front,metal,lamp}.png` | Workbench faces | placeholder |
| `A/textures/gui/container/lightsaber_workbench.png` | Workbench screen (230x200) | placeholder |
| `A/textures/particle/saber_spark_0..1.png` | Saber sparks (8x8) | placeholder |
| `A/textures/particle/contact_flash.png` | One-tick contact flash (8x8) | placeholder |
| `A/items/lightsaber.json` | Item definition: flat icon in GUIs, 3D hilt plus blade stages elsewhere | final |
| `A/models/item/lightsaber/base.json` | Shared parent with display transforms | placeholder, **needs in-game tuning** |
| `A/models/item/lightsaber/{emitter,grip,pommel}_*.json` | 3D hilt parts (6) | placeholder |
| `A/models/item/lightsaber/blade_0..6.json` | Blade extension stages | placeholder |
| `A/models/item/lightsaber_icon.json`, `lightsaber_icon_lit.json` | Flat icons | final |
| `A/items/kyber_crystal.json`, `A/models/item/kyber_crystal.json`, `kyber_crystal_held.json` | Crystal item definition and models | placeholder |
| `A/items/<part>.json`, `A/models/item/<part>.json` | The six hilt part items | final |
| `A/models/block/lightsaber_workbench.json` | Workbench model | placeholder |
| `A/blockstates/lightsaber_workbench.json`, `A/items/lightsaber_workbench.json` | Blockstate and item definition | final |
| `A/particles/saber_spark.json`, `contact_flash.json` | Particle sprite lists | final |
| `D/recipe/lightsaber_workbench.json`, `grip_wrapped.json`, `grip_ridged.json`, `pommel_weighted.json`, `pommel_balanced.json` | Recipes (emitters have none on purpose) | final |
| `D/loot_table/blocks/lightsaber_workbench.json` | Workbench drops itself | final |

## Crystal Poacher and blaster

Brief and skin UV layout: [assets/enemy.md](assets/enemy.md). The blaster bolt and the muzzle glow are drawn in code and have no texture.

| Path | What it is | Status |
|---|---|---|
| `A/textures/entity/crystal_poacher.png` | Poacher skin (64x64, custom parts) | placeholder |
| `A/textures/item/blaster_rifle.png` | Blaster rifle inventory sprite | placeholder |
| `A/textures/item/blaster_rifle_model.png` | Swatches for the 3D in-hand model | placeholder |
| `A/textures/item/crystal_poacher_spawn_egg.png` | Spawn egg | placeholder |
| `A/textures/particle/muzzle_glow_0..1.png` | Muzzle glow cores (8x8) | placeholder |
| `A/textures/particle/bolt_impact_0..1.png` | Bolt impact sparks (8x8) | placeholder |
| `A/textures/particle/bolt_scorch_0..2.png` | Bolt scorch smoke (8x8) | placeholder |
| `A/items/blaster_rifle.json`, `A/models/item/blaster_rifle.json` | Item definition and flat model | final |
| `A/models/item/blaster_rifle_in_hand.json` | 3D held model (muzzle point used by `MuzzleGlowLayer`) | placeholder |
| `A/items/crystal_poacher_spawn_egg.json`, `A/models/item/crystal_poacher_spawn_egg.json` | Spawn egg definition and model | final |
| `A/particles/muzzle_glow.json`, `bolt_impact.json`, `bolt_scorch.json` | Particle sprite lists | final |
| `D/loot_table/entities/crystal_poacher.json` | Poacher loot (the emitter is dropped in code) | final |
| `D/damage_type/blaster.json`, `data/minecraft/tags/damage_type/is_projectile.json` | Blaster damage type, tagged as projectile | final |

## Crystal caves and the Trial of Reflection

Brief, including the rules for a final structure builder: [assets/world.md](assets/world.md).

| Path | What it is | Status |
|---|---|---|
| `A/textures/block/kyber_cluster.png` | Kyber cluster (cross model) | placeholder |
| `A/textures/block/unstable_crystal_cluster.png` | Unstable cluster at rest | placeholder |
| `A/textures/block/unstable_crystal_cluster_charging.png` | Unstable cluster during the tell | placeholder |
| `A/textures/block/crystal_veined_stone.png` | Breakable veined wall | placeholder |
| `A/textures/block/resonant_seal.png` | Translucent seal of light | placeholder |
| `A/textures/block/meditation_stone_{top,side,bottom}.png` | Meditation stone faces | placeholder |
| `A/textures/particle/crystal_mote_0..1.png` | Cave motes (8x8) | placeholder |
| `A/textures/particle/crystal_glint_0..3.png` | Crystal twinkle, 4 frames (8x8) | placeholder |
| `A/textures/particle/crystal_spark_0..1.png` | Discharge sparks (8x8) | placeholder |
| `A/textures/particle/shadow_wisp_0..3.png` | Shadow Echo smoke, 4 frames (8x8) | placeholder |
| `A/textures/entity/shadow_echo.png` | Echo fallback skin (normally it wears its owner's skin) | placeholder |
| `A/blockstates/{kyber_cluster,unstable_crystal_cluster,resonant_seal,crystal_veined_stone,meditation_stone}.json` | Blockstates | final |
| `A/models/block/*.json` (cave blocks) | Block models, incl. `resonant_seal_open` and the charging cluster | final |
| `A/models/item/kyber_cluster.json`, `unstable_crystal_cluster.json` | Flat item models | final |
| `A/items/{kyber_cluster,unstable_crystal_cluster,resonant_seal,crystal_veined_stone,meditation_stone}.json` | Item definitions | final |
| `A/particles/crystal_mote.json`, `crystal_glint.json`, `crystal_spark.json`, `shadow_wisp.json` | Particle sprite lists | final |
| `D/structure/crystal_cave/camp.nbt` | 25x16x25 poacher camp over the cave mouth | placeholder |
| `D/structure/crystal_cave/descent.nbt` | 9x30x32 shaft and stair tunnel | placeholder |
| `D/structure/crystal_cave/heart.nbt` | 45x22x48 cavern, hidden chamber, sealed trial chamber | placeholder |
| `D/worldgen/structure/crystal_cave.json`, `structure_set/crystal_caves.json`, `template_pool/crystal_cave/{camp,descent,heart}.json`, `processor_list/crystal_cave_weathering.json` | Jigsaw structure definition | final (spacing may be tuned) |
| `D/tags/worldgen/structure/crystal_caves.json`, `D/tags/worldgen/biome/has_structure/crystal_cave.json` | Structure and biome tags | final |
| `D/loot_table/chests/crystal_cave_hidden.json` | Hidden chamber stash | final (tuning) |
| `D/loot_table/blocks/{kyber_cluster,crystal_veined_stone,meditation_stone}.json` | Block drops | final |
| `D/damage_type/crystal_discharge.json` | Unstable discharge damage | final |

## Sounds

Brief: [AUDIO_BRIEF.md](AUDIO_BRIEF.md). All 55 files live in `A/sounds/<group>/<name>.ogg` and are registered in `A/sounds.json` (41 events, all `oldrepublic:` namespace). All are synthesised placeholders. Voice lines for the poacher are planned but not made yet.

| Event | Files | What it is |
|---|---|---|
| `saber.ignite` | `saber/ignite` | Ignition snap into the hum |
| `saber.retract` | `saber/retract` | Retraction dive |
| `saber.hum` | `saber/hum` | Idle hum (loop) |
| `saber.swing` | `saber/swing1..4` | Swing doppler passes |
| `saber.clash` | `saber/clash1..3` | Blade-on-blade contact |
| `saber.deflect` | `saber/deflect1..3` | Bolt batted away |
| `saber.parry` | `saber/parry` | Perfect-timing ring |
| `saber.guard_break` | `saber/guard_break` | Guard collapse |
| `saber.hit` | `saber/hit` | Blade hits a body |
| `blaster.charge` | `blaster/charge` | Poacher attack tell (exactly 0.6 s) |
| `blaster.fire` | `blaster/fire1..2` | Shot |
| `blaster.bolt_flyby` | `blaster/bolt_flyby` | Bolt passing close |
| `blaster.impact` | `blaster/impact` | Bolt hits something |
| `force.push.charge` | `force/push_charge` | Push charge (loop) |
| `force.push.release` | `force/push_release1..3` | Push release |
| `force.push.impact` | `force/push_impact` | Body struck by a push |
| `force.push.slam` | `force/push_slam` | Focused Blast wall slam |
| `force.fail` | `force/fail` | Not enough energy |
| `awakening.whispers` | `awakening/whispers` | Dream voices |
| `awakening.drone` | `awakening/drone` | Dream drone (loop) |
| `awakening.burst` | `awakening/burst` | Uncontrolled Force burst |
| `meditation.loop` | `meditation/loop` | Meditation pad (loop) |
| `meditation.complete` | `meditation/complete` | Meditation success chime |
| `meditation.break` | `meditation/break` | Meditation broken |
| `crystal.calling` | `crystal/calling` | The Calling ping |
| `crystal.resonance` | `crystal/resonance` | Formation hum (loop) |
| `crystal.chime` | `crystal/chime1..4` | Random cluster chimes |
| `crystal.unstable_charge` | `crystal/unstable_charge` | Hazard tell (exactly 1.2 s) |
| `crystal.discharge` | `crystal/discharge` | Hazard discharge |
| `crystal.form` | `crystal/form` | Kyber crystal forming |
| `ambient.cave` | `ambient/cave` | Cave bed (loop) |
| `trial.begin` | `trial/begin` | Chamber dims |
| `trial.echo_form` | `trial/echo_form` | Shadow Echo forming |
| `trial.echo_lunge` | `trial/echo_lunge` | Echo lunge tell |
| `trial.complete` | `trial/complete` | Trial won |
| `workbench.slot` | `workbench/slot` | Component seated |
| `workbench.assemble` | `workbench/assemble` | Saber assembled |
| `journal.open` | `journal/open` | Journal opened |
| `journal.entry` | `journal/entry` | New journal entry |
| `poacher.hurt` | `poacher/hurt1..2` | Poacher hurt |
| `poacher.death` | `poacher/death` | Poacher death |
