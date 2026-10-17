# Boss assets: Ruin Sentinels, Orren Vasch, the Reliquary

Milestone 3, W5. Everything here is **original placeholder work**, generated deterministically by scripts in this
repository; nothing is copied from or imitates any Star Wars game or film asset, and Orren Vasch, the Reliquary and the
sentinels are original designs. Re-run the generators instead of editing their outputs:

| Generator | Writes |
|---|---|
| `tools/assets/gen_textures_boss.py` | every texture, block model, blockstate, item definition and particle JSON in the sections "Ruin Sentinel", "Orren Vasch", "Blocks" and "Particles" |
| `tools/assets/gen_structures_reliquary.py` | the two Reliquary templates and their anchor sidecars ("Structures") |
| `tools/assets/sounds/boss.py` through `python3 tools/assets/gen_sounds.py --m3 --feature boss` | the 44 sound files ("Sounds") |

Paths are under `src/main/resources/`. "placeholder generated" means the file is usable in game and labelled for
replacement; the brief says what the final version should be. Code that draws or plays each asset is named so a final
artist knows what must keep working.

## Ruin Sentinel (spec 7.7)

Model: `client.boss.sentinel.RuinSentinelModel` (layer `oldrepublic:ruin_sentinel#main`), a humanoid frame scaled to
2.3 blocks by `RuinSentinelRenderer`. One 64x64 UV layout serves both variants and the lumen layer:

| Part (parent) | texOffs | Notes |
|---|---|---|
| head | 0,0 | closed face plate; the vanilla hat is left empty |
| crest (head) | 32,0 | 2x3x10 carved ridge along the helm |
| legs (mirrored) | 0,16 | |
| body | 16,16 | |
| arms (mirrored) | 40,16 | |
| right/left chest plate (body) | 0,32 | hinged at the sides; they swing open (1.1 rad) for the exposed core and half open in the lance tell |
| spikes (arms) | 12,32 | Tomb only (hidden on the Watcher) |
| pauldrons (arms) | 24,32 | |
| lumen (body) | 48,32 | the chest light |
| fauld (body) | 0,41 | waist plates |
| gauntlets (arms) | 32,41 | |
| greaves (legs) | 0,50 | |
| tabard (body) | 20,50 | Watcher only; swings with the forward leg |
| back slab (body) | 36,52 | |

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `assets/oldrepublic/textures/entity/ruin_sentinel/watcher.png` | Watcher skin, 64x64 | placeholder generated | Weathered pale stone (warm grey, lichen in the cracks) with robe-like carved plates, bronze trims gone green at the edges, a closed face plate with one vertical slit. Calm, monastic, old. It must still read as stone, not armour, at 20 blocks. |
| `assets/oldrepublic/textures/entity/ruin_sentinel/tomb.png` | Tomb skin, 64x64 | placeholder generated | Dark basalt, angular, rust-red weathering; bronze spikes along the shoulders; two angled eye slits. Menacing, but the same construction family as the Watcher. |
| `assets/oldrepublic/textures/entity/ruin_sentinel/watcher_lumen.png`, `tomb_lumen.png` | Lumen layers, 64x64, same layout | placeholder generated | Only what glows: the chest lumen, the face slit or eyes, thin chest seams and the inner edges of the chest plates. Pale blue (Watcher, `#A8DCFF`) or red (Tomb, `#FF4636`) on transparent. Drawn additively and full bright (`RenderTypes.eyes`), dimmed by code while dormant, steady while the core is exposed; keep it free of dark pixels. |
| `assets/oldrepublic/textures/item/ruin_sentinel_spawn_egg.png` | Spawn egg, 16x16 | placeholder generated | A pale stone egg with bronze flecks and a small blue lumen spot. |
| `assets/oldrepublic/items/ruin_sentinel_spawn_egg.json`, `models/item/ruin_sentinel_spawn_egg.json` | Item definition and flat model | final | |
| `data/oldrepublic/loot_table/entities/ruin_sentinel.json` | Drops: `durasteel_plate` 40% (1-2); `lumen_thread` (Watcher) or `grave_silk` (Tomb) 20% | final | Not an art asset; listed for completeness. |

