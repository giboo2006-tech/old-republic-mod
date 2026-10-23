# Chapter Four: art and sound (Milestone 4)

Owner: W2. All PLACEHOLDER, original and generated: textures by `tools/assets/gen_textures_hub.py` (PIL, deterministic
seeds), sounds by `tools/assets/sounds/story_m4.py` through `python3 tools/assets/gen_sounds.py --m4 --feature story_m4`
(numpy synthesis, nothing sampled). Nothing copies or imitates film or game assets. Design: `docs/design/chapter_four.md`.

## A1. Effects, phase by phase (13.3)

### `seeker_scan` (client `quest.SeekerFx`)

| Phase | Server | Client look | Sound |
|---|---|---|---|
| Descent (50 ticks) | `SeekerScene` moves W6's `seeker_droid` from `seekerDescend` above the hover point down to it, easing out | The droid itself (W6's model); no effect | none (the droid's own idle, W6) |
| Scan (`seekerScanTicks`, 40) | `seeker_scan` variant 0 to the marked player, entity = droid, intensity = duration | Seven rays (scaled by the VFX setting, at least two) of small red dust motes (`#D0352B`, scale 0.45) from the lens toward the player's chest, 14 degrees either side, the scan line sweeping 16 degrees down and up every 30 ticks; a hotter core mote (`#FF7A5C`) at the lens every third tick. Plays from the stored hover point when there is no droid. Nothing on the ground, no outline | `seeker.scan` at the lens |
| Leaving (30 ticks) | variant 1; the droid climbs and drifts away, then is removed | The fan stops at once | `seeker.chirp` twice, 7 ticks apart (pitch 1.05 then 1.25), `seeker.fly` |
| Cancel | death, logout or a dimension change forgets the scene; the droid is discarded | the scan's duration ends; a missing level or player clears everything (`SeekerFx.reset`) | stops with the scan |

### `lesson_complete` 8 to 10 (client `mentor.MentorClient`)

| Variant | Look | Sound |
|---|---|---|
| 8 `chapter4` | The M2 banner ("Chapter Four complete" over a path line) in the path's colour (Jedi gold, Sith red, Independent amber) | `story.chapter_four` |
| 9 `planet` | The world's name over its subtitle, in the world's colour (Selvaren green, Varkhast rust) | `planet.arrive` |
| 10 `masterwork` | The banner "Masterwork" in the path's colour | `story.lesson_complete` (M2) |

## A2. Sounds

| Event | File | Length | Design |
|---|---|---|---|
| `story.chapter_four` | `story/chapter_four.ogg` | 3 s + tail | A low muted-brass swell (two filtered saws a fifth apart, opening then closing) under a three-bell figure (D, B, E) that settles on an open fifth, a soft floor thump at the start: resolved, with weight |
| `planet.arrive` | `planet/arrive.ogg` | 2.6 s + tail | An airy band-passed noise swell like wind over open ground, a soft sine pad gliding up a fourth, one high chime as the name settles |
| `seeker.scan` | `seeker/scan.ogg` | 2 s | A thin pulse-wave buzz (820 Hz with a fast wobble) whose band-pass sweeps down and up once every 1.5 s (the scan line's period), a faint servo whirr, a tick at each turn |
| `seeker.chirp` | `seeker/chirp.ogg` | 0.3 s | Two quick upward FM blips (1.5 to 2.6 kHz, then 1.9 to 3.3 kHz), a little droid voice; the client plays it twice at rising pitch |
| `seeker.fly` | `seeker/fly.ogg` | 1.6 s | A repulsor hum (a soft saw at 180 Hz rising by 90%) thinning out through a closing low-pass, with a rising breath of air, fading as it climbs |

Levels: peaks around -3 dBFS after `gen_sounds.finish`; RMS 0.13 to 0.17 for the seeker sounds, 0.10 to 0.14 for the
banners. `sounds.json` and the subtitles are unchanged (the placeholder file names are kept).

## A3. Portraits (`textures/gui/sprites/portrait/<id>.png`, 32x32)

Head and shoulders on a dim vertical gradient (Watch green, Imperial red-black, frontier brown, hunter ember), a rim light
in the speaker's accent on the right edge of the figure.

| Id | Look |
|---|---|
| `ansa` | Keeper Ansa Veylin: grey hair streaked lighter, tied back; lined face; moss robe over cream, a deep green hood down |
| `kaddor` | Overseer Vael Kaddor: cropped dark hair grey at the temples, a pale scar through the brow, grey uniform coat with red trim, a brass rank pin |
| `kaast` | Torvin Kaast's helmet: rounded, rust paint with a cream crown panel, one straight dark visor slit with a faint lit edge, cheek seams, a three-line chin vent, chipped paint, an antenna stub with a red tip |
| `mandalorian` | One of Kaast's hunters: the same rounded helmet shape in grey-green, no antenna |
| `smuggler` | A frontier smuggler: stubble beard, a scar, a quilted brown vest over a pale shirt |
| `quartermaster_watch` | The Watch quartermaster: full beard, leather apron over a green tunic |
| `quartermaster_imperial` | The Imperial quartermaster: peaked grey cap with a brass badge, grey coat, red trim |
| `spacer` | A spacer: goggles on the brow (pale blue lenses), worn tan flight jacket with a pale collar |
| `trainer` | A discipline trainer: goggles, beard, a work apron |
| `apprentice` | A young Watch apprentice: sandy hair, sage tunic, hood down |
| `acolyte` | A Proving acolyte: cropped dark hair, dark grey robe |

The hunters' helmets are deliberately generic (round dome, straight horizontal slit), not any film or game design.

## A4. Items (16x16, `textures/item/`)

| Item | Look |
|---|---|
| `kaast_pattern` | A half-unrolled flimsi sheet, the roll on the right tied with a cord, a helmet and breastplate drawn in rust ink |
| `tomb_relic` | A carved shard of dark violet-grey stone, lighter edges on the lit side, a glyph that still glows dull red with hot points |
| `sealed_contract` | A folded contract with grey text lines, a dark red seal and two ribbon tails |

## A5. The marker beacon (`marker_beacon`)

A scrap post (model: an 8x1x8 foot plate, a 4x10x4 post, a small battery box on one side, a 3x3x3 lamp on top; the
block's shape is 6x14x6). Textures `marker_beacon_post` (rust-streaked steel with a worn yellow and black hazard band),
`marker_beacon_lamp` (a red lens with a hot core and a glint), `marker_beacon_base` (dark plate, four rivets). Block light
3. In game the lamp blinks: one small red dust mote every second or so and a quiet `seeker.chirp` now and then
(`MarkerBeaconBlock#animateTick`, client only).
