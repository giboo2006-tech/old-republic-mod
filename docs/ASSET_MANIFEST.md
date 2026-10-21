# Asset manifest (Milestones 1 to 4)

Every texture, sound and structure in the mod is an original **placeholder** produced by a script in `tools/assets/`. Nothing is copied from films, games or other mods. This file lists every asset in one place; the per-feature files hold the full briefs for the final artists and sound designers:

- [Force Push](assets/force.md)
- [Quest and HUD](assets/quest.md)
- [Lightsaber and workbench](assets/saber.md)
- [Crystal Poacher and blaster](assets/enemy.md)
- [Crystal caves and the Trial of Reflection](assets/world.md)
- [HUD and screens (UI kit)](assets/ui.md)
- [Audio](AUDIO_BRIEF.md)

Milestone 2, 3 and 4 assets are listed in their own parts at the end, [Milestone 2 additions](#milestone-2-additions), [Milestone 3 additions](#milestone-3-additions) and [Milestone 4 additions](#milestone-4-additions). The sections before them describe Milestone 1 and are unchanged, except that the Milestone 1 sound count (41 events, 55 files) is now 356 events and 380 files in total.

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
| `A/textures/particle/push_streak_0..2.png` | Directional dust streak, 3 variants (16x16); unused since October 2026 | placeholder |
| `A/textures/particle/push_impact_0..3.png` | Dust puff where a push lands, 4 frames (32x32); unused since October 2026 | placeholder |
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

Brief: [assets/saber.md](assets/saber.md). Crystal textures are white and tinted in code. Since October 2026 the blade has no texture or model: `SaberBladeRenderer` draws it in code, `BladeTrails` its swing trail and `SaberSparks` its sparks.

| Path | What it is | Status |
|---|---|---|
| `A/textures/item/lightsaber_icon_hilt.png` | Inventory hilt (16x16) | placeholder |
| `A/textures/item/lightsaber_icon_blade.png` | Inventory blade layer (16x16) | placeholder |
| `A/textures/item/lightsaber_hilt.png` | Material swatches for the 3D hilt | placeholder |
| `A/textures/item/kyber_crystal.png` | Crystal body (16x16) | placeholder |
| `A/textures/item/kyber_crystal_glint.png` | Crystal highlight layer | placeholder |
| `A/textures/item/emitter_guardian.png`, `emitter_duelist.png` | Emitter icons | placeholder |
| `A/textures/item/grip_wrapped.png`, `grip_ridged.png` | Grip icons | placeholder |
| `A/textures/item/pommel_weighted.png`, `pommel_balanced.png` | Pommel icons | placeholder |
| `A/textures/block/lightsaber_workbench_{top,side,front,metal,lamp}.png` | Workbench faces | placeholder |
| `A/textures/gui/container/lightsaber_workbench.png` | Workbench screen (230x200) | placeholder |
| `A/textures/particle/saber_spark_0..1.png` | Saber sparks (8x8); the particle stays registered, but the sparks are now drawn in code | unused |
| `A/textures/particle/contact_flash.png` | One-tick contact flash (8x8); likewise replaced by a burst of light drawn in code | unused |
| `A/items/lightsaber.json` | Item definition: flat icon in GUIs; elsewhere the 3D hilt and the blade drawn in code (the `oldrepublic:saber_blade` special model) | final |
| `A/models/item/lightsaber/base.json` | Shared parent with display transforms: third person tilted 22° at 0.68 scale; first person is posed in code (`SaberFirstPerson`) | placeholder, tuned from CI screenshots, **needs hand-play** |
| `A/models/item/lightsaber/{emitter,grip,pommel}_*.json` | 3D hilt parts (6) | placeholder |
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
| `saber.ignite` | `saber/ignite` | Ignition: a snap-hiss and the hum rushing in on a quick upward glide |
| `saber.retract` | `saber/retract` | Retraction: the hum diving and fading under a falling hiss |
| `saber.hum` | `saber/hum` | Idle hum (loop): a warm motor hum and a hard electrical buzz beating slowly; code lifts its pitch and level with the blade's motion |
| `saber.swing` | `saber/swing1..4` | Swing passes: the hum carried past the ear (Doppler bend, swell, moving comb filter) |
| `saber.clash` | `saber/clash1..3` | Blade-on-blade contact: a crack, a spitting sizzle, a harsh ring, both hums overdriven |
| `saber.deflect` | `saber/deflect1..3` | Bolt batted away: a falling zap, a zing, sparks and a flick of the hum |
| `saber.parry` | `saber/parry` | Perfect-timing ring over a tight contact |
| `saber.guard_break` | `saber/guard_break` | Guard collapse: an impact, a sizzle, a sagging groan, the hum stuttering down |
| `saber.hit` | `saber/hit` | Blade hits a body: a frying sear and a hum bump |
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
| `A/textures/particle/pull_streak_0..2.png` | Force Pull dust streak (16x16); unused since October 2026 | placeholder |
| `A/textures/particle/jump_trail_0..1.png` | Force Jump motes (8x8); unused since October 2026 | placeholder |
| `A/textures/particle/lightning_segment_0..3.png` | One piece of a Lightning arc (16x16); unused since October 2026 | placeholder |
| `A/textures/particle/lightning_spark_0..1.png` | Fingertip crackle, contact and disarm sparks (8x8) | placeholder |
| `A/textures/particle/ward_glint_0..3.png` | Serene Ward glint (8x8); unused since October 2026 | placeholder |
| `A/particles/pull_streak.json`, `jump_trail.json`, `lightning_segment.json`, `lightning_spark.json`, `ward_glint.json` | Particle sprite lists | final |
| `D/damage_type/force_lightning.json`, `force_impact.json` | Damage types | final |

Force Heal reuses `force_mote`; jump debris uses vanilla block particles.

## Saber combat and Saber Throw

| Path | What it is | Status |
|---|---|---|
| `A/textures/particle/blade_glint_0..1.png` | NPC wind-up tell along the blade (8x8); the particle stays registered, but since October 2026 the tell is a glint of light drawn in code | unused |
| `A/textures/particle/lunge_streak.png` | Lunge path streak (8x8); likewise replaced by streaks of light drawn in code (the lunge and the staff whirl) | unused |
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
| `A/textures/particle/sense_mote_0..3`, `sense_stream_0..1`, `lift_shimmer_0..3`, `holocron_mote`, `projection_band` | Sense, Lift and holocron particles (12 sprites; the Sense and Lift ones unused since October 2026) | placeholder |
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

---

# Milestone 4 additions

Same rules as above: every file is an original **placeholder** made by a script in `tools/assets/`, and nothing is copied. This part covers all six areas: W1 worlds, W2 Chapter Four, vendors and economy, W3 ships and travel, W4 gear, W5 crafting and saber, and W6 Force powers, foes and Kaast. W2's 9 and W6's 63 sound files (72 in all) replaced the generic Phase 0 placeholders under the same names, so `sounds.json` did not change. Every path in this part was checked against the repository. Every texture, parent model and sprite that the 184 new model, particle and equipment JSON files and the 111 new item definitions and block states reference exists, and so does every entity skin, portrait and skill icon that W2's and W6's code and data name: **no referenced Milestone 4 asset is missing.** What is missing is final art and audio, listed at the end under "Missing assets (Milestone 4)".

Per-feature briefs, with every file, its status and the brief for the final artist or sound designer: [planets](assets/planets.md) (W1), [Chapter Four](assets/chapter_four.md) and [vendors](assets/vendor.md) (W2), [ships and travel](assets/travel.md) (W3), [gear](assets/gear.md) (W4), [crafting](assets/crafting.md) (W5), [saber](assets/saber.md), "Milestone 4 additions" (W5), and [Force powers](assets/force_m4.md) and [foes](assets/foes.md) (W6).

## Summary (Milestone 4)

| Category | Count | Status | Generator |
|---|---|---|---|
| Block textures (W1 plants, ores, duskglass, waystones, Heartstone, sealed plating, hub spawner; W2 contract terminal and marker beacon; W3 landing beacon and pad terminal; W5 Engineering Bench; W6 camp gate) | 35 PNG (16, 6, 7, 5, 1) | placeholder | `gen_textures_planet.py`, `gen_textures_hub.py`, `gen_textures_ship.py`, `gen_textures_crafting.py`, `gen_textures_foes.py` |
| Entity textures (7 courier shuttle sheets; 9 armour layers and 2 cape and tabard model sheets; 18 hub people; 18 foe sheets: hunters, Kaast, the droids, the beasts, the rocket, net and mine) | 54 PNG | placeholder | `gen_textures_ship.py`, `gen_textures_equipment.py --m4`, `gen_textures_hub.py`, `gen_textures_foes.py` |
| GUI textures and sprites (the destination map; the workbench's lens hint; 11 portraits; 15 skill icons) | 28 PNG | placeholder | `gen_textures_ship.py`, `gen_textures_saber.py`, `gen_textures_hub.py`, `gen_textures_foes.py` |
| Item textures (W1 2, W2 5, W3 9, W4 36, W5 crafting 12, W5 saber 7, W6 10) | 81 PNG | placeholder | the texture scripts above |
| Particle textures (8 new particle types) | 27 PNG | placeholder | `gen_textures_planet.py`, `gen_textures_ship.py`, `gen_textures_equipment.py --m4`, `gen_textures_foes.py` |
| **Textures total (Milestone 4)** | **225 PNG** (683 in total) | placeholder | |
| Sounds and music | 139 new events, 140 OGG files (356 events and 380 files in total), including Kaast's 3 music loops | synthesised placeholders; `workbench.tune` is still the generic Phase 0 one | `gen_sounds.py --m4 --feature <module>` with `tools/assets/sounds/{world_m4,story_m4,ship,gear,crafting,saber,force_m4,enemy_m4}.py` |
| Structure templates | 19 NBT with 19 `site_anchors` sidecars (W1 17, W3 2) | placeholder | `gen_structures_m4.py`, `gen_structures_ship_m4.py` |
| Blockstates, block models, item definitions and item models | 17 blockstates, 19 block models, 94 item definitions, 152 item models | final (flat, cube and part models), except the saberstaff display, which needs tuning in game (tuned from CI screenshots in October 2026) | the texture scripts above; W6's are hand-written |
| Equipment assets (`A/equipment/*.json`) | 5 JSON (11 in total) | final | `gen_textures_equipment.py --m4` |
| Particle sprite lists | 8 JSON (58 in total) | final | the texture scripts above |
| Dimensions and worldgen (2 dimension types, 2 dimensions, 2 noise settings, 6 biomes, 15 configured and 17 placed features, 2 structures, 5 template pools, 2 structure sets, 7 worldgen tags) | 60 JSON | final (tuning may change) | hand-written and `gen_structures_m4.py` |
| Schematics, loot tables (8 block, 2 chest), the bench recipe, `duskglass_cut`, the pad terminal dialogue, 4 armour repair tags | 65 schematics and 17 other JSON | final content, tuning may change | hand-written |
| W2 data: 9 vendor stocks, 7 story missions and 12 planet contracts, 20 dialogues (and the two officer dialogues extended), loot tables `gameplay/planet_contract`, `blocks/contract_terminal` and `blocks/marker_beacon` | 51 JSON | final content, tuning may change | hand-written |
| W6 data: 10 entity loot tables and `chests/kaast_stash`, 5 damage types, 3 cooking recipes, the entity tags `droids` and `choke_immune`, the `minecraft:is_fire` tag; additions to the 5 skill trees and to the `is_explosion`, `force_harm` and `c:bosses` tags | 22 JSON | final content, tuning may change | hand-written |

The courier shuttle, the hub people (the mentors' robed model), the hunters, Kaast, the droids, the beasts, the rocket, the net and the mine have their models in code; only their skins are files. The hyperspace overlay, the flight HUD, the ship screen, the bench screen, the vendor screen, the blaster reticle and heat gauge are drawn in code with the UI kit sprites.

## Generator scripts (Milestone 4)

| Script | Writes | Needs |
|---|---|---|
| `python3 tools/assets/gen_textures_planet.py` | The W1 block, item and particle textures and their JSON | Pillow |
| `python3 tools/assets/gen_structures_m4.py` | The 17 planet templates (waystones, greater tomb pieces, hubs, the Sepulchre, the Heartstone, expeditions, the Wayfarer cache, the hunting camp, the ambush rocks) and their sidecars | Python 3 only |
| `python3 tools/assets/gen_textures_ship.py` | The courier shuttle sheets, the ship item icons, the beacon and terminal faces (the beacon lamp is a 4-frame texture), the destination map, `engine_exhaust` and `spark_shower`, and their JSON | Pillow |
| `python3 tools/assets/gen_structures_ship_m4.py` | `D/structure/ship/cradle.nbt`, `beacon_pad.nbt` and their sidecars | Python 3 only |
| `python3 tools/assets/gen_textures_equipment.py --m4` | The four M4 armour sets and Kaast's two pieces (icons, layers, cape and tabard sheets, `A/equipment/*.json`), the heavy repeater, Kaast's Longshot, the vibrosword, the electrostaff, the 12 mods (11 mod items and the jet pack), `steam_vent` and `flame_jet` | Pillow |
| `python3 tools/assets/gen_textures_crafting.py` | The Engineering Bench faces, materials, consumables, tools, grenades and the schematic item, and their JSON | Pillow |
| `python3 tools/assets/gen_textures_saber.py` | Its Milestone 4 section: the staff grip, saberstaff and training saber icons and models, the three lenses, the lens hint (earlier output unchanged) | Pillow |
| `python3 tools/assets/gen_textures_hub.py` | W2: the 18 hub people's skins, the 11 portraits, the story and credit item icons, the contract terminal and marker beacon faces, and their JSON | Pillow |
| `python3 tools/assets/gen_textures_foes.py [--part particle\|skill\|entity\|item\|block]` | W6: `held_dust`, the 15 skill icons, the 18 foe sheets, the 10 item icons and the camp gate (textures only; the JSON is hand-written) | Pillow |
| `python3 tools/assets/gen_sounds.py --m4 --feature <module>` | One recipe module's sounds: `world_m4` (19), `story_m4` (9), `ship` (16), `gear` (15 files), `crafting` (14), `saber` (3), `force_m4` (13), `enemy_m4` (50, with Kaast's music) | numpy |

## Worlds (W1): `docs/assets/planets.md`

| Group | Count | Status |
|---|---|---|
| Block textures: `silverleaf`, `ashroot`, `aurelite_ore`, `deepslate_aurelite_ore`, `duskglass_cluster`, `budding_duskglass`, `waystone_{front,side,top}`, `waystone_corrupted_{front,side,top}`, `heartstone_{side,top}`, `sealed_plating`, `hub_spawner` | 16 PNG | placeholder |
| Item textures: `aurelite_shard`, `duskglass_shard` | 2 PNG | placeholder |
| Particles: `ash_flake` (3 sprites), `aurelite_glint` (4 frames), `duskglass_shard` (3 sprites) | 10 PNG, 3 sprite lists | placeholder sprites, final lists |
| Blockstates, block models, item definitions and item models | 11, 11, 12, 12 JSON | final |
| Structure templates with sidecars: `planet/waystone`, `waystone_corrupted`, `heartstone`, `wayfarer_cache`; `greater_tomb/gate`, `descent`, `urn_vault`, `sealed_crypt`; `hub/greenwatch`, `proving`, `sepulchre`; `expedition/singing_hollow_{surface,grotto}`, `duskglass_rift_{surface,rift}`; `boss/hunting_camp`; `mission/ambush_rocks` | 17 NBT, 17 sidecars | placeholder |
| Sounds: `waystone.*` (3), `heartstone.*` (2), `hollow.*` (2), `rift.*` (2), six biome beds, two hub beds, `music.selvaren`, `music.varkhast` | 19 events, 19 OGG | synthesised placeholders |

## Ships and travel (W3): `docs/assets/travel.md`

| Group | Count | Status |
|---|---|---|
| Entity textures: `entity/ship/courier_shuttle_{watch,imperial,vasch}` (paint), `_engines`, `_lights`, `_lights_damaged`, `_scorch` | 7 PNG | placeholder |
| Block textures: `landing_beacon_{base,post,post_front,lamp}` (the lamp animated, `.mcmeta`), `pad_terminal_{base,body,screen}` | 7 PNG | placeholder |
| Item textures: `power_core`, `hyperdrive_motivator`, `nav_computer`, `nav_computer_loaded`, `hyperfuel_cell`, `passenger_seat`, `cargo_bay_extension`, `reinforced_plating`, `fuel_tank_extension` | 9 PNG | placeholder |
| GUI: `gui/destination_map.png` | 1 PNG | placeholder |
| Particles: `engine_exhaust` (4), `spark_shower` (2) | 6 PNG, 2 sprite lists | placeholder sprites, final lists |
| Blockstates, block models, item definitions and item models | 2, 2, 10, 9 JSON | final |
| Structure templates with sidecars: `ship/cradle`, `ship/beacon_pad` | 2 NBT, 2 sidecars | placeholder |
| Sounds: `ship.*` (13), `beacon.*` (3) | 16 events, 16 OGG | synthesised placeholders |

## Gear (W4): `docs/assets/gear.md`

| Group | Count | Status |
|---|---|---|
| Item textures: the heavy repeater and Kaast's Longshot (icon and in-hand sheet each), the vibrosword, the electrostaff, the jet pack, 16 armour icons (Watch battle, Sith war, Mandalorian, spacer's gear), Kaast's helmet and chest, the 11 mod items (with the jet pack, 12 mods) | 36 PNG | placeholder |
| Entity textures: armour layers `humanoid/{watch_battle,sith_war,mandalorian,kaast,spacer}`, `humanoid_leggings/{watch_battle,sith_war,mandalorian,spacer}`; the Watch tabard and Sith war cape sheets `entity/robe/{watch_battle,sith_war}` | 11 PNG | placeholder |
| Particles: `steam_vent` (4), `flame_jet` (4) | 8 PNG, 2 sprite lists | placeholder sprites, final lists |
| Item definitions, item models (including the two in-hand long-gun models), equipment assets | 34, 36, 5 JSON | final (the in-hand geometry is ours; an artist may refine it) |
| Sounds: `blaster.repeater.fire` (2 variants), `blaster.rifle.charged`, `blaster.overheat`, `blaster.vent`, `blaster.vent_perfect`, `blaster.repeater.spin_up`, `spin_down`, `vibro.flurry`, `vibro.cleave`, `electrostaff.charge`, `shock`, `clash`, `jetpack.burst`, `jetpack.empty` | 14 events, 15 OGG | synthesised placeholders |
| Reused sounds: the player pistol and rifle fire `blaster.pistol_fire` and `blaster.fire`, the rifle charge `blaster.charge`, the charged-shot latch `workbench.slot`, the vibro hits and swings the M2 `vibroblade.*` and saber sounds | none new | existing placeholders |

## Crafting (W5): `docs/assets/crafting.md`

| Group | Count | Status |
|---|---|---|
| Block textures: `engineering_bench_{front,side,top,metal,arc}` | 5 PNG | placeholder |
| Item textures: `alloy_plate`, `masterwork_fragment`, `medpac`, `kolto_injector`, `combat_stim`, `focus_stim`, `scanner`, `fusion_cutter`, `repair_kit`, `frag_grenade`, `stun_grenade`, `schematic` | 12 PNG | placeholder |
| Blockstate, block model, item definitions and item models | 1, 1, 13, 12 JSON | final |
| Data: 65 schematics, `recipe/engineering_bench`, `loot_table/blocks/engineering_bench` | 67 JSON | final content, tuning may change |
| Sounds: `bench.*` (6), `medpac.use`, `kolto.inject`, `stim.inject`, `stim.crash`, `scanner.pulse`, `scanner.ping`, `grenade.throw`, `grenade.stun` | 14 events, 14 OGG | synthesised placeholders |

The player's frag grenade has no effects of its own past the throw: in flight and on the ground it is the Milestone 3 grenade, with its skin (`entity/frag_grenade.png`), its `grenade_fuse` and `grenade_burst` effects and its `grenade.fuse` and `grenade.burst` sounds. (`docs/assets/crafting.md` still says vanilla's bounce and explosion; the code uses the M3 grenade's.)

## Saber additions (W5): `docs/assets/saber.md`, "Milestone 4 additions"

| Group | Count | Status |
|---|---|---|
| Item textures: `grip_staff`, `lightsaber_staff_icon_hilt`, `lightsaber_staff_icon_blade`, `training_saber_icon_hilt`, `lens_{honed,steady,resonant}` | 7 PNG | placeholder |
| GUI sprite: `gui/sprites/bench/hint_lens` | 1 PNG | placeholder |
| Item definitions: `lightsaber_staff`, `training_saber`, `training_saber_red`, `grip_staff`, three lenses | 7 JSON | final |
| Item models: the staff's two parents; its grip and emitters, once for normal use and once for the guard (in `lightsaber/staff/` and `staff_guard/`); the icons and lenses. The 42 staff and 14 training blade stages were removed in October 2026, when the blades came to be drawn in code | 28 JSON | placeholder geometry; `staff_base.json` and `staff_guard_base.json` turn the staff crosswise in the fist for the two-handed hold, tuned from CI screenshots, need hand-play |
| Sounds: `saber.staff_ignite`, `saber.staff_whirl`, `workbench.lens_fit`, and the reserved `workbench.tune` | 4 events, 4 OGG | synthesised placeholders (the two saber sounds rebuilt on the film-style saber hum, October 2026); `workbench.tune` generic and unused (crystal tuning is cut) |

## Chapter Four and vendors (W2): `docs/assets/chapter_four.md`, `docs/assets/vendor.md`

| Group | Count | Status |
|---|---|---|
| Entity textures: `entity/hub/{ansa,kaddor,zarvos,apprentice_0..2,acolyte_0..3,proving_guard,watch_quartermaster,imperial_quartermaster,spacer,trainer_armstech,trainer_armourcraft,trainer_artifice,trainer_medtech}`, 64x64 skins for the mentors' robed model | 18 PNG | placeholder |
| GUI sprites: portraits `gui/sprites/portrait/{ansa,kaddor,kaast,mandalorian,smuggler,quartermaster_watch,quartermaster_imperial,spacer,trainer,apprentice,acolyte}`, 32x32 | 11 PNG | placeholder |
| Item textures: `kaast_pattern`, `tomb_relic`, `sealed_contract`, `credit_chit`, `credit_bundle` | 5 PNG | placeholder |
| Block textures: `contract_terminal_{front,side,top}`, `marker_beacon_{post,lamp,base}` | 6 PNG | placeholder |
| Blockstates, block models, item definitions and item models | 2, 2, 7, 7 JSON | final |
| Data: vendor stocks, missions, dialogues and loot tables (see the summary) | 51 JSON | final content, tuning may change |
| Sounds: `story.chapter_four`, `planet.arrive`, `seeker.scan`, `seeker.chirp`, `seeker.fly`, `vendor.buy`, `vendor.refuse`, `credits.pickup`, `hub.apprentice_spar` | 9 events, 9 OGG | synthesised placeholders |

The seeker droid's model and skin are W6's. The vendor screen uses the M3 UI kit and needed no new GUI texture.

## Force powers (W6): `docs/assets/force_m4.md`

| Group | Count | Status |
|---|---|---|
| Particle: `held_dust` (3 sprites, tinted in code) | 3 PNG, 1 sprite list | placeholder sprites, final list |
| Skill icons: `gui/sprites/skill/{force_speed,speed_burst,speed_long_road,speed_wayfarer,force_barrier,barrier_shelter,barrier_return,guardians_patience,lift_learned,force_choke,choke_crush,choke_dread,cruel_focus,staff_whirl,lens_mastery}` (`lift_learned` serves both Lift nodes) | 15 PNG | placeholder |
| Skill tree nodes: 17 new nodes in `skill_tree/{universal,exploration,jedi,sith,saber}.json` | 5 files extended | final |
| Sounds: `force.speed.{start,loop,end,burst}`, `force.barrier.{start,loop,impact,pulse,break}`, `force.choke.{start,loop,release,crush}` | 13 events, 13 OGG | synthesised placeholders |

## Foes and Kaast (W6): `docs/assets/foes.md`

| Group | Count | Status |
|---|---|---|
| Entity textures: `entity/mandalorian/hunter_{0,1,2,3}` (four paints), `hunter_rocket`, `hunter_net`; `entity/kaast/torvin_kaast`, `torvin_kaast_cracked`, `camp_mine`, `kaast_droid` (Rattle); `entity/droid/security_droid`, `security_droid_imperial`, `security_droid_eye` (emissive), `seeker_droid`; `entity/creature/hollowhorn`, `hollowhorn_bull`, `hollowhorn_corrupted`, `ash_hound` | 18 PNG | placeholder |
| Item textures: `raw_hollowhorn`, `cooked_hollowhorn`, `hollowhorn_hide`, `hollowhorn_horn`, `ash_hound_fang`, `droid_actuator`, and the spawn eggs of the hunter, the droid, the hollowhorn and the hound | 10 PNG | placeholder |
| Block texture: `camp_gate` | 1 PNG | placeholder |
| Blockstate, block models (`camp_gate`, `camp_gate_open`, `camp_gate_hidden`), item definitions and item models | 1, 3, 11, 10 JSON | final |
| Data: loot tables, damage types, recipes and tags (see the summary) | 22 JSON | final content, tuning may change |
| Sounds: `mandalorian.*` (12), `droid.*` (7), `hollowhorn.*` (6), `ash_hound.*` (6), `boss.kaast.*` (16) | 47 events, 47 OGG | synthesised placeholders |
| Music: `music.kaast.hunt`, `trap`, `last_stand` (one loop per phase) | 3 events, 3 OGG | synthesised placeholders, original (no quotations) |

## Missing assets (Milestone 4)

Final art and audio are needed for everything below. Data files (worldgen, schematics, loot tables, tags) and the JSON that only wires a texture are final and not listed.

- **Worlds**
  - 16 planet block textures and the two shard icons; the three planet particles.
  - The 17 planet templates: the waystones, the greater tomb, both hubs, the Sepulchre, the Heartstone, the expeditions, the Wayfarer cache, the hunting camp and the ambush rocks.
- **Ships**
  - The courier shuttle's 7 sheets (three paints, engines, lights, damaged lights, scorch), the beacon and terminal faces, 9 ship item icons, the destination map, `engine_exhaust` and `spark_shower`.
  - The cradle and beacon pad templates.
- **Gear**
  - 16 M4 armour icons and Kaast's two pieces, 9 armour layers and the 2 cape and tabard sheets.
  - The heavy repeater, Kaast's Longshot, the vibrosword, the electrostaff and the 12 mods (the 11 mod items and the jet pack); `steam_vent` and `flame_jet`.
- **Crafting and saber**
  - The Engineering Bench, 12 crafting item icons, the staff grip, saberstaff and training saber icons, the three lenses and the lens hint.
  - The saberstaff's held display (in-game tuning more than art; tuned from CI screenshots in October 2026, still to be checked by hand).
  - `textures/gui/container/lightsaber_workbench.png` is still generated but the screen no longer draws it; it is kept for a final artist.
- **Chapter Four and vendors**
  - The 18 hub people's skins and the 11 portraits; the story and credit item icons; the contract terminal and marker beacon faces.
- **Force powers**
  - `held_dust` and the 15 skill icons.
- **Foes and Kaast**
  - The hunters' four paints, Kaast and his cracked helmet, the rocket, net and mine, the security droid (both colours and its eye), the seeker droid and Rattle, the hollowhorn (with the bull and the corrupted one) and the ash hound; their item icons, spawn eggs and the camp gate.
- **Sounds and music**
  - All 139 new events (140 files) are synthesised placeholders. The ones heard most: the six biome beds and two hub beds, the two planet music loops, Kaast's three music loops, `ship.idle`, `ship.hyperspace`, `ship.damaged`, `bench.weld`, `waystone.corrupt`, the repeater fire, the jet pack burst, the hunters' tells and flamer, `droid.fire`, and the power loops `force.speed.loop`, `force.barrier.loop` and `force.choke.loop`. `workbench.tune` is the one event still on the generic Phase 0 placeholder, and the rifle's charged-shot latch borrows `workbench.slot`. Rules in `AUDIO_BRIEF.md`.
- **Voices**
  - None are recorded. The hunters' hurt, death and bark sounds are a wordless voice through a helmet, Kaast's grunts are wordless, the Proving's loudspeaker in `ambient.proving` is wordless, and the Chapter Four people speak in subtitles only.

---

# Milestone 5 additions

Same rules as above: every file is an original **placeholder** made by a script in `tools/assets/`, and nothing is copied. It covers all eight Milestone 5 areas: the free flight and the ships (W1), space and ship combat (W2), the worlds (W3), Chapter Five and the factions (W4), the paths and ranks (W5), the Force powers (W6), sabers, crafting and gear (W7), and the foes, bosses and the raid (W8).

Per-feature briefs, with every file, its status and the brief for the final artist or sound designer: [flight](assets/flight.md) (W1), [space](assets/space.md) (W2), [worlds](assets/planets_m5.md) (W3), [Chapter Five](assets/chapter_five.md) (W4), [paths and ranks](assets/turning.md) (W5), [powers](assets/powers_m5.md) (W6), [sabers, crafting and gear](assets/sabers_m5.md) (W7), [foes and bosses](assets/foes_m5.md) (W8).

## Force powers (W6): `docs/assets/powers_m5.md`

| Asset | Count | Status |
|---|---|---|
| Particles `stasis_rime`, `drain_mote`, `valor_dust` | 9 PNG (8 × 8, tinted in code), 3 sprite lists | placeholder sprites, final lists; unused since October 2026 |
| Effect icons `mob_effect/{stasis,fear,pacified,valor,rage,drained}` | 6 PNG (18 × 18) | placeholder |
| Skill icons `gui/sprites/skill/<the 22 new nodes>` | 22 PNG | placeholder |
| Skill tree files | 4 extended | final |
| Data: the `force_drain` damage type and the 4 entity tags | 5 JSON | final |
| Sounds: `force.stasis.{cast,loop,break}`, `force.valor.{cast,end}`, `force.drain.{loop,end}`, `force.fear.cast`, `force.rage.{cast,loop,end}`, `force.mind_trick.{cast,fail}`, `force.focus` | 14 events, 14 OGG | synthesised placeholders |

## Paths and ranks (W5): `docs/assets/turning.md`

| Asset | Count | Status |
|---|---|---|
| Item texture `watch_holocron` (16 × 128, eight interpolated frames) | 1 PNG, 1 mcmeta | placeholder |
| Item definition and model `watch_holocron` | 2 JSON | final |
| Dialogues `turn_*` (9) and `rank4_*` (3) | 12 JSON | final content |
| Lang `turning.json` | 183 keys | final text |
| Sounds `story.turning.offer`, `story.turning.rite`, `story.rank_four` | 3 OGG | synthesised placeholders |

The robes are W7's; the skins and portraits are W4's and earlier milestones'; the particles are W3's, W6's and W8's.

## Sabers, crafting and gear (W7): `docs/assets/sabers_m5.md`

| Asset | Count | Status |
|---|---|---|
| Item icons (3 hilt parts, the lens, 4 mods, 2 ship parts, 2 materials, 3 rank 4 pieces, the mask) and the legendary hilt swatch | 17 PNG (16 × 16) | placeholder |
| Robe model textures `entity/robe/*` | 4 PNG (128 × 64, 128 × 64, 64 × 64, 64 × 32) | placeholder |
| Plain humanoid layers | 4 PNG | placeholder |
| Equipment assets | 4 JSON | final |
| Item definitions (16), item models (16), saber part, blade and display-parent models (36), regenerated saber item definitions (4) | 72 JSON | final, generated |
| Schematics | 11 JSON | final |
| Sounds: ignition, hum and retraction for the four emitter profiles, `saber.offhand_swing`, `bench.legendary` | 14 events, 14 OGG | synthesised placeholders (the 13 saber sounds rebuilt on the film-style saber hum, October 2026) |
| Lang | 85 keys added | final text |

## Worlds (W3): `docs/assets/planets_m5.md`

| Asset | Count | Status |
|---|---|---|
| Block textures (salt crust top and side, sunstone ore, stormglass cluster, peat, the verse stone awake and silent, top and side, the holdfast gate) and item textures (`sunstone`, `stormglass_shard`) | 12 PNG (16 × 16) | placeholder |
| Particle frames `sand_gust_0` to `_3`, `verse_mote_0` to `_2` | 7 PNG (8 × 8) | placeholder |
| Block models (8), block states (6), item definitions and item models (6 each), particle definitions (2) | 28 JSON | final |
| Structure templates: `hub/saltgate`, `hub/holdfast`, `site/verse_stone`, `site/hymnal_arch`, `site/hymnal`, `site/debtor_camp`, `site/detention_post`, `planet/crashed_freighter`, `planet/kajidic_post`, `planet/hymn_shrine`, `planet/mire_grave` | 11 NBT, with 11 sidecar anchor files | placeholder (functional; walkability checked) |
| World data: 2 dimensions and dimension types, noise settings, density functions, 6 biomes, 10 configured and 10 placed features, 4 worldgen structures with their sets, pools and tags | JSON | final (tuning may change) |
| Loot tables (6 blocks, 4 chests) | 10 JSON | final (tuning may change) |
| Sounds: `verse_stone.{hum,pulse,channel,silence}`, `sandstorm.wind`, `stormglass.form`, `holdfast.gate`, `hymnal.door`, 9 ambience beds, `music.ghessar`, `music.tyrvann` | 19 events, 19 OGG | synthesised placeholders |
| Lang `planet.json` additions | | final text |

## Chapter Five and the factions (W4): `docs/assets/chapter_five.md`

| Asset | Count | Status |
|---|---|---|
| Hub people skins (64 × 64) | 15 PNG | placeholder |
| Vessba (128 × 128) | 1 PNG | placeholder (the model is final code) |
| Portraits (32 × 32) | 13 PNG | placeholder |
| Faction icons (9 × 9) | 2 PNG | placeholder |
| `ulvaines_writ` (16 × 16) | 1 PNG and 2 JSON | placeholder; the JSON is final |
| Dialogues (17), missions (17), vendors (2), loot tables (3) | 39 JSON | final content |
| Lang | 561 keys added | final text |
| `story.chapter_five`, `story.verse_vision` | 2 OGG | synthesised placeholders |

## Foes and bosses (W8): `docs/assets/foes_m5.md`

| Asset | Count | Status |
|---|---|---|
| Foe textures: the cartel's, Clan Hask's and the Hymnsworn's three kinds each, the scour and the matriarch, the stormback and its charged veins (64 × 64, 128 × 64, 128 × 32) | 13 PNG | placeholder |
| Boss textures: Ulvaine and her hologram skin, the resonator and its light, the First Cantor and his light, a choir echo and its light (64 × 64, 128 × 64) | 8 PNG | placeholder |
| Particles `sand_burst` (4 frames), `spectral_wisp` (3 frames) | 7 PNG (8 × 8), 2 sprite lists | placeholder sprites, final lists |
| Spawn eggs (5) with item definitions and models | 5 PNG (16 × 16), 10 JSON | placeholder icons, final JSON |
| Loot tables (9 entities, `gameplay/long_hymn`), damage types `storm_call` and `hymn` | 12 JSON | final |
| Lang `enemy.json` (+15) and `boss.json` (+62) | 77 keys | final text |
| Sounds: `cartel.*` (4), `hask.*` (8), `hymnsworn.*` (5), `scour.*` (8), `stormback.*` (7), `boss.ulvaine.*` (13), `boss.cantor.*` (17), `story.hologram` | 63 events, 63 OGG | synthesised placeholders |
| Music `music.boss.ulvaine`, `music.boss.cantor` (eight-bar loops, streamed) | 2 OGG | synthesised placeholders |

## Flight and ships (W1): `docs/assets/flight.md`

| Asset | Count | Status |
|---|---|---|
| Interceptor paints, lights (and damaged), engines and scorch, 256 × 128 | 7 PNG | placeholder |
| Courier paints and lights regenerated with the weapon parts' 132 × 12 block | 5 PNG | placeholder |
| `atmosphere_streak` frames (8 × 8) and the particle JSON | 3 PNG, 1 JSON | placeholder / final |
| Sounds `ship.flight.{engine,boost,thruster,atmosphere,impact,land_warn,helm,edge,alarm,rescue}`, `ship.interceptor.engine`, `ship.refit`, `ship.upgrade_fit` | 13 OGG | synthesised placeholders |
| Lang `travel.json` | 97 keys added | final text |

## Space (W2): `docs/assets/space.md`

| Asset | Count | Status |
|---|---|---|
| Planet maps and the nebula (`environment/space/*`) | 6 PNG | placeholder |
| Fighter, gunship, missile and buoy textures with glow layers (`entity/space/*`) | 12 PNG | placeholder |
| `hull_debris` sprites and item icons | 4 + 5 PNG | placeholder |
| Item definitions and models, the particle list | 11 JSON | final |
| Station template `station/spindle` and its sidecar | 1 NBT, 1 JSON | placeholder template, final anchors |
| Data: `orbit_field` biome, asteroid feature, `ship_weapon`, `#ship_targets`, two loot tables | 7 JSON | final (tuning may change) |
| Sounds (`space.*`, `ambient.space`, `music.space`) | 24 events, 26 OGG | synthesised placeholders |
| Lang `space.json` | 79 keys | final text |

## Missing assets (Milestone 5)

Final art and audio are needed for everything below. Data files and the JSON that only wires a texture are final and not listed.

- **Force powers**
  - The 9 particle sprites (`stasis_rime`, `drain_mote`, `valor_dust`), the 6 effect icons and the 22 skill icons: 37 PNGs. Since October 2026 the 9 particle sprites are unused and need no final art.
  - The 14 sounds, all synthesised placeholders.
- **Paths and ranks**
  - The `watch_holocron` texture (eight frames): 1 PNG.
  - The 3 sounds, all synthesised placeholders.
- **Sabers, crafting and gear**
  - The 16 item icons, the legendary hilt swatch, the 4 robe textures and the 4 plain humanoid layers: 25 PNGs.
  - The 14 sounds, all synthesised stand-ins; the 13 saber ones follow the saber asset brief in `AUDIO_BRIEF.md` (Lightsaber).
- **Worlds**
  - The 12 block and item textures and the 7 particle frames: 19 PNGs.
  - The 11 structure templates (functional placeholders a builder should replace, keeping their sizes and anchors).
  - The 19 sounds, all synthesised placeholders.
- **Chapter Five and the factions**
  - The 15 skins, Vessba, the 13 portraits, the 2 faction icons and the writ: 32 PNGs.
  - The 2 sounds, both synthesised placeholders.
- **Foes and bosses**
  - The 21 entity textures, the 7 particle frames and the 5 egg icons: 33 PNGs.
  - The 63 sounds and the 2 music loops, all synthesised placeholders.
- **Flight and ships**
  - The 7 interceptor textures, the 5 regenerated courier textures and the 3 streak frames: 15 PNGs.
  - The 13 sounds, all synthesised placeholders.
- **Space**
  - The 27 PNGs and the Spindle template.
  - The 26 sounds, all synthesised placeholders.

# After Milestone 5: the owner's fixes (October 2026)

Gabriel's first list (`DECISIONS.md`, "After Milestone 5"). The same rules hold: every file is an original **placeholder** made by a script in `tools/assets/`, and nothing is copied or sampled. Most of the new look has no asset: the saber blade, its trail and sparks, the Force powers' light and the kinetic pressure fronts are drawn in code.

Briefs: [lightsaber](assets/saber.md) and [Milestone 5 sabers](assets/sabers_m5.md) (updated in place), [Force effects, October 2026](assets/force_october.md), and [Audio](AUDIO_BRIEF.md) (Lightsaber).

| Asset | Count | Status |
|---|---|---|
| Kinetic particles `kinetic_dust`, `kinetic_pebble`, `kinetic_streak`, `kinetic_shard`, `kinetic_rime`, `kinetic_leaf` (`tools/assets/gen_textures_force_kinetic.py`) | 27 PNG (16 × 16 and 32 × 32, tinted in code), 6 sprite lists | placeholder sprites, final lists |
| Energy particles `energy_smoke`, `energy_dust`, `energy_mist` (`tools/assets/gen_textures_force_energy.py`) | 12 PNG (32 × 32, tinted in code), 3 sprite lists | placeholder sprites, final lists |
| The Barrier's `held_dust`, regenerated as soft 32 × 32 specks (`tools/assets/gen_textures_foes.py`) | 3 PNG | placeholder |
| Saber sounds rebuilt on one film-style hum (`tools/assets/sounds/saber.py`, `saber_m5.py`, through `gen_sounds.py`): ignition, hum and retraction of every emitter, swings, clashes, deflections, the techniques, the throw, the saberstaff and the off-hand | 41 OGG (same events and file names) | synthesised placeholders |
| Saber item definitions and the four hilt model parents (the held display) | 4 item definitions, 4 models | final |
| Removed: the blade's stage models and texture | 82 models, 1 PNG | gone (the blade is drawn in code) |

Unused since October 2026 and needing no final art: the saber's `saber_spark`, `contact_flash`, `blade_glint` and `lunge_streak` sprites, and twelve Force sprite sets (`assets/force_october.md`).

## Missing assets (October 2026)

- **Saber sounds:** the 41 files above, all synthesised to the films' character. A sound designer would replace them by the saber brief in `AUDIO_BRIEF.md` (Lightsaber), keeping the file names.
- **Force particles:** the 39 new sprites and the 3 regenerated `held_dust` frames (42 PNGs). The light and the pressure fronts need no art.
- **Force sounds:** none was added or changed. Ideas for new ones are at the end of `VFX_SFX_SPEC.md`.