Poses (code, no animation files): statue (head lowered), the Watcher's bow, the wake (rising with a grinding tremor), the
slam tell (arms overhead), the slam and exposed core (arms down, plates open), the lance tell (arms drawn back, chest
forward, plates half open), stagger and the death slump. Effects are in `client.boss.sentinel.SentinelFx` (see
`docs/design/boss.md`, "Ruin Sentinels").

## Orren Vasch (spec 8.1)

Model: W5 part A's `client.boss.OrrenVaschModel` on `HumanoidModel.createMesh(CubeDeformation.NONE, 0f)`; the skin
layout is the W5 plan contract (4.10), 64x64, vanilla cube unwrap:

| Part (parent) | texOffs | Box (x, y, z, w, h, d) | Deformation |
|---|---|---|---|
| head, hat (hat = hair tie and short beard on `base`, hood up on `unkept`) | 0,0 / 32,0 | vanilla | vanilla |
| body, arms (40,16 mirrored), legs (0,16 mirrored) | vanilla | vanilla | vanilla |
| `robe` (body) | 16,32 | -4, 0, -2, 8, 12, 4 | 0.3 |
| `sleeve` (each arm, left mirrored) | 40,32 | right -3,-2,-2, 4,12,4 / left -1,-2,-2, 4,12,4 | 0.3 |
| `skirt` (body) | 0,48 | -4, 12, -2, 8, 8, 4 | 0.4 |
| `cloak` (body, pivot at the shoulders behind the back; lifts and streams out behind him in the Surge gather) | 0,32 | -4, 0, 0, 8, 16, 0 | 0 |
| `bandolier` (body, rotated about z by about 0.75 rad) | 24,48 | -4.5, -0.5, -2.5, 9, 1, 5 | 0 |
| `satchel` (body, right hip) | 24,54 | -2, 0, -1, 4, 4, 2 | 0 |
| `holocron` (left arm, phase 3 only) | 36,54 | -1, 9, -1, 2, 2, 2 | 0 |

**Model notes for the artist (W5 part A, `OrrenVaschModel`).** The boxes and UVs are the table above; these are
where the parts sit and how they move, so the paint lands where it shows.

| Part | Pose (pivot offset, rotation in radians) | What shows and when |
|---|---|---|
| `cloak` | offset (0, 0.3, 2.45) behind the back, tilted 0.06 away from it | A zero-depth plane: its outer side (seen from behind) is u 8-16, v 32-48; its lining (facing his back) is u 0-8, v 32-48. It sways a little at rest and lifts with his stride. In the Surge gather it lifts about 66 degrees and streams out behind him on the inrushing air (it does not swing forward: a plane hung behind him would pass through his body and head), so the lining shows from behind and below; paint the lining a darker ink-grey and the bottom edge frayed on `unkept`. |
| `bandolier` | offset (0, 6, 0), rotated 0.75 about z | Runs from his left shoulder down to his right hip across the chest; the front face (u 29-38, v 53-54) carries the row of empty brass cradles. |
| `satchel` | offset (-2.6, 10, -3.3), turned 0.15 about y | Sits at the front of his right hip, clear of the hanging sleeve; its front face (u 26-30, v 56-60) is the flap. |
| `holocron` | on the left arm, offset (1, 1.6, -1.2) | Cupped under the palm of his off-hand, visible in phase 3 only (`unkept`, `echo_unkept`). It is raised overhead in the Surge gather and the renderer adds block light 6 to the whole model in phase 3, so paint the crimson seams bright on every face; the code adds no glow of its own. |
| `robe`, `skirt`, `sleeve` | on the body and arms, no offset | The robe and sleeves (0.3 inflation) and the skirt (0.4) cover the vanilla body, arms and upper legs; the skirt's lower edge hangs over the knees, so the boots are the only leg paint that shows below it. |

