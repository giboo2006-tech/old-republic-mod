# Asset manifest (Milestones 1 and 2)

Every texture, sound and structure in the mod is an original **placeholder** produced by a script in `tools/assets/`. Nothing is copied from films, games or other mods. This file lists every asset in one place; the per-feature files hold the full briefs for the final artists and sound designers:

- [Force Push](assets/force.md)
- [Quest and HUD](assets/quest.md)
- [Lightsaber and workbench](assets/saber.md)
- [Crystal Poacher and blaster](assets/enemy.md)
- [Crystal caves and the Trial of Reflection](assets/world.md)
- [HUD and screens (UI kit)](assets/ui.md)
- [Audio](AUDIO_BRIEF.md)

Milestone 2 assets are listed in their own part at the end, [Milestone 2 additions](#milestone-2-additions). The sections before it describe Milestone 1 and are unchanged, except that the Milestone 1 sound count (41 events, 55 files) is now 96 events and 117 files in total.

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

---

# Milestone 2 additions

Same rules as above: every file is an original **placeholder** made by a script in `tools/assets/`, nothing is copied. Every path in this section was checked against the repository, and a script cross-checked every particle sprite list, item model texture, `sounds.json` entry and skill node icon against the files on disk: **no referenced Milestone 2 asset is missing.** What is missing is final art and audio, listed at the end.

Per-feature briefs: [identity](assets/identity.md), [Chapter Two and mentors](assets/mentor.md), [skills](assets/skill.md), [Force powers](assets/force.md), [saber](assets/saber.md), [Sith Acolyte and Poacher Captain](assets/enemy.md).

## Summary (Milestone 2)

| Category | Count | Status | Generator |
|---|---|---|---|
| Entity skins (settlers, mentors, acolyte variants, Captain) | 12 PNG | placeholder | `gen_textures_identity.py`, `gen_textures_mentor.py`, `gen_textures_enemy.py` |
| GUI sprites (34 skill node icons, 3 faction icons, 2 mentor portraits) | 39 PNG | placeholder | `gen_textures_skill.py`, `gen_textures_identity.py`, `gen_textures_mentor.py` |
| Item textures (ledger, vibroblade, blaster pistol, 2 spawn eggs) | 5 PNG | placeholder | `gen_textures_mentor.py`, `gen_textures_enemy.py` |
| Particle textures (11 new particle types) | 30 PNG | placeholder | force, saber, identity and mentor scripts |
| **Textures total (Milestone 2)** | **86 PNG** | placeholder | |
| Sounds | 55 new events, 62 OGG files (96 events, 117 files in total) | placeholder | `gen_sounds.py` with the recipe modules in `tools/assets/sounds/` |
| Structure templates | 3 NBT (`story/jedi_camp`, `sith_camp`, `raider_camp`) | placeholder | `gen_structures_story.py` |
| Item definitions and models (5 items) | 10 JSON | flat models; vibroblade and pistol are placeholders (see below) | hand-written / scripts |
| Particle sprite lists | 11 JSON (27 in total) | final | scripts |
| Skill trees, dialogues, loot tables, tags, damage types | JSON | final content, tuning may change | hand-written |

The new entities (settler, mentors, acolyte, Captain, thrown saber) have their models in code (`SettlerModel`, `MentorModel`, `AcolyteModel`, `CaptainModel`, `ThrownSaberFx`); only their skins are files.

## Generator scripts (Milestone 2)

| Script | Writes | Needs |
|---|---|---|
| `python3 tools/assets/gen_textures_identity.py` | Settler skins (free and bound), rope fibre sprites, faction icons | Pillow |
| `python3 tools/assets/gen_textures_mentor.py` | Mentor skins and portraits, ledger icon, `voice_warm`, `voice_dust`, `ash_mote` sprites | Pillow |
| `python3 tools/assets/gen_textures_skill.py` | The 34 skill node icons | Pillow |
| `python3 tools/assets/gen_textures_force.py` | Adds `pull_streak`, `jump_trail`, `lightning_segment`, `lightning_spark`, `ward_glint` and their particle JSONs | numpy, Pillow |
| `python3 tools/assets/gen_textures_saber.py` | Adds `blade_glint`, `lunge_streak` and the `lightsaber` item definition's thrown-state wrapper | Pillow |
| `python3 tools/assets/gen_textures_enemy.py` | Adds acolyte (3 variants) and Captain skins, vibroblade, pistol, 2 spawn eggs | Pillow |
| `python3 tools/assets/gen_structures_story.py` | `D/structure/story/*.nbt` (uses `gen_structures.py`) | Python 3 only |
| `python3 tools/assets/gen_sounds.py --m2` | Every Milestone 2 OGG from `tools/assets/sounds/{identity,story,skill,force,saber,enemy}.py`; `--feature <name>` renders one module. Events and subtitles come from `tools/assets/sounds/events_m2.json`. | numpy, `ffmpeg` with libvorbis |

## Identity: Frontier Settler, factions

| Path | What it is | Status |
|---|---|---|
| `A/textures/entity/frontier_settler_0..2.png` | Settler skins, 3 variants (64x64, tunic and hood layers) | placeholder |
| `A/textures/entity/frontier_settler_0..2_bound.png` | Same skins with rope while captive | placeholder |
| `A/textures/particle/rope_fibre_0..2.png` | Rope fibres when captives are cut loose (8x8) | placeholder |
| `A/textures/gui/sprites/identity/faction_{jedi_order,sith_empire,frontier}.png` | Faction icons (9x9) | placeholder |
| `A/particles/rope_fibre.json` | Particle sprite list | final |
| `D/dialogue/yield.json`, `captives.json` | Yield and captives dialogues | final |
| `D/loot_table/gameplay/settler_thanks.json`, `captive_payment.json` | Freed settlers' thanks, captives' payment | final (tuning) |
| `D/tags/damage_type/force_harm.json` | Force damage that counts as cruelty and is reduced on captives | final |

## Chapter Two: mentors, ledger, story sites

| Path | What it is | Status |
|---|---|---|
| `A/textures/entity/jedi_mentor.png`, `sith_mentor.png` | Master Ilyra Saelen, Overseer Teyvar Sorn (64x64 plus robe, skirt, hood, cape boxes) | placeholder |
| `A/textures/gui/sprites/portrait/jedi_mentor.png`, `sith_mentor.png` | Dialogue and Standing portraits (32x32) | placeholder |
| `A/textures/item/poachers_ledger.png` | Poachers' Ledger (16x16) | placeholder |
| `A/textures/particle/voice_warm_0..2.png`, `voice_dust_0..2.png` | Calling variant 2 (the two voices), mentor departure puff | placeholder |
| `A/textures/particle/ash_mote_0..2.png` | Calling variant 3 (smoke on the ridge) | placeholder |
| `A/particles/voice_warm.json`, `voice_dust.json`, `ash_mote.json` | Particle sprite lists | final |
| `A/items/poachers_ledger.json`, `A/models/item/poachers_ledger.json` | Item definition and model | final |
| `D/structure/story/jedi_camp.nbt`, `sith_camp.nbt` | 7x5x7 mentor camps | placeholder |
| `D/structure/story/raider_camp.nbt` | 13x6x13 raider camp (signal fire, tents, binding posts, smashed cart) | placeholder |
| `D/dialogue/crossing_jedi.json`, `crossing_sith.json`, `mentor_jedi.json`, `mentor_sith.json`, `brenn.json`, `independent.json` | Story dialogues | final (writing may be revised) |
| `D/tags/block/story_ground.json`, `built_blocks.json` | Site checks | final |

## Skills

| Path | What it is | Status |
|---|---|---|
| `A/textures/gui/sprites/skill/<node>.png` | One 16x16 icon per node, 34 in total (13 Universal, 9 Lightsaber, 6 Jedi, 6 Sith). Locked, dormant and refused looks are drawn in code. | placeholder |
| `D/skill_tree/universal.json`, `saber.json`, `jedi.json`, `sith.json` | The four trees | final content |

## Force powers

| Path | What it is | Status |
|---|---|---|
| `A/textures/particle/pull_streak_0..2.png` | Force Pull dust streak (16x16) | placeholder |
| `A/textures/particle/jump_trail_0..1.png` | Force Jump motes (8x8) | placeholder |
| `A/textures/particle/lightning_segment_0..3.png` | One piece of a Lightning arc (16x16) | placeholder |
| `A/textures/particle/lightning_spark_0..1.png` | Fingertip crackle, contact and disarm sparks (8x8) | placeholder |
| `A/textures/particle/ward_glint_0..3.png` | Serene Ward glint (8x8) | placeholder |
| `A/particles/pull_streak.json`, `jump_trail.json`, `lightning_segment.json`, `lightning_spark.json`, `ward_glint.json` | Particle sprite lists | final |
| `D/damage_type/force_lightning.json`, `force_impact.json` | Damage types | final |

Force Heal reuses `force_mote`; jump debris uses vanilla block particles.

## Saber combat and Saber Throw

| Path | What it is | Status |
|---|---|---|
| `A/textures/particle/blade_glint_0..1.png` | NPC wind-up tell along the blade (8x8) | placeholder |
| `A/textures/particle/lunge_streak.png` | Lunge path streak (8x8) | placeholder |
| `A/particles/blade_glint.json`, `lunge_streak.json` | Particle sprite lists | final |
| `A/items/lightsaber.json` (changed) | Shows `minecraft:empty` while the saber carries `oldrepublic:saber_thrown` | final |
| `D/damage_type/thrown_saber.json` | Damage type | final |

The thrown saber has no texture or model of its own: `ThrownSaberFx` draws the blade in code.

## Sith Acolyte and Poacher Captain

| Path | What it is | Status |
|---|---|---|
| `A/textures/entity/sith_acolyte_acolyte.png`, `sith_acolyte_brenn.png`, `sith_acolyte_deserter.png` | Acolyte skins: generic, Brenn Ostrak, the raider camp's deserter | placeholder |
| `A/textures/entity/poacher_captain.png` | Captain Hessa Dray | placeholder |
| `A/textures/item/vibroblade.png`, `blaster_pistol.png` | Render-only held items (16x16) | placeholder |
| `A/textures/item/sith_acolyte_spawn_egg.png`, `poacher_captain_spawn_egg.png` | Spawn eggs | placeholder |
| `A/items/vibroblade.json`, `blaster_pistol.json` and their `A/models/item/*.json` | Flat `item/handheld` models | placeholder (`PistolGlowLayer` assumes the sprite's muzzle position) |
| `A/items/sith_acolyte_spawn_egg.json`, `poacher_captain_spawn_egg.json` and models | Spawn egg definitions | final |
| `D/loot_table/entities/sith_acolyte.json`, `poacher_captain.json` | Nuggets plus a small hilt-part chance | final (tuning) |
| `D/tags/entity_type/push_resistant.json` (changed) | Adds both entities | final |

## Sounds (Milestone 2)

All synthesised placeholders, registered in `A/sounds.json` with subtitles. Loops are marked (loop).

| Group | Events (files) |
|---|---|
| identity | `identity.alignment_light`, `identity.alignment_dark`, `identity.rank_up`, `dialogue.open`, `dialogue.select`, `settler.hurt` (2), `settler.death`, `settler.freed` |
| story | `story.two_voices` (loop), `story.lesson_complete`, `story.duel_begin`, `story.duel_end` |
| skill | `skill.learn`, `skill.slot`, `skill.respec` |
| force | `force.pull.cast` (2), `force.pull.drag`, `force.pull.catch`, `force.pull.disarm`, `force.jump.gather` (loop), `force.jump.launch`, `force.jump.land` (soft, heavy), `force.jump.slam`, `force.jump.glide` (loop), `force.heal.start`, `force.heal.loop` (loop), `force.heal.end`, `force.heal.ward`, `force.heal.interrupt`, `force.lightning.charge`, `force.lightning.loop` (loop), `force.lightning.impact`, `force.lightning.release` |
| saber | `saber.finisher`, `saber.heavy_windup`, `saber.heavy_strike`, `saber.lunge`, `saber.form_change`, `saber.throw`, `saber.throw_loop` (loop), `saber.throw_catch`, `saber.npc_windup`, `saber.lightning_block` |
| enemy | `acolyte.shove_windup`, `acolyte.shove`, `acolyte.hurt` (2), `acolyte.death`, `acolyte.yield`, `captain.heavy_windup`, `captain.hurt` (2), `captain.death`, `captain.yield`, `vibroblade.swing` (2), `vibroblade.hit`, `blaster.pistol_fire` (2) |

## Still needed from a real artist or sound designer (Milestone 1 and 2)

Nothing is missing as a file, but nothing is final art. In rough order of how much it shows in play:

1. **Character skins:** both mentors (and their portraits), Brenn, the acolyte and deserter, Captain Hessa Dray, three settlers (free and bound), the poacher. These are named story characters on screen for long stretches; the generated skins are flat.
2. **Lightsaber 3D models and the blade glow** (M1), and the thrown-saber look, which is drawn in code from the same blade.
3. **Story structures:** the two mentor camps and the raider camp (M2), the crystal cave pieces (M1). Briefs in `docs/assets/mentor.md` and `docs/assets/world.md`.
4. **Voices:** none exist. Settler, acolyte, Captain and poacher vocals are synthesised grunts; the Two Voices dream and every story line are subtitles only (no voice acting is planned for M2).
5. **All 96 sound events**, especially the loops (saber hum, Lightning, Heal, Jump gather and glide, thrown saber, Two Voices bed). Rules in `AUDIO_BRIEF.md`.
6. **Skill icons** (34), faction icons, held items (vibroblade and blaster pistol still have flat sprites, not 3D models), spawn eggs and the ledger.
7. **Particle sprites** (M1 and M2), lowest priority: they are small and tinted in code.
