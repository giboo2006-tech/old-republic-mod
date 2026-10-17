# Asset manifest (Milestones 1 to 3)

Every texture, sound and structure in the mod is an original **placeholder** produced by a script in `tools/assets/`. Nothing is copied from films, games or other mods. This file lists every asset in one place; the per-feature files hold the full briefs for the final artists and sound designers:

- [Force Push](assets/force.md)
- [Quest and HUD](assets/quest.md)
- [Lightsaber and workbench](assets/saber.md)
- [Crystal Poacher and blaster](assets/enemy.md)
- [Crystal caves and the Trial of Reflection](assets/world.md)
- [HUD and screens (UI kit)](assets/ui.md)
- [Audio](AUDIO_BRIEF.md)

Milestone 2 and Milestone 3 assets are listed in their own parts at the end, [Milestone 2 additions](#milestone-2-additions) and [Milestone 3 additions](#milestone-3-additions). The sections before them describe Milestone 1 and are unchanged, except that the Milestone 1 sound count (41 events, 55 files) is now 217 events and 240 files in total.

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

---

# Milestone 3 additions

Same rules as above: every file is an original **placeholder** made by a script in `tools/assets/`, nothing is copied. Two exceptions are marked: the Reliquary templates have their final layout (with placeholder blocks), and the singing crystal is final art for Milestone 3. Every path in this section was checked against the repository, and every texture, particle sprite list and sound that a model, JSON or `sounds.json` entry references exists: **no referenced Milestone 3 asset is missing.** What is missing is final art and audio, listed at the end under "Missing assets (Milestone 3)".

Per-feature briefs: [world](assets/world.md), [Chapter Three](assets/quest.md), [missions](assets/mission.md), [holocrons](assets/holocron.md), [Force](assets/force.md), [skills](assets/skill.md), [troopers](assets/enemy.md), [armour](assets/equipment.md), [boss and sentinels](assets/boss.md), [saber](assets/saber.md).

## Summary (Milestone 3)

| Category | Count | Status | Generator |
|---|---|---|---|
| Block textures (W1 sites, puzzle blocks, pedestals, terminal, resources) | 69 PNG | placeholder | `gen_textures_world.py` |
| Block textures (Reliquary heart, seals, pylons, hoard plinth) | 16 PNG | placeholder | `gen_textures_boss.py` |
| Entity textures (8 trooper skins, grenade, 10 armour layers, 4 robe models, 4 sentinel, 4 Vasch, banner and shield) | 33 PNG | placeholder | `gen_textures_trooper.py`, `gen_textures_enemy.py`, `gen_textures_equipment.py`, `gen_textures_boss.py`, `gen_textures_world.py` |
| GUI sprites (21 skill node icons, Sense pip, 12 gatekeeper portraits, gatekeeper figure, 2 officer portraits, Vasch portrait, Republic faction icon) | 39 PNG | placeholder | `gen_textures_skill.py`, `gen_textures_holocron.py`, `gen_textures_quest.py`, `gen_textures_boss.py`, `gen_textures_identity.py` |
| Item textures (18 armour pieces, 12 holocron sheets, 3 spawn eggs, 11 other items) | 44 PNG | placeholder; the singing crystal is final art for M3 | `gen_textures_equipment.py`, `gen_textures_holocron.py`, `gen_textures_m3items.py`, `gen_textures_quest.py`, `gen_textures_saber.py`, `gen_textures_enemy.py`, `gen_textures_boss.py` |
| Particle textures (23 new particle types) | 63 PNG | placeholder | world, mentor, force, holocron, enemy and boss scripts |
| **Textures total (Milestone 3)** | **264 PNG** (458 in total) | placeholder | |
| Sounds | 121 new events, 123 OGG files (217 events, 240 files in total) | synthesised placeholders | `gen_sounds.py --m3` with `tools/assets/sounds/{world,story,force,holocron,enemy,equipment,boss,saber}.py` |
| Structure templates | 30 NBT with 30 `site_anchors` sidecars | 28 placeholder; the 2 Reliquary templates final layout | `gen_structures_m3.py`, `gen_structures_story_m3.py`, `gen_structures_reliquary.py` |
| Blockstates, block models, item definitions and models | 32 blockstates, 66 block models, 65 item definitions, 75 item models | final (flat or cube models) | scripts and hand-written |
| Equipment assets (`A/equipment/*.json`) | 6 JSON | final | `gen_textures_equipment.py` |
| Particle sprite lists | 23 JSON (50 in total) | final | scripts |
| Worldgen (5 structures, 14 template pools, 3 processor lists, 2 structure sets) | 24 JSON | final (tuning may change) | `gen_structures_m3.py`; structure sets hand-written |
| Skill tree, dialogues, missions, recipes, loot tables, damage types, tags, banner pattern | JSON | final content, tuning may change | hand-written |

The new entities (troopers, the frag grenade, the Ruin Sentinel, Orren Vasch, the gatekeeper figure, the rising holocron) have their models in code; only their skins are files.

## Generator scripts (Milestone 3)

| Script | Writes | Needs |
|---|---|---|
| `python3 tools/assets/gen_textures_m3items.py` | Phase 0 shared items: `durasteel_plate`, `lumen_thread`, `grave_silk`, both commendations, `holocron_shard` (textures, item definitions, models) | Pillow |
| `python3 tools/assets/gen_textures_world.py` | Adds the 69 W1 block textures, the 7 site particle sprites and the Republic chevron banner and shield (Milestone 1 output stays byte-identical) | Pillow |
| `python3 tools/assets/gen_structures_m3.py` | The 21 site templates, their `site_anchors` sidecars, and the 5 structures, 14 template pools and 3 processor lists; it checks the redstone, the jigsaw assembly and the resource counts | Python 3 only |
| `python3 tools/assets/gen_structures_story_m3.py` | `D/structure/story/couriers_fall.nbt`, the 6 mission camps and their sidecars | Python 3 only |
| `python3 tools/assets/gen_textures_quest.py` | Adds `archive_record`, `fence_book` and the two officer portraits | Pillow |
| `python3 tools/assets/gen_textures_mentor.py` | Adds `falling_ember` | Pillow |
| `python3 tools/assets/gen_textures_identity.py` | Adds the Republic faction icon | Pillow |
| `python3 tools/assets/gen_textures_force.py` | Adds `sense_mote`, `sense_stream`, `lift_shimmer` | numpy, Pillow |
| `python3 tools/assets/gen_textures_holocron.py` | The 3 holocrons (textures, item definitions, models), 12 gatekeeper portraits, the gatekeeper figure, `holocron_mote`, `projection_band` | Pillow |
| `python3 tools/assets/gen_textures_skill.py` | Adds the 21 M3 node icons and `sense_pip` | Pillow |
| `python3 tools/assets/gen_textures_trooper.py` | The 8 trooper skins | Pillow |
| `python3 tools/assets/gen_textures_enemy.py` | Adds the frag grenade, 2 trooper spawn eggs, `kolto_mist`, `vent_steam`, `grenade_smoke`, `flare_ember` | Pillow |
| `python3 tools/assets/gen_textures_equipment.py` | 18 armour icons, item definitions and models, 10 armour layers, 4 robe model textures, `A/equipment/*.json` | Pillow |
| `python3 tools/assets/gen_textures_boss.py` | Sentinel and Vasch skins, the Vasch portrait, the sentinel spawn egg, the Reliquary blocks, the 6 boss and sentinel particles | Pillow |
| `python3 tools/assets/gen_structures_reliquary.py` | `D/structure/boss/reliquary_surface.nbt`, `reliquary_hall.nbt` and their sidecars | Python 3 only |
| `python3 tools/assets/gen_textures_saber.py` | Adds `pommel_archivist`, `emitter_reliquary` and the singing crystal | Pillow |
| `python3 tools/assets/gen_sounds.py --m3` | Every Milestone 3 OGG. Events, subtitles, `loop`, `stream` and `volume` come from `tools/assets/sounds/events_m3.json`; `--feature <name>` renders one module, `--placeholders` lists ids still on generic placeholder audio (none now) | numpy, `ffmpeg` with libvorbis |
| `python3 tools/assets/gen_test_fixtures_m3.py` | The game test fixtures in `src/gametest/resources/data/oldrepublic_test/` (`arena_24x7x24`, `urn_fixture`, `seal_fixture`, `pedestal_fixture`, `terminal_fixture`, `anchor_fixture`); never shipped in the mod jar | Python 3 only |

## World: sites, puzzle blocks and outposts (W1)

| Path | What it is | Status |
|---|---|---|
| `A/textures/block/ruin_stone{,_carved,_cracked,_mossy}.png`, `tomb_stone{,_carved,_cracked}.png`, `wreck_plating{,_scorched}.png`, `wreck_conduit.png`, `republic_panel.png`, `imperial_panel.png` | Decor blocks of the ruins, tombs, wrecks and outposts | placeholder |
| `A/textures/block/veiled_stone_{ruin,tomb,wreck}_revealed.png`, `counterweight_stone_*`, `step_stone*`, `echo_urn_*`, `urn_keeper_*`, `pull_anchor_*`, `collapsed_rubble.png`, `puzzle_seal_{ruin,tomb,wreck}.png`, `tomb_vent_*` | Puzzle blocks and vents, with their state looks (strain, tell, puff, solved) | placeholder |
| `A/textures/block/holocron_pedestal_{jedi,sith,wayfarer}_{side,side_ready,top}.png` | Holocron pedestals | placeholder |
| `A/textures/block/bounty_terminal_*.png`, `site_spawner.png`, `echo_cairn_{stone,satchel}.png` | Bounty terminal, spawner, echo cairn | placeholder |
| `A/textures/block/lumen_moss.png`, `funerary_urn_{side,top}.png`, `wreck_salvage{,_top}.png` | Harvestable resources | placeholder |
| `A/blockstates/*.json`, `A/models/block/*.json`, `A/items/*.json` and `A/models/item/*.json` of the 26 W1 blocks with items, plus `echo_cairn` and `site_spawner` (no item) | 28 blockstates, 56 block models, 26 item definitions | final |
| `A/textures/entity/banner/republic_chevron.png`, `shield/republic_chevron.png`, `D/banner_pattern/republic_chevron.json` | Republic banner and shield pattern | placeholder |
| `A/textures/particle/light_mote_*`, `leaf_fall_*`, `tomb_dust_*`, `veil_dust_*`, `vent_grit_*`, `miasma_*`, `conduit_spark_*` (19 sprites) | Site particles | placeholder |
| `A/particles/light_mote.json`, `leaf_fall.json`, `tomb_dust.json`, `veil_dust.json`, `vent_grit.json`, `miasma.json`, `conduit_spark.json` | Particle sprite lists | final |
| `D/structure/jedi_ruin/*.nbt` (5), `sith_tomb/*.nbt` (7), `wreck_field/*.nbt` (4), `outpost/{republic,imperial}_post.nbt`, `site/echo_shrine_{jedi,sith,wayfarer}.nbt` | 21 site templates | placeholder |
| `D/site_anchors/<same paths>.json` | Anchor sidecars (pedestal, guard, spawner, cache cells) | final |
| `D/worldgen/structure/*.json` (5), `template_pool/**` (14), `processor_list/*_weathering.json` (3) | Worldgen | generated, final |
| `D/worldgen/structure_set/ruins.json`, `outposts.json` | Placement: ruins spacing 32, separation 12; outposts 40, 16 | final (tuning) |
| `D/tags/worldgen/biome/has_structure/*.json` (5), `D/tags/worldgen/structure/*.json` (5), `D/tags/block/force_reactive.json` | Tags | final |
| `D/loot_table/chests/{jedi_ruin,sith_tomb,wreck_field,force_cache}.json`, `D/loot_table/blocks/*.json` (15) | Site caches and block drops | final (tuning) |
| `D/damage_type/tomb_miasma.json`, `urn_dust.json` | Vent and wrong-urn damage | final |

## Phase 0 shared items (lead)

| Path | What it is | Status |
|---|---|---|
| `A/textures/item/durasteel_plate.png`, `lumen_thread.png`, `grave_silk.png` | Materials from sites, sentinels and the Collector | placeholder |
| `A/textures/item/republic_commendation.png`, `imperial_commendation.png` | Commendations (currency for trooper armour) | placeholder |
| `A/textures/item/holocron_shard.png` | Holocron shard | placeholder |
| `A/items/*.json`, `A/models/item/*.json` of these 6 | Flat item models | final |

## Chapter Three and missions (W2)

| Path | What it is | Status |
|---|---|---|
| `A/textures/item/archive_record.png`, `fence_book.png` and their item definitions and models | Mission items | placeholder |
| `A/textures/gui/sprites/portrait/officer_republic.png`, `officer_imperial.png` | Officer portraits | placeholder |
| `A/textures/gui/sprites/identity/faction_republic.png` | Republic faction icon (9x9) | placeholder |
| `A/textures/particle/falling_ember_0..3.png`, `A/particles/falling_ember.json` | Courier's Fall embers | placeholder (list final) |
| `D/structure/story/couriers_fall.nbt`, `D/site_anchors/story/couriers_fall.json` | Courier's Fall | placeholder |
| `D/structure/mission/{patrol_ambush,smuggler_cache,fence_camp,survey_post,raider_camp_small,skirmish_line}.nbt` and their sidecars | Mission camps | placeholder |
| `D/mission/*.json` (10) | Story missions, contracts and duties | final content |
| `D/dialogue/c3_*.json`, `officer_*.json`, `pathfinder.json`, `reliquary_heart.json`, `yield_border.json`, `yield_intercept.json`, `r1_leave_confirm.json`, `i1_burn_confirm.json` (12) | Chapter Three, officer and mission dialogues | final (writing may be revised) |
| `D/loot_table/gameplay/{echo_cache,frontier_contract,r1_supply,wayfarer_cache}.json` | Rewards | final (tuning) |

## Holocrons, Force and skills (W3)

| Path | What it is | Status |
|---|---|---|
| `A/textures/item/{jedi,sith,wayfarer}_holocron{,_seams,_dark,_model}.png` | Holocron icons, seam glow, dark (broken) look and model sheets (12) | placeholder |
| `A/items/{jedi,sith,wayfarer}_holocron.json` and 12 item models | Holocron item definitions | final |
| `A/textures/gui/sprites/portrait/gatekeeper_*.png` (12) | Gatekeeper portraits | placeholder |
| `A/textures/gui/sprites/portrait/gatekeeper_figure.png` | Skin of the projected gatekeeper figure | placeholder |
| `A/textures/gui/sprites/skill/<node>.png` (21), `sense_pip.png` | Exploration, holocron and rank 3 node icons; the Sense HUD pip | placeholder |
| `A/textures/particle/sense_mote_0..3`, `sense_stream_0..1`, `lift_shimmer_0..3`, `holocron_mote`, `projection_band` | Sense, Lift and holocron particles (12 sprites) | placeholder |
| `A/particles/sense_mote.json`, `sense_stream.json`, `lift_shimmer.json`, `holocron_mote.json`, `projection_band.json` | Particle sprite lists | final |
| `D/skill_tree/exploration.json` | The fifth tree | final content |
| `D/dialogue/holocron_*.json` (15), `archive_hoard.json` | Gatekeeper and Archive dialogues | final (writing may be revised) |

## Troopers and armour (W4)

| Path | What it is | Status |
|---|---|---|
| `A/textures/entity/trooper/republic_{rifleman,medic,sergeant,officer}.png`, `imperial_{rifleman,grenadier,sergeant,officer}.png` | Trooper skins | placeholder |
| `A/textures/entity/frag_grenade.png` | Frag grenade | placeholder |
| `A/textures/item/republic_trooper_spawn_egg.png`, `sith_trooper_spawn_egg.png` | Spawn eggs | placeholder |
| `A/textures/item/republic_trooper_*.png`, `sith_trooper_*.png` (4 each), `jedi_{hood,robe,trousers,boots}.png`, `jedi_knight_robe.png`, `sith_{hood,robe,trousers,boots}.png`, `sith_lord_robe.png` | 18 armour icons | placeholder |
| `A/textures/entity/equipment/humanoid/*.png` (6), `humanoid_leggings/*.png` (4) | Vanilla-style armour layers | placeholder |
| `A/equipment/{republic_trooper,sith_trooper,jedi_robes,sith_robes,jedi_knight,sith_lord}.json` | Equipment assets | final |
| `A/textures/entity/robe/{jedi_robes,sith_robes,jedi_knight,sith_lord}.png` | Robe custom model textures (hood, mantle, sash) | placeholder |
| `A/textures/particle/kolto_mist_*`, `vent_steam_*`, `grenade_smoke_*`, `flare_ember_*` (12 sprites) and their 4 `A/particles/*.json` | Trooper particles | placeholder (lists final) |
| `D/recipe/*.json` (16) | Armour recipes (cost commendations) | final (tuning) |
| `D/tags/item/repairs/*.json` (3), `jedi_robe_cloth.json`, `sith_robe_cloth.json`, vanilla `enchantable/*` (7) | Repair and enchanting tags | final |
| `D/loot_table/entities/republic_trooper.json`, `sith_trooper.json`, `D/damage_type/frag_grenade.json` | Drops (never armour or commendations) and grenade damage | final (tuning) |

## Boss, sentinels and saber (W5)

| Path | What it is | Status |
|---|---|---|
| `A/textures/entity/ruin_sentinel/{watcher,tomb}{,_lumen}.png`, `A/textures/item/ruin_sentinel_spawn_egg.png` | Ruin Sentinel skins, lumen layers, spawn egg (5) | placeholder |
| `A/textures/entity/orren_vasch/{base,unkept,echo,echo_unkept}.png`, `A/textures/gui/sprites/portrait/orren_vasch.png` | Orren Vasch skins and portrait (5) | placeholder |
| `A/textures/block/reliquary_heart_*`, `reliquary_seal{,_vault}.png`, `resonance_pylon_*`, `hoard_plinth_*` (16) with 4 blockstates, 10 models, 4 items | Reliquary blocks | placeholder |
| `A/textures/particle/ward_haze_*`, `ward_current_*`, `surge_dust_*`, `lash_seam_*`, `echo_smoke_*`, `lumen_spark_*` (16 sprites) and their 6 `A/particles/*.json` | Boss and sentinel particles | placeholder (lists final) |
| `D/structure/boss/reliquary_surface.nbt`, `reliquary_hall.nbt`, `D/site_anchors/boss/*.json` | Reliquary templates and anchors | final layout, placeholder blocks |
| `A/textures/item/pommel_archivist.png`, `emitter_reliquary.png` with 6 models | Hilt parts | placeholder |
| `A/textures/item/singing_crystal.png` with 3 JSON | Singing crystal | final art for M3 |
| `D/damage_type/{archive_debris,archive_grip,collector_surge,repelling_palm,resonant_lash}.json`, `D/loot_table/entities/ruin_sentinel.json` | Boss damage types, sentinel drops | final (tuning) |

## Sounds (Milestone 3)

All 121 events have a synthesised recipe in the module of their feature, registered in `A/sounds.json` with subtitles. `site.discovered` has 3 variants. Loops are marked (loop); streamed beds and music (stream). Subtitles and final briefs are in `AUDIO_BRIEF.md`.

| Group | Events |
|---|---|
| world (25) | `ambient.jedi_ruin`, `ambient.sith_tomb`, `ambient.wreck`, `ambient.outpost` (loop, stream), `ambient.ruin_chime`, `ambient.tomb_whisper`, `ambient.wreck_creak`, `puzzle.*` (15), `vent.hiss`, `vent.puff`, `terminal.use` |
| story (13) | `site.discovered` (3), `story.ones_that_sing` (loop, stream), `story.calling_holocron`, `story.calling_reliquary`, `story.calling_fall`, `story.collector_fallen`, `story.rank_three`, `story.cairn_touch`, `story.blade_touch`, `story.tablet_break`, `mission.accept`, `mission.update`, `mission.complete` |
| force (7) | `force.sense.cast`, `force.sense.reveal`, `force.sense.danger`, `force.lift.start`, `force.lift.loop` (loop), `force.lift.slam`, `force.lift.hurl` |
| holocron (12) | `holocron.rise`, `open`, `gatekeeper_form`, `gatekeeper_fade`, `hum` (loop), `seal`, `break`, `listen`, `echo`, `archive_return`, `archive_keep`, `unmake` |
| enemy (16) | `trooper.republic.charge`, `trooper.republic.fire`, `trooper.imperial.charge`, `trooper.imperial.fire`, `trooper.vent`, `trooper.kolto`, `trooper.tend`, `trooper.stand`, `trooper.rally`, `trooper.flare`, `trooper.radio`, `trooper.hurt`, `trooper.death`, `grenade.arm`, `grenade.fuse`, `grenade.burst` |
| equipment (3) | `armour.equip_trooper`, `armour.equip_robe`, `armour.set_bonus` |
| boss (44) | `sentinel.*` (8), `boss.collector.*` (26, with `ward_hum` (loop)), `pylon.*` (5, with `pylon.sing` (loop)), `music.collector.{archivist,singing_wall,unkept}` (loop, stream), `ambient.reliquary` (loop, stream), `hoard.open` |
| saber (1) | `saber.hum.singing` (loop) |

## Missing assets (Milestone 3)

Nothing is missing as a file. Every Milestone 3 asset below is a generated placeholder that needs final work, in rough order of how much it shows in play:

- **Character skins and portraits**
  - Orren Vasch: three looks (base, Unkept, echo and Unkept echo) and his portrait.
  - Troopers: 8 skins (3 Republic roles, 3 Imperial roles, 2 officers) and the 2 officer portraits.
  - Ruin Sentinels: the Watcher and Tomb skins with their lumen layers.
  - Gatekeepers: the projected figure's skin and 12 portraits.
- **Armour**
  - 10 armour layers (6 body, 4 leggings) and the 18 armour icons.
  - 4 robe custom model textures (Jedi and Sith robes, the Knight and Lord robes).
- **Structures**
  - 21 site templates: Jedi Ruin (5), Sith Tomb (7), Wreck Field (4), the two outposts, the three Echo Shrines.
  - Courier's Fall and the 6 mission camps.
  - The Reliquary: the layout is final, its blocks and textures are not.
- **Blocks**
  - 69 site block textures: decor stones and panels, puzzle blocks, pedestals, the bounty terminal, the spawner, the echo cairn and the three resources.
  - 16 Reliquary block textures: the heart, the seals, the resonance pylons and the hoard plinth.
  - The Republic chevron banner and shield pattern.
- **Items**
  - 3 holocrons (icons, seams, dark and model sheets) and `holocron_shard` (still the Phase 0 art).
  - Both commendations (Phase 0 art), `durasteel_plate`, `lumen_thread`, `grave_silk`, `archive_record`, `fence_book`.
  - The hilt parts `pommel_archivist` and `emitter_reliquary`, the frag grenade, 3 spawn eggs.
  - The singing crystal is final art for M3 and is not on this list.
- **GUI**
  - 21 skill node icons and the Sense pip.
  - The Republic faction icon.
- **Sounds**
  - All 121 new events (123 files) are synthesised placeholders. The ones heard most: the five ambience beds, the three boss music loops, the Chapter Three dream bed, the trooper rifles and grenade, and the loops `force.lift.loop`, `holocron.hum`, `boss.collector.ward_hum`, `pylon.sing` and `saber.hum.singing`. Rules in `AUDIO_BRIEF.md`.
- **Voices**
  - None exist. Vasch, the gatekeepers, the officers and the trooper barks are subtitles only (barks with a radio click). Vasch's brief ("a tired older man") needs a voice actor if voices are added later.
- **Particles** (lowest priority: small and tinted in code)
  - 63 sprites for the 23 new particle types.