Poses the skin must survive: reading at the lectern (head down, both hands forward), the Hurl and the Grip (off-hand
raised or reaching), the Palm (hand drawn back), the Lash and the Lunge (blade low; the Lunge crouches him), the Surge
(off-hand straight up), Spent (sagging, arms hanging), the kneel (lowered 0.55 blocks, one knee down), the fall after
the execution (face down), and the binders (wrists together in front). The echo is drawn translucent (about 72%
opacity, tinted cool grey) over `echo` or `echo_unkept`, so strong value contrast in those skins keeps him readable.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `assets/oldrepublic/textures/entity/orren_vasch/base.png` | Phases 1-2, 64x64 | placeholder generated | See the artist brief below. Hood down, hair tied back, short greying beard; faded ochre robe with an ink-grey front panel and ink spatter low on the front; ink-grey undertunic and sleeves, ink-stained cuffs; fingerless leather gloves; a brown leather bandolier of empty brass holocron cradles; a satchel on the right hip; worn boots. |
| `assets/oldrepublic/textures/entity/orren_vasch/unkept.png` | Phase 3, 64x64 | placeholder generated | The same man, hood up (ink-grey, framing the face), the robe a shade darker and more worn, frayed cloak edges, and the stolen Sith holocron (dark, crimson seams) on the left forearm. The seams light his hand from below: paint them bright, the code adds no glow. |
| `assets/oldrepublic/textures/entity/orren_vasch/echo.png`, `echo_unkept.png` | Echo of the Collector, 64x64 | placeholder generated | The two skins above desaturated toward a cool smoke grey, mottled, with thinning edges on the cloak and hem (alpha breaks up at the edges). The echo's outline breaks into slow smoke in code (`echo_smoke`), so the skin itself stays readable. |
| `assets/oldrepublic/textures/gui/sprites/portrait/orren_vasch.png` | Dialogue portrait, 32x32 | placeholder generated | Head and shoulders like the mentor portraits: greying hair tied back, a short beard, tired eyes, an amber rim light from his blade on the left edge. Tired, articulate, certain. |

**Artist brief, Orren Vasch.** A fallen Jedi Archivist of the Watch enclave: a scholar who stole his order's Archive,
not a warlord. Tall and spare, slightly stooped; greying hair tied back; a short greying beard; tired eyes. Layered
Archivist robes in faded ochre and ink-grey, the cuffs ink-stained; fingerless leather gloves; a long cloak or sash as a
separate part (it lifts and streams out behind him on the inrushing air in the Surge gather). A satchel and a bandolier of *empty* holocron cradles across the
chest. His blade is amber, from a squared, archival hilt with a brass collar (the same family as `pommel_archivist` and
`emitter_reliquary`). Phase 3, "Unkept": hood up; the stolen Sith holocron in his off-hand lights the hand from below with
crimson seams; the cloak's edges fray into drifting dust (steady, no flicker). The rematch echo is the same model,
desaturated, its edges breaking into slow smoke. Avoid any resemblance to a film or game character: no recognisable
robe cut, hairstyle or hilt from existing media.

## Blocks (spec 8.2)

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `assets/oldrepublic/textures/block/resonance_pylon_stone.png`, `resonance_pylon_bronze.png`, `resonance_pylon_top.png` | Pylon base: coursed pale stone, a bronze band, a top with a bronze ring around a dark socket | placeholder generated | Ancient carved masonry of the Watch; bronze aged, not polished. |
| `assets/oldrepublic/textures/block/resonance_pylon_crystal_{dormant,singing,silenced,regrowing}.png` | The pylon crystal in each `crystal` state | placeholder generated | Vertical facets with a bright edge. Dormant: cool grey-blue. Singing: near-white pale blue, brightest edge lines. Silenced: dark slate with black cracks. Regrowing: pale blue with growth bands. |
| `assets/oldrepublic/models/block/resonance_pylon_{dormant,singing,silenced,regrowing,empty}.json` | A stepped stone and bronze cradle with three leaning crystal prisms (a tall centre and two short sides); `light_emission` 3 / 12 / 0 / 6; regrowing prisms are 45% height; `empty` shows broken stumps | placeholder | The `pylon_focus` entity (an end-crystal-like target) sits over the cradle; keep the crystal top clear for it. |
| `assets/oldrepublic/blockstates/resonance_pylon.json`, `items/resonance_pylon.json` | `crystal=dormant|singing|silenced|regrowing|empty`; the item shows `dormant` | final | |
| `assets/oldrepublic/textures/block/reliquary_heart_{side,top,front,crystal}.png` | The heart: a carved stone lectern-altar with a niche in a bronze frame on the front, a dim amber crystal on top | placeholder generated | It must read as the hall's focus from the antechamber door: an altar, not a machine. |
| `assets/oldrepublic/models/block/reliquary_heart.json`, `blockstates/reliquary_heart.json`, `items/reliquary_heart.json` | Plinth, body, cap and a small rotated crystal (light emission 6); four facings | placeholder | |
| `assets/oldrepublic/textures/block/hoard_plinth_{side,top,front}.png` | The hoard plinth | placeholder generated | A low carved plinth for the three holocrons of the hoard choice. |
| `assets/oldrepublic/models/block/hoard_plinth.json`, `blockstates/hoard_plinth.json`, `items/hoard_plinth.json` | Base, body and a wide top slab; four facings | placeholder | |
| `assets/oldrepublic/textures/block/reliquary_seal.png`, `reliquary_seal_vault.png` | Door seal (carved slab with bronze bars) and vault seal (heavier, with a lock plate) | placeholder generated | It crumbles when opened (`hoard_open`, `hoard.open`): carved stone that looks breakable by a ritual, not by a pickaxe. |
| `assets/oldrepublic/models/block/reliquary_seal{,_vault,_open}.json`, `blockstates/reliquary_seal.json`, `items/reliquary_seal.json` | `cube_all` seals; `open=true` draws nothing (particle texture only) | final | |

## Particles (spec 13)

All are `SimpleParticleType`s: five registered by part A's `BossParticles`, `lumen_spark` by `SentinelParticles`.

| Path | Frames | Status | Brief |
|---|---|---|---|
| `assets/oldrepublic/particles/ward_haze.json`, `textures/particle/ward_haze_{0,1}.png` | 2, 8x8 | placeholder generated | Near-white soft heat-haze specks drifting off Vasch while the ward holds; tinted pale crystal by the client. Never a bubble or a halo. |
| `assets/oldrepublic/particles/ward_current.json`, `textures/particle/ward_current_{0,1}.png` | 2, 8x8 | placeholder generated | A white mote with a short tail: the thin, wavering currents from each singing pylon to him. |
| `assets/oldrepublic/particles/surge_dust.json`, `textures/particle/surge_dust_{0..3}.png` | 4, 16x16 | placeholder generated | Sandy grey-brown dust puffs in natural colour; frames grow and thin with age. The Surge's wall of air and dust. |
| `assets/oldrepublic/particles/lash_seam.json`, `textures/particle/lash_seam_{0,1}.png` | 2, 8x8 | placeholder generated | A streak of grit and dust, natural colour: the Lash's seam and travelling ridge. |
| `assets/oldrepublic/particles/lumen_spark.json`, `textures/particle/lumen_spark_{0,1}.png` | 2, 8x8 | placeholder generated | A white core with a soft edge; tinted pale blue or red with the sentinel's lumen. Sputters only with flashing effects on. |
| `assets/oldrepublic/particles/echo_smoke.json`, `textures/particle/echo_smoke_{0..3}.png` | 4, 16x16 | placeholder generated | Grey smoke loosening with age; tinted by the client. The echo's edges. |

## Structures (spec 3.6, 8.2)

| Path | What it is | Status |
|---|---|---|
| `data/oldrepublic/structure/boss/reliquary_surface.nbt` | 17x16x17: a watchtower stump; layers 0-8 (the spiral descent) are set underground, layers 9-15 stand above | placeholder generated |
| `data/oldrepublic/structure/boss/reliquary_hall.nbt` | 31x18x31: the hall, directly below the surface (layout in `docs/design/boss.md`, "Reliquary placement") | placeholder generated |
| `data/oldrepublic/site_anchors/boss/reliquary_surface.json` | `DESCENT_TOP` | final |
| `data/oldrepublic/site_anchors/boss/reliquary_hall.json` | `BED`, `HEART`, `THRESHOLD`, `DOOR_SEAL`, `LECTERN`, `DAIS`, `PYLON_1`-`4`, `GALLERY_W`, `GALLERY_E`, `SENTINEL_1`-`4`, `COLUMN_1`-`6`, `DEBRIS_1`-`4`, `PLINTH`, `VAULT_SEAL` | final |

A builder may restyle the masonry freely, as long as the anchors, the walkable floor around them, the threshold plane and
the light levels the generator's self-checks enforce stay as they are (re-run the generator to check).

## Sounds (spec 13, 16)

All 44 are placeholders synthesised by `tools/assets/sounds/boss.py` (procedural noise, oscillators and filters; no
samples). Ids, `sounds.json` entries and subtitles come from `events_m3.json` (lead). Paths are under
`assets/oldrepublic/sounds/`. Loops are seamless at the loop point; streamed files are `stream: true`. Volume and pitch
variation are set in `events_m3.json`. The final brief says what the real sound must do.

| Id | File | Length | Placeholder | Final brief |
|---|---|---|---|---|
| `sentinel.wake` | `sentinel/wake.ogg` | 2.3 s | Stone grinding loose, a low glassy hum swelling as the lumen ignites | Over the 20-tick wake: heavy stone grinding against stone, dust trickling, then a low crystal hum rising as the lumen catches. Massive, slow, not robotic. Also played soft and high for the Watcher's bow. |
| `sentinel.slam_tell` | `sentinel/slam_tell.ogg` | 1.6 s | A heavy grind and a creak of bronze rising in pitch | The 20-tick tell must be readable with eyes closed: arms of stone and bronze lifting, strain rising to a peak just before the blow. |
| `sentinel.slam` | `sentinel/slam.ogg` | 2.0 s | A deep stone impact, a crunch and a debris spray | Two stone fists driven into the floor: a deep, short impact with a crunch of floor and a spray of chips; a sub-bass thump that does not boom for long. |
| `sentinel.core_exposed` | `sentinel/core_exposed.ogg` | 2.0 s | Chest plates grinding apart, the core's hum rising bright | The plates part and the core's hum is suddenly exposed and bright: an audible "now" for the 40-tick weak window. |
| `sentinel.lance_charge` | `sentinel/lance_charge.ogg` | 1.7 s | An intake of air under a rising glassy whine | The 24-tick tell: motes drawn in, a rising crystal whine that peaks where the bolt fires; the parry cue, so its end must be sharp and predictable. |
| `sentinel.lance_fire` | `sentinel/lance_fire.ogg` | 1.2 s | A deep blaster discharge with a crystal crack and a stone body | A heavy amber-white bolt: weightier than a blaster, with a crystalline crack; must not be confused with poacher blasters. |
| `sentinel.hurt` | `sentinel/hurt.ogg` | 0.5 s | A stone crack, a few chips, a dull bronze knock | Stone and bronze taking a hit; no voice. |
| `sentinel.death` | `sentinel/death.ogg` | 3.4 s | The lumen hum guttering and falling while the body crumbles | The lumen gutters out (a falling hum), the plates slump and break into rubble and dust. |
| `boss.collector.hurt` | `boss/collector_hurt.ogg` | 0.7 s | A sharp exhale and grunt, robes snapping | A tired older man's pained exhale or grunt (voice actor), with cloth movement; several takes. |
| `boss.collector.hurl_lift` | `boss/collector_hurl_lift.ogg` | 2.0 s | Cracking and grinding, grit pouring, a low Force hum | The Hurl tell (24 ticks): a chunk tearing from a debris pile, grit pouring off it, a low Force hum swelling. |
| `boss.collector.hurl_throw` | `boss/collector_hurl_throw.ogg` | 1.2 s | A deep pressure push and a rough whoosh | A heavy stone flung by the Force: pressure push and a rough, tumbling whoosh. |
| `boss.collector.hurl_rip` | `boss/collector_hurl_rip.ogg` | 2.0 s | The chunk crashing down between them | Pulled out of his hold: a wrench of the Force and the chunk crashing to the floor. |
| `boss.collector.hurl_return` | `boss/collector_hurl_return.ogg` | 1.3 s | A bent-air streak, a pressure snap, the stone tumbling away | A Push sending the chunk back at him: the player's Push signature plus a stone streak. |
| `boss.collector.palm_tell` | `boss/collector_palm_tell.ogg` | 1.3 s | Air and dust drawn toward his palm, a short in-breath that stops dead | The 16-tick Palm tell: an inward draw that stops dead just before the release (the Push-clash cue). |
| `boss.collector.lash_tell` | `boss/collector_lash_tell.ogg` | 1.6 s | A dry rising hiss of grit | The blade tip dragging and a seam of dust lifting in a line; the hum rises over 20 ticks. |
| `boss.collector.lash_wave` | `boss/collector_lash_wave.ogg` | 2.2 s | A rolling rumble that passes and fades | A 1-block ridge of dust and grit travelling along the floor at 0.8 blocks per tick: positional, passing by. |
| `boss.collector.surge_gather` | `boss/collector_surge_gather.ogg` | 2.6 s | A long inward rush with a dissonant undertone | The Surge tell: the air bends toward him, the stolen holocron's dissonant undertone underneath. Long enough to reach cover. |
| `boss.collector.surge_release` | `boss/collector_surge_release.ogg` | 3.4 s | A deep pressure blast, a roaring wall of air and dust | The Surge breaking outward: the largest sound of the fight, a wall of air, dust and tumbling stone. |
| `boss.collector.surge_clash` | `boss/collector_surge_clash.ogg` | 1.6 s | A blade-on-pressure clash and air tearing past | The wall breaking against a guard or a Push: saber-on-pressure clash and air tearing past the player. |
| `boss.collector.spent` | `boss/collector_spent.ogg` | 2.5 s | A ragged out-breath, the holocron guttering, dust settling | He sags after the Surge: a ragged breath (voice), the holocron's light guttering. The punish window. |
| `boss.collector.grip` | `boss/collector_grip.ogg` | 1.6 s | A tight low choke of pressure and a creak, pebbles lifting | The Archive Grip closing. Not a choking voice: pressure, creak, pebbles. |
| `boss.collector.grip_hold` | `boss/collector_grip_hold.ogg` | 2.0 s | A straining, wavering pressure and grit trickling | Held in the Grip: a straining pressure, layered under the player's own breathing if any. |
| `boss.collector.grip_break` | `boss/collector_grip_break.ogg` | 1.1 s | A burst of grit and the pressure letting go | The Grip broken: release and a burst of grit. |
| `boss.collector.lunge` | `boss/collector_lunge.ogg` | 0.9 s | A kick of dust, then the amber blade's streaking swing | The Unkept lunge: a dust kick and a fast blade streak. |
| `boss.collector.ward_hum` | `boss/collector_ward_hum.ogg` | 4.0 s loop | A shimmering crystal resonance with slow beating and air | The Resonance Ward around him while pylons sing: a shimmering crystal resonance that works at any volume (it may be played softer as pylons fall). Must sit under dialogue and combat. |
| `boss.collector.unsung` | `boss/collector_unsung.ogg` | 3.0 s | Currents snapping back with a crystal crack, a falling tone, ceiling dust | The last pylon falls: the ward collapses, the currents snap back, a falling tone and ceiling dust. A payoff moment. |
| `boss.collector.transition` | `boss/collector_transition.ogg` | 4.5 s | A deep rumble, falling grit and the crystals' rising choir | The phase sting: the Reliquary shakes and the crystals rise in a choir. |
| `boss.collector.yield` | `boss/collector_yield.ogg` | 4.9 s | The amber blade retracting, crystals dimming in a falling chime, a low drone | He yields: blade retract, crystals dim, quiet resolution. |
| `boss.collector.leash` | `boss/collector_leash.ogg` | 1.5 s | A soft whoomp and a hiss | He resets to the dais in a puff of smoke. |
| `boss.collector.echo_form` | `boss/collector_echo_form.ogg` | 4.9 s | A reversed swirl and a cold, rising choir | The rematch echo gathering from smoke over 60 ticks: cold, ghostly, not horror. |
| `boss.collector.echo_fade` | `boss/collector_echo_fade.ogg` | 3.9 s | A falling hiss and a choir fading | The echo breaking into slow smoke that drains into the floor. |
| `boss.collector.binders` | `boss/collector_binders.ogg` | 1.0 s | Two ratchet clicks and a solid clank | Custody ending: Republic or Imperial binders locking. |
| `boss.collector.execute` | `boss/collector_execute.ogg` | 3.3 s | The swing, the blade's sear, a body settling, the amber hum guttering out | The execution ending: restrained, not gory; the last thing heard is his amber blade's hum dying. |
| `boss.collector.walk_out` | `boss/collector_walk_out.ogg` | 4.5 s | Footsteps on stone receding toward the descent | The release ending: footsteps receding up the spiral. |
| `pylon.sing` | `pylon/sing.ogg` | 3.0 s loop | A sustained crystal tone with slow vibrato and a breathy formant | Each singing pylon, positional: a sustained, almost vocal crystal tone. Up to four play at once with the ward hum, so each should be narrow in pitch and still sound good pitched a little up or down. |
| `pylon.crack` | `pylon/crack.ogg` | 0.7 s | A glassy tick and a short ring | A hit on the crystal: a chip and a ring. Frequent: keep it short and not tiring. |
| `pylon.silence` | `pylon/silence.ogg` | 2.1 s | A split, its song falling away, glints tinkling down | A pylon silenced: the crystal cracks dark and its song falls away. |
| `pylon.regrow` | `pylon/regrow.ogg` | 4.1 s | A crystalline crackle and a swelling tone | The 60-tick re-sing tell: crystal growing back with a rising whine; a warning to hit it. |
| `pylon.shatter` | `pylon/shatter.ogg` | 2.2 s | A bright crash of glass and shards ringing | A pylon shattered into him (a Pull rip or Unsung): bright glass crash and ringing shards. |
| `music.collector.archivist` | `music/collector_archivist.ogg` | 24 s loop, streamed | 80 BPM, 8 bars of A minor: pads, a plucked arpeggio, a slow archival chime motif, a soft frame drum | Phase 1 music (`SoundSource.MUSIC`): measured and melancholy, a scholar's duel. The three loops crossfade over 40 ticks, so they should share key relationships and tempo feel; 1 to 2 minutes each in the final. |
| `music.collector.singing_wall` | `music/collector_singing_wall.ogg` | 20 s loop, streamed | 96 BPM, 8 bars of D minor: the pylons' crystal ostinato over pulsing pads, a driving bass and drums | Phase 2: the pylons' song becomes the music's ostinato; urgency rising. |
| `music.collector.unkept` | `music/collector_unkept.ogg` | 17.1 s loop, streamed | 112 BPM, 8 bars of C harmonic minor: low saw stabs, a taiko pattern, dissonant crystal clusters, a saturated drone | Phase 3: dark and driving, the stolen holocron's dissonance in the harmony. |
| `ambient.reliquary` | `ambient/reliquary.ogg` | 16 s loop | A low crystal drone with slow beating, cold air, distant drips and settling stone | The Reliquary's bed outside the fight (requested from W1's site ambience): cold, sacred, a little wrong. Quiet enough for dialogue. |
| `hoard.open` | `hoard/open.ogg` | 4.0 s | A stone collapse, then a resolving shimmer | The vault seal crumbling and motes rising from three holocrons: a release, then a resolving shimmer. |
